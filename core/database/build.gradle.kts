// Room, encrypted with SQLCipher (D3, technical-plan.md §6). SQLCipher arrives with its own
// Phase 2 task. Entities never leave this module: repositories map them to :shared types.
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.android.room)
    alias(libs.plugins.ulap.hilt)
}

android {
    namespace = "app.ulap.core.database"
    // Robolectric reads the merged manifest and resources, and the schemas as test assets.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(projects.core.model)
    // Entities use the shared enums (Trigger, SupportType), so the DAOs' users see them too.
    api(projects.shared)

    // DAO tests on the JVM: Robolectric stands in for Android, with plain in-memory SQLite.
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // MigrationTestHelper: builds each schema version from schemas/ and checks the migrations.
    testImplementation(libs.room.testing)
}
