package app.ulap.shared.validation

/**
 * Field rules for accounts (api-spec.md §5.1, §6.2), used by the sign-in forms and the server.
 * Normalizing happens here too, so an email typed as ` Ana@Example.COM ` is the same account as
 * `ana@example.com` on both sides.
 */
object AccountRules {
    const val DISPLAY_NAME_MAX_LENGTH: Int = 50

    /** The longest address a mail server accepts (RFC 5321). */
    const val EMAIL_MAX_LENGTH: Int = 254

    /** The languages a `User` can have (api-spec.md §5.1). */
    val LANGUAGES: Set<String> = setOf("en", "fil", "ceb")
    const val DEFAULT_LANGUAGE: String = "en"

    /**
     * What `DELETE /me` must carry as `confirm` (api-spec.md §7.3), so that a stray or replayed
     * request can't delete an account by accident.
     */
    const val DELETE_CONFIRMATION: String = "DELETE"

    // One "@" with something on both sides and a dot in the domain. Deliverability is proven by
    // the verification email, not by a stricter pattern.
    private val emailShape = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")

    /** The email trimmed and lower-cased, or null when it doesn't look like an address. */
    fun normalizeEmail(raw: String): String? {
        val email = raw.trim().lowercase()
        return email.takeIf { it.length <= EMAIL_MAX_LENGTH && emailShape.matches(it) }
    }

    /** True for a trimmed name of 1 to 50 characters. A missing name is allowed; an empty one isn't. */
    fun isValidDisplayName(name: String): Boolean = name.trim().length in 1..DISPLAY_NAME_MAX_LENGTH

    /**
     * `en`, `fil` or `ceb` from a language tag such as `fil-PH`, or null for anything else. `tl`
     * is accepted as Filipino: older phones still report Tagalog under that code.
     */
    fun languageFromTag(tag: String?): String? {
        val primary = tag?.trim()?.substringBefore('-')?.substringBefore(';')?.lowercase() ?: return null
        return when (primary) {
            "tl" -> "fil"
            in LANGUAGES -> primary
            else -> null
        }
    }
}
