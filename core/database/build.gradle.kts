// Room, encrypted with SQLCipher (D3, technical-plan.md §6). The ulap.android.room plugin and
// the first entity arrive with the Phase 2 "Local data" tasks. Entities never leave this module.
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.hilt)
}

android {
    namespace = "app.ulap.core.database"
}

dependencies {
    implementation(projects.core.model)
}
