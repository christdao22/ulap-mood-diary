// Repositories: the only place Room rows and network DTOs meet, mapped to :shared domain types
// and bound to core:domain's interfaces (README §5).
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.hilt)
}

android {
    namespace = "app.ulap.core.data"
    // Robolectric reads the merged manifest and resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    // withTransaction: one Room transaction per user action (ulap-reference.md §5).
    implementation(libs.room.ktx)

    // Repository tests run on a real in-memory Room database under Robolectric.
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
