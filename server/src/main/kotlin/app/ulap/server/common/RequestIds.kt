package app.ulap.server.common

import java.security.SecureRandom

/**
 * Request ids like `req_01JBX8Q2W7Z5…` (api-spec.md §4): `req_` and a ULID, which is a
 * millisecond timestamp (10 characters) and 80 random bits (16 characters) in Crockford base32.
 * They sort by time, which makes log searches by `X-Request-Id` easy.
 */
object RequestIds {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val TIME_CHARS = 10
    private const val RANDOM_CHARS = 16
    private const val BITS_PER_CHAR = 5
    private const val CHAR_MASK = 0x1F
    private const val MAX_LENGTH = 64

    private val random = SecureRandom()

    // Letters, digits, '_' and '-' only, so a client-sent id can't forge extra log lines.
    private val allowed = Regex("[A-Za-z0-9_-]{1,$MAX_LENGTH}")

    fun next(nowMillis: Long = System.currentTimeMillis()): String {
        val chars = CharArray(TIME_CHARS + RANDOM_CHARS)
        var time = nowMillis
        for (i in TIME_CHARS - 1 downTo 0) {
            chars[i] = ALPHABET[(time and CHAR_MASK.toLong()).toInt()]
            time = time ushr BITS_PER_CHAR
        }
        for (i in TIME_CHARS until chars.size) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)]
        }
        return "req_" + String(chars)
    }

    /** True for an id a client may pass in `X-Request-Id`; anything else gets a new id. */
    fun isAcceptable(id: String): Boolean = allowed.matches(id)
}
