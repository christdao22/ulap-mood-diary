// Accessibility checks for Compose screen tests: touch target size and text cut off at large font
// sizes (design-spec.md §11). Only test source sets depend on it. It's an Android module, unlike
// core:testing, because the Compose test APIs are Android-only.
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.android.library.compose)
}

android {
    namespace = "app.ulap.core.composetesting"
    // Robolectric reads the merged manifest for the empty activity the Compose test rule launches.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui.test.junit4)

    // The checks' own tests: they must fail on a screen that breaks the rules.
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
