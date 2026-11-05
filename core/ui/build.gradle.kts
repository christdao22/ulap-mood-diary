// Screen pieces several features share, and every user-facing string in English, Filipino and
// Bisaya (res/values, values-fil, values-b+ceb; README §13).
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.android.library.compose)
}

android {
    namespace = "app.ulap.core.ui"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.designsystem)
}
