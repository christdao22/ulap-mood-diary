package app.ulap.core.ui

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
