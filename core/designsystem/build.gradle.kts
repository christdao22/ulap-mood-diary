// Theme tokens (Color, Type, Shape), the Ulap mascot and the shared components
// (design-spec.md §2–§8). Knows the domain types (a Zone picks its colors) but not the app's data:
// no repositories, Room or network.
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.android.library.compose)
}

android {
    namespace = "app.ulap.core.designsystem"
    // Robolectric reads the merged resources (the fonts) for the component tests.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    // api: UlapColors.zone(Zone) has Zone in its signature, so callers need it too.
    api(projects.core.model)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)

    // Component tests drive real composables on the JVM through Robolectric.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    // The empty activity the Compose test rule launches.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
