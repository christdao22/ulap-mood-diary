@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.common.ApiException
import app.ulap.server.db.AuthProvider
import app.ulap.server.db.Db
import app.ulap.server.db.isUniqueViolation
import app.ulap.server.users.findUser
import app.ulap.server.users.insertUser
import app.ulap.shared.api.AuthResponse
import app.ulap.shared.api.EmailLoginRequest
import app.ulap.shared.api.EmailRegisterRequest
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.GoogleSignInRequest
import app.ulap.shared.api.UserDto
import app.ulap.shared.model.Ids
import app.ulap.shared.validation.AccountRules
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/** What a caller must send with a sign-in: the device the session is for and, for emails, their language. */
class SignInContext(val deviceId: Uuid, val clientIp: String, val language: String?)

/**
 * Signing up and in (api-spec.md §6.1, §6.2, §6.4) and adding a second way to sign in to an
 * existing account (§6.12, §6.13). Rules live here; the DAL files only run queries and the routes
 * only parse and respond (ulap-reference.md §6.1).
 */
class SignInService(
    private val db: Db,
    private val google: GoogleTokenVerifier,
    private val passwords: PasswordPolicy,
    private val limits: AuthLimits,
    private val sessions: SessionService,
    private val emails: AccountEmailService,
) {
    /** `POST /auth/google`: finds the identity by Google's `sub`, or creates the user and identity (api-spec.md §6.1). */
    suspend fun signInWithGoogle(request: GoogleSignInRequest, context: SignInContext): AuthResponse {
        val identity = verifyGoogle(request.idToken)
        val displayName =
            displayNameOrNull(request.displayName ?: identity.name?.take(AccountRules.DISPLAY_NAME_MAX_LENGTH))
        return db.tx {
            val existing = findIdentity(AuthProvider.GOOGLE, identity.subject)
            if (existing != null) {
                with(sessions) { openSession(existing.userId, context.deviceId, isNewUser = false) }
            } else {
                val userId = newUser(displayName, context.language)
                insertIdentity(
                    IdentityRow(
                        id = Uuid.parse(Ids.newId()),
                        userId = userId,
                        provider = AuthProvider.GOOGLE,
                        providerSubject = identity.subject,
                        email = identity.email?.let(AccountRules::normalizeEmail),
                        emailVerified = identity.emailVerified,
                        passwordHash = null,
                    ),
                )
                with(sessions) { openSession(userId, context.deviceId, isNewUser = true) }
            }
        }
    }

    /** `POST /auth/email/register` (api-spec.md §6.2, §6.3). The verification email goes out after the commit. */
    suspend fun register(request: EmailRegisterRequest, context: SignInContext): AuthResponse {
        val email = AccountRules.normalizeEmail(request.email) ?: throw ApiException.invalidField("email")
        limits.checkSignIn(context.clientIp, email)
        val displayName = displayNameOrNull(request.displayName)
        passwords.requireStrong(request.password, email)
        val hash = withContext(Dispatchers.Default) { passwords.hash(request.password) }
        val language = context.language ?: AccountRules.DEFAULT_LANGUAGE
        val (response, mail) = db.tx {
            if (findIdentity(AuthProvider.EMAIL, email) != null) throw AuthErrors.emailTaken()
            val userId = newUser(displayName, language)
            val identity = emailIdentity(userId, email, hash)
            insertIdentityOrEmailTaken(identity)
            val mail = with(emails) { prepareVerification(identity, language) }
            with(sessions) { openSession(userId, context.deviceId, isNewUser = true) } to mail
        }
        emails.send(mail)
        return response
    }

    /** `POST /auth/email/login` (api-spec.md §6.4): one error, and the same work, whether or not the email exists. */
    suspend fun login(request: EmailLoginRequest, context: SignInContext): AuthResponse {
        val email = AccountRules.normalizeEmail(request.email)
        if (email != null) limits.checkSignIn(context.clientIp, email)
        val identity = email?.let { db.tx { findIdentity(AuthProvider.EMAIL, it) } }
        val matches = withContext(Dispatchers.Default) { passwords.verify(identity?.passwordHash, request.password) }
        if (identity == null || !matches) throw AuthErrors.invalidCredentials()
        return db.tx { with(sessions) { openSession(identity.userId, context.deviceId, isNewUser = false) } }
    }

    /** `POST /auth/link/google` (api-spec.md §6.12). Linking the same Google account again is a no-op. */
    suspend fun linkGoogle(userId: Uuid, idToken: String): UserDto {
        val identity = verifyGoogle(idToken)
        return db.tx {
            requireAccount(userId)
            val existing = findIdentity(AuthProvider.GOOGLE, identity.subject)
            val conflict = when {
                existing?.userId == userId -> null

                existing != null -> "This Google account belongs to another Ulap user"

                identitiesOf(userId).any { it.provider == AuthProvider.GOOGLE } ->
                    "This Ulap account already has a different Google sign-in"

                else -> null
            }
            if (conflict != null) throw ApiException(ErrorCode.IDENTITY_LINKED_ELSEWHERE, conflict)
            if (existing == null) {
                insertIdentity(
                    IdentityRow(
                        id = Uuid.parse(Ids.newId()),
                        userId = userId,
                        provider = AuthProvider.GOOGLE,
                        providerSubject = identity.subject,
                        email = identity.email?.let(AccountRules::normalizeEmail),
                        emailVerified = identity.emailVerified,
                        passwordHash = null,
                    ),
                )
            }
            loadUserOrGone(userId)
        }
    }

    /** `POST /auth/link/email` (api-spec.md §6.13): adds a password sign-in; the email still needs verifying. */
    suspend fun linkEmail(userId: Uuid, request: EmailRegisterRequest, language: String?): UserDto {
        val email = AccountRules.normalizeEmail(request.email) ?: throw ApiException.invalidField("email")
        passwords.requireStrong(request.password, email)
        val hash = withContext(Dispatchers.Default) { passwords.hash(request.password) }
        val (user, mail) = db.tx {
            requireAccount(userId)
            val conflict = when {
                findIdentity(AuthProvider.EMAIL, email) != null -> "An account with this email already exists"

                identitiesOf(userId).any { it.provider == AuthProvider.EMAIL } ->
                    "This Ulap account already has an email sign-in"

                else -> null
            }
            if (conflict != null) throw AuthErrors.emailTaken(conflict)
            val identity = emailIdentity(userId, email, hash)
            insertIdentityOrEmailTaken(identity)
            val user = loadUserOrGone(userId)
            val mail = with(emails) { prepareVerification(identity, language ?: user.language) }
            user to mail
        }
        emails.send(mail)
        return user
    }

    private suspend fun verifyGoogle(idToken: String): GoogleIdentity =
        withContext(Dispatchers.IO) { google.verify(idToken) }
            ?: throw ApiException(ErrorCode.INVALID_GOOGLE_TOKEN, "Google ID token failed verification")

    private fun JdbcTransaction.newUser(displayName: String?, language: String?): Uuid {
        val userId = Uuid.parse(Ids.newId())
        insertUser(userId, displayName, language ?: AccountRules.DEFAULT_LANGUAGE)
        return userId
    }

    /** Two registrations racing on one email: the unique index decides, and the loser gets `409`. */
    private fun JdbcTransaction.insertIdentityOrEmailTaken(identity: IdentityRow) {
        try {
            insertIdentity(identity)
        } catch (e: ExposedSQLException) {
            if (e.isUniqueViolation()) throw AuthErrors.emailTaken() else throw e
        }
    }

    private fun emailIdentity(userId: Uuid, email: String, hash: String) = IdentityRow(
        id = Uuid.parse(Ids.newId()),
        userId = userId,
        provider = AuthProvider.EMAIL,
        providerSubject = email,
        email = email,
        emailVerified = false,
        passwordHash = hash,
    )

    /** A trimmed display name, null when none was sent, or `400 VALIDATION_FAILED` when it's empty or too long. */
    private fun displayNameOrNull(raw: String?): String? {
        val name = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!AccountRules.isValidDisplayName(name)) throw ApiException.invalidField("displayName", "TOO_LONG")
        return name
    }
}

/**
 * An access token outlives a deleted account by up to 15 minutes (api-spec.md §3, §7.3).
 * Without this check, linking with such a token would try to insert an identity for a user
 * that is gone and fail as a `500` instead of `403 ACCOUNT_DELETED`.
 */
private fun JdbcTransaction.requireAccount(userId: Uuid) {
    findUser(userId) ?: throw AuthErrors.accountDeleted()
}
