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
    // SettingsRepository: the device ID and language every request carries (api-spec.md §2).
    implementation(projects.core.domain)
    // The API DTOs, the error codes and the JSON settings the server uses too.
    implementation(projects.shared)

    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // MockEngine answers requests in tests without a server.
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
