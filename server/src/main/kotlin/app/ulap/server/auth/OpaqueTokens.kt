package app.ulap.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Refresh tokens and the tokens in verification and reset links (api-spec.md §3, §6.5, §6.7):
 * 256 random bits as base64url. The database keeps only the SHA-256 of each one, so a leaked
 * dump holds nothing a client could present. Looking one up means hashing what the client sent
 * and comparing hashes.
 */
object OpaqueTokens {
    private const val TOKEN_BYTES = 32
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String = encoder.encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))

    /** SHA-256 of [token], base64url, as stored in `token_hash`. */
    fun hash(token: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(token.encodeToByteArray()))
}
