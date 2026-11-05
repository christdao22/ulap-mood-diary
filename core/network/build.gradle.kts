// Ktor Client and its plugins: bearer token, app headers, the 426 handler (ulap-reference.md §5).
// Network DTOs never leave this module.
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.hilt)
}

android {
    namespace = "app.ulap.core.network"
}

dependencies {
    implementation(projects.core.model)
}
