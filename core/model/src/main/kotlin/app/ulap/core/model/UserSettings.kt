package app.ulap.core.model

/**
 * The app's three languages (technical-plan.md §11). [tag] is the BCP 47 tag that picks the
 * values folder: `en` → values, `fil` → values-fil, `ceb` → values-b+ceb.
 */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    FILIPINO("fil"),
    BISAYA("ceb"),
    ;

    companion object {
        /** The language for [tag] (a region like `fil-PH` is ignored), or `null` if it isn't one of the three. */
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag?.substringBefore('-') }
    }
}

/** Settings kept on this phone only (technical-plan.md §6). */
data class UserSettings(
    /** `null` until the person picks one in onboarding. The app then follows the phone. */
    val language: AppLanguage? = null,
    /** "Show Ulap the cloud." Messages still show without the character (design-spec.md §8.5). */
    val mascotEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
)
