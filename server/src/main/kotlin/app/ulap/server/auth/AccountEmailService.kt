@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.common.atUtc
import app.ulap.server.common.redacted
import app.ulap.server.db.AuthProvider
import app.ulap.server.db.Db
import app.ulap.server.db.EmailTokenPurpose
import app.ulap.server.users.findUser
import app.ulap.shared.model.Ids
import app.ulap.shared.validation.AccountRules
import java.time.Clock
import java.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory

/** An email prepared inside a transaction and sent after it commits, so a rolled-back sign-up sends nothing. */
class PendingMail(val identityId: Uuid, val message: MailMessage) {
    override fun toString(): String = "PendingMail(identityId=$identityId)"
}

/**
 * Everything that reaches the person by email (api-spec.md §6.5–§6.8): the verification link
 * after registering or linking an email, resending it, and the forgot/reset flow. Tokens are
 * single use and short-lived; a link that was used or has expired is `410 TOKEN_USED_OR_EXPIRED`.
 */
class AccountEmailService(
    private val db: Db,
    private val clock: Clock,
    private val mailer: Mailer,
    private val appLinkBase: String,
    private val limits: AuthLimits,
    private val passwords: PasswordPolicy,
) {
    private val log = LoggerFactory.getLogger(AccountEmailService::class.java)

    /** Inside the caller's transaction: a new 24-hour verification token for [identity], as a mail to send after commit. */
    fun JdbcTransaction.prepareVerification(identity: IdentityRow, language: String): PendingMail {
        val token = newEmailToken(identity.id, EmailTokenPurpose.VERIFY, VERIFICATION_TTL)
        val to = requireNotNull(identity.email) { "An email identity always has an email" }
        return PendingMail(
            identity.id,
            AuthMail.verification(to, language, AuthMail.verificationLink(appLinkBase, token)),
        )
    }

    /**
     * Sends a prepared mail. A mail server outage doesn't fail the request that prepared it: the
     * account exists and the person can ask for the link again. The log names the identity, never
     * the address or the link.
     */
    suspend fun send(mail: PendingMail) {
        try {
            mailer.send(mail.message)
        } catch (e: MailException) {
            log.warn("Could not send email for identity ${mail.identityId}", e.redacted())
        }
    }

    /** `POST /auth/email/verify` (api-spec.md §6.5). */
    suspend fun verify(token: String) {
        db.tx {
            val row = spendToken(token, EmailTokenPurpose.VERIFY)
            markEmailVerified(row.identityId)
        }
    }

    /**
     * `POST /auth/email/resend-verification` (api-spec.md §6.6), for the signed-in user's email
     * identity. Nothing to send (no email identity, or already verified) is still `204`.
     */
    suspend fun resendVerification(userId: Uuid, language: String?) {
        val mail = db.tx {
            val identity = identitiesOf(userId).firstOrNull { it.provider == AuthProvider.EMAIL }
                ?.takeIf { !it.emailVerified }
                ?: return@tx null
            limits.checkEmailSend(identity.providerSubject)
            prepareVerification(identity, language ?: languageOf(userId))
        }
        if (mail != null) send(mail)
    }

    /**
     * `POST /auth/password/forgot` (api-spec.md §6.7): always `204`, so the answer doesn't say
     * whether an account exists. The limit is counted per email either way.
     */
    suspend fun forgotPassword(rawEmail: String, language: String?) {
        val email = AccountRules.normalizeEmail(rawEmail) ?: return
        limits.checkEmailSend(email)
        val mail = db.tx {
            val identity = findIdentity(AuthProvider.EMAIL, email) ?: return@tx null
            val token = newEmailToken(identity.id, EmailTokenPurpose.RESET, RESET_TTL)
            val text = AuthMail.passwordReset(
                email,
                language ?: languageOf(identity.userId),
                AuthMail.resetLink(appLinkBase, token),
            )
            PendingMail(identity.id, text)
        }
        if (mail != null) send(mail)
    }

    /**
     * `POST /auth/password/reset` (api-spec.md §6.8): sets the new password and signs every device
     * out. Opening the link proved the person reads this inbox, so the email counts as verified
     * too, which is what the verification link would have proven.
     */
    suspend fun resetPassword(token: String, newPassword: String) {
        // Check the password before touching the token, so a weak one doesn't spend the link.
        val identity = db.tx { findEmailToken(OpaqueTokens.hash(token))?.let { findIdentityById(it.identityId) } }
            ?: throw AuthErrors.tokenUsedOrExpired()
        passwords.requireStrong(newPassword, identity.email)
        val hash = withContext(Dispatchers.Default) { passwords.hash(newPassword) }
        db.tx {
            val row = spendToken(token, EmailTokenPurpose.RESET)
            setPasswordHash(row.identityId, hash)
            markEmailVerified(row.identityId)
            deleteAllSessions(identity.userId)
        }
    }

    /** Stores a new token's hash and returns the token itself, which goes into the link and nowhere else. */
    private fun JdbcTransaction.newEmailToken(identityId: Uuid, purpose: EmailTokenPurpose, ttl: Duration): String {
        val token = OpaqueTokens.generate()
        insertEmailToken(
            EmailTokenRow(
                id = Uuid.parse(Ids.newId()),
                identityId = identityId,
                purpose = purpose,
                tokenHash = OpaqueTokens.hash(token),
                expiresAt = (clock.instant() + ttl).atUtc(),
                usedAt = null,
            ),
        )
        return token
    }

    /** Marks a live token used (and retires its siblings), or throws `410`. */
    private fun JdbcTransaction.spendToken(token: String, purpose: EmailTokenPurpose): EmailTokenRow {
        val now = clock.instant()
        val row = findEmailToken(OpaqueTokens.hash(token))
            ?.takeIf { it.purpose == purpose && it.usedAt == null && it.expiresAt.toInstant().isAfter(now) }
            // Two clicks racing on one link: only the one that marks it used gets through.
            ?.takeIf { useEmailToken(it.id, now.atUtc()) == 1 }
            ?: throw AuthErrors.tokenUsedOrExpired()
        retireEmailTokens(row.identityId, purpose, now.atUtc())
        return row
    }

    private fun JdbcTransaction.languageOf(userId: Uuid): String =
        findUser(userId)?.language ?: AccountRules.DEFAULT_LANGUAGE

    companion object {
        /** Verification links last 24 hours (api-spec.md §6.5). */
        val VERIFICATION_TTL: Duration = Duration.ofHours(24)

        /** Reset links last 30 minutes (api-spec.md §6.7). */
        val RESET_TTL: Duration = Duration.ofMinutes(30)
    }
}
