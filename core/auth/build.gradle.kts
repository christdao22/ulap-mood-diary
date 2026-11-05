// Credential Manager sign-in and the token store (DataStore + Tink, ulap-reference.md §5).
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.hilt)
}

android {
    namespace = "app.ulap.core.auth"
}

dependencies {
    implementation(projects.core.model)
}
