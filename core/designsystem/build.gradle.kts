// Theme tokens (Color, Type, Shape), the Ulap mascot and the shared components
// (design-spec.md §2–§8). Knows nothing about the app's data.
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.android.library.compose)
}

android {
    namespace = "app.ulap.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
}
