package app.ulap.shared.validation

/**
 * The password rules of api-spec.md §6.3, shared so the sign-up form and the server reject the
 * same passwords: 10 to 128 characters, not a common password, not the same as the email. No
 * forced symbols or capitals: long passphrases are encouraged.
 *
 * The common-password list lives on the server (ten thousand entries). [check] takes a lookup
 * so the app can pass `{ false }` and still apply the other rules before sending anything.
 */
object PasswordRules {
    const val MIN_LENGTH: Int = 10
    const val MAX_LENGTH: Int = 128

    /** Why a password was turned down. The app shows its own text for each. */
    enum class Problem { TOO_SHORT, TOO_LONG, COMMON, SAME_AS_EMAIL }

    /** The first rule [password] breaks, or null when it passes all of them. */
    fun check(password: String, email: String? = null, isCommon: (String) -> Boolean = { false }): Problem? = when {
        password.length < MIN_LENGTH -> Problem.TOO_SHORT
        password.length > MAX_LENGTH -> Problem.TOO_LONG
        email != null && password.trim().equals(email.trim(), ignoreCase = true) -> Problem.SAME_AS_EMAIL
        isCommon(password) -> Problem.COMMON
        else -> null
    }
}
