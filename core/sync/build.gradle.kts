// SyncEngine, SyncWorker and SyncTriggers (D8, technical-plan.md §8). Repositories never start sync.
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.hilt)
}

android {
    namespace = "app.ulap.core.sync"
    // Robolectric reads the merged manifest and resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    // SyncRepository and SyncSignInPreparer, which this module implements.
    implementation(projects.core.domain)
    implementation(projects.core.common)
    // check_ins, pull_seen and sync_position; SyncLock.
    implementation(projects.core.database)
    // SyncApi, with the :shared sync types (the DTOs stay in core:network).
    implementation(projects.core.network)
    // Who is signed in: sync runs only then.
    implementation(projects.core.auth)
    implementation(projects.shared)
    // withTransaction: each sync step is one Room transaction.
    implementation(libs.room.ktx)

    // One unique background job, with backoff and a network constraint.
    implementation(libs.androidx.work.runtime)
    // @HiltWorker: SyncWorker gets SyncEngine injected.
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    // ProcessLifecycleOwner: "the app came to the foreground".
    implementation(libs.androidx.lifecycle.process)

    // FakeSyncRepository for SyncTriggers' tests.
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
