// Settings: language, mascot on/off, device ID, last sync position (technical-plan.md §6).
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.hilt)
}

android {
    namespace = "app.ulap.core.datastore"
}

dependencies {
    implementation(projects.core.model)
}
