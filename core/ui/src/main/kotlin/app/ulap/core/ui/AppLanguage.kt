package app.ulap.core.ui

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.ulap.core.model.AppLanguage

/**
 * Switches the whole app to [language] with the per-app language API. The visible Activity is
 * recreated in the new language, and the choice survives restarts: Android 13+ keeps it in
 * system settings, and AppCompat's autoStoreLocales keeps it on Android 8–12.
 */
fun applyAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
}

/**
 * The language the person chose for Ulap, or null if they never chose one (the app then follows
 * the phone's language, falling back to English).
 */
fun chosenAppLanguage(): AppLanguage? {
    val locales = AppCompatDelegate.getApplicationLocales()
    return if (locales.isEmpty) null else AppLanguage.fromTag(locales[0]?.toLanguageTag())
}

/**
 * Each language's own name for itself, e.g. "Bisaya" (design-spec.md §7.1). The same in every
 * values folder, so a person who reads only Bisaya can still find theirs.
 */
val AppLanguage.nameRes: Int
    @StringRes get() = when (this) {
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.FILIPINO -> R.string.language_filipino
        AppLanguage.BISAYA -> R.string.language_bisaya
    }

/** A welcome written in that language, whatever the app is showing now. */
val AppLanguage.greetingRes: Int
    @StringRes get() = when (this) {
        AppLanguage.ENGLISH -> R.string.language_english_greeting
        AppLanguage.FILIPINO -> R.string.language_filipino_greeting
        AppLanguage.BISAYA -> R.string.language_bisaya_greeting
    }
