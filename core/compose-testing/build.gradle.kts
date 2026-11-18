// Helpers for Compose screen tests: the accessibility checks (touch target size and text cut off
// at large font sizes, design-spec.md §11) and HiltTestActivity for flow tests that run the real
// ViewModels. Only test source sets depend on it. It's an Android module, unlike core:testing,
// because the Compose test APIs are Android-only.
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.android.library.compose)
    // HiltTestActivity is an @AndroidEntryPoint, so hiltViewModel() works inside it.
    alias(libs.plugins.ulap.hilt)
}

android {
    namespace = "app.ulap.core.composetesting"
    // Robolectric reads the merged manifest for the empty activity the Compose test rule launches.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(libs.androidx.activity.compose)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui.test.junit4)
    // HiltAndroidRule, HiltTestApplication and @BindValue for the flow tests.
    api(libs.hilt.android.testing)

    // The checks' own tests: they must fail on a screen that breaks the rules.
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
