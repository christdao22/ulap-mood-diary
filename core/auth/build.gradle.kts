// The signed-in session: the token store (DataStore + Tink, ulap-reference.md §5), the refresh
// mutex and AccountRepository. Credential Manager needs an Activity, so it sits with the sign-in
// sheet in feature:account, which hands the Google ID token to AccountRepository.
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
    // AccessTokenSource, which this module implements, and the auth API calls.
    implementation(projects.core.network)
    // AccountRepository, which features use to sign in, is implemented here.
    implementation(projects.core.domain)
    // AccountRules (the email's normal form) and the API error codes.
    implementation(projects.shared)

    // The session file: DataStore on Okio storage (it can replace a file on Windows too, where
    // tests run), encrypted with a Tink keyset locked by the Android Keystore.
    implementation(libs.androidx.datastore.core.okio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tink.android)

    testImplementation(libs.kotlinx.coroutines.test)
}
