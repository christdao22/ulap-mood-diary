// Account: sign-in, sync status, language, the hotline row and deleting data (design-spec.md §7.8).
// ulap.android.feature allows only core:common, model, domain, designsystem, ui and :shared (README §5).
plugins {
    alias(libs.plugins.ulap.android.feature)
}

android {
    namespace = "app.ulap.feature.account"
}
