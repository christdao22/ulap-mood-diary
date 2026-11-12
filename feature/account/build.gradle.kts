// Account: sign-in, sync status, language, the hotline row and deleting data (design-spec.md §7.8).
// ulap.android.feature allows only core:common, model, domain, designsystem, ui and :shared (README §5).
plugins {
    alias(libs.plugins.ulap.android.feature)
}

android {
    namespace = "app.ulap.feature.account"
}

dependencies {
    // AccountRules and PasswordRules: the sign-in forms check what the server checks.
    implementation(projects.shared)

    // Credential Manager shows Google's account picker over this screen, so it needs the
    // Activity and lives with the sign-in UI. The ID token then goes to AccountRepository.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
}
