// Credential Manager sign-in and the token store (DataStore + Tink, ulap-reference.md §5).
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.ulap.core.auth"
}

dependencies {
    implementation(projects.core.model)
    // The application scope a refresh runs in.
    implementation(projects.core.common)
    // AccessTokenSource, which this module implements, and AuthApi's refresh call.
    implementation(projects.core.network)

    // The session file: DataStore on Okio storage (it can replace a file on Windows too, where
    // tests run), encrypted with a Tink keyset locked by the Android Keystore.
    implementation(libs.androidx.datastore.core.okio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tink.android)

    testImplementation(libs.kotlinx.coroutines.test)
}
