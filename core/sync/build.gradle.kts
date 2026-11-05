// SyncEngine, SyncWorker and SyncTriggers (D8, technical-plan.md §8). Repositories never start sync.
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.hilt)
}

android {
    namespace = "app.ulap.core.sync"
}

dependencies {
    implementation(projects.core.data)
}
