package app.ulap.shared.rules

import app.ulap.shared.api.HotlineInfo

/**
 * Which hotline number the app shows (technical-plan.md §13, api-spec.md §9.3). The NCMH 1553
 * number is built in, so the Hopeless flow works offline and on a first run. `GET /meta` can only
 * replace it with another number that looks dialable. Anything else keeps what the phone has, so a
 * bad server answer can never leave a person in crisis without a number. Only the number updates:
 * the card's name stays the app's own translated text, because `/meta` sends one English name.
 * Safety (🔒 in PHASES.md): a change here needs two reviewers.
 */
object HotlineRules {
    /** The NCMH Crisis Hotline, the one built-in number. strings.xml takes it as `%1$s`. */
    val BUILT_IN_NUMBER: String = HotlineInfo.NCMH.number

    /** Digits only, with an optional leading `+`: what the dialer gets as a `tel:` number. */
    private val DIALABLE = Regex("""\+?[0-9]{3,15}""")

    /**
     * What to keep saved after a `/meta` answer, given what's [saved] now (null = the built-in
     * number). The built-in number clears the saved one, a new dialable number replaces it, and
     * a number that isn't dialable is ignored, so [saved] stays.
     */
    fun savedAfter(fromMeta: HotlineInfo, saved: String?): String? {
        val number = fromMeta.number.trim()
        return when {
            number == BUILT_IN_NUMBER -> null
            isDialable(number) -> number
            else -> saved
        }
    }

    /** The number to show: a saved `/meta` number if it's still dialable, otherwise the built-in one. */
    fun numberToShow(saved: String?): String = saved?.takeIf(::isDialable) ?: BUILT_IN_NUMBER

    fun isDialable(number: String): Boolean = DIALABLE.matches(number)
}
