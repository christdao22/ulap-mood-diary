// Settings: language, mascot on/off, device ID, later the sync position (technical-plan.md §6).
// A typed DataStore file, written as JSON with kotlinx.serialization (no protobuf toolchain).
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.ulap.core.datastore"
}

dependencies {
    api(projects.core.domain)

    // Okio storage, in the app and in tests: DataStore's plain File storage can't replace a file
    // on Windows, where tests also run (found in macro-tracker).
    implementation(libs.androidx.datastore.core.okio)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
}
