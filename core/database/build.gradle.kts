// Room, encrypted with SQLCipher (D3, technical-plan.md §6). Entities never leave this module:
// repositories map them to :shared types.
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

    // SupportSQLiteOpenHelper: the hook SQLCipher plugs into Room with.
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher)
    // Wraps the database passphrase with an Android Keystore key.
    implementation(libs.tink.android)

    // DAO tests on the JVM: Robolectric stands in for Android, with plain (unencrypted)
    // in-memory SQLite. The encryption itself is tested on a phone or emulator (androidTest).
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // MigrationTestHelper: builds each schema version from schemas/ and checks the migrations.
    testImplementation(libs.room.testing)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
