// The app shell (README §5): Application, MainActivity, navigation and the build types. It wires
// the core and feature modules together and holds no screens of its own.
plugins {
    alias(libs.plugins.ulap.android.application)
    alias(libs.plugins.ulap.android.application.compose)
    alias(libs.plugins.ulap.hilt)
}

// Per-machine and CI values come from ~/.gradle/gradle.properties (never this repo) or -P flags,
// all prefixed ulap. (README §8, ulap-reference.md §4.3).
fun ulapProperty(name: String): Provider<String> = providers.gradleProperty("ulap.$name")

// Where each build type finds the API. Debug defaults to the emulator's name for this computer.
fun apiUrl(buildType: String, default: String): String = ulapProperty("apiUrl.$buildType").getOrElse(default)

// The Google OAuth *Web* client ID: the audience the server checks, so it must equal
// GOOGLE_WEB_CLIENT_ID in .env (README §6.6). Not a secret (it ships in the APK), but it belongs
// to one Google Cloud project, so it stays out of the repo. Empty hides the Google button.
val googleWebClientId: String = ulapProperty("googleWebClientId").getOrElse("")

// Crash reports (D11). Empty locally: the app then reports nothing.
val sentryDsn: String = ulapProperty("sentryDsn").getOrElse("")

// CI sets these for releases (ulap-reference.md §9); local builds are 0.1.0 (1).
val appVersionCode: Int = ulapProperty("versionCode").map { it.toInt() }.getOrElse(1)
val appVersionName: String = ulapProperty("versionName").getOrElse("0.1.0")

// The upload key (README §7.2). Without it, release is unsigned and staging uses the debug key.
val uploadStoreFile: String? = ulapProperty("upload.storeFile").orNull

android {
    namespace = "app.ulap.android"

    defaultConfig {
        applicationId = "app.ulap.android"
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = ulapProperty("upload.storePassword").get()
                keyAlias = ulapProperty("upload.keyAlias").get()
                keyPassword = ulapProperty("upload.keyPassword").get()
            }
        }
    }

    buildFeatures {
        // Generates BuildConfig, which carries API_BASE_URL and the client ID into the code.
        buildConfig = true
    }

    // Robolectric reads the merged manifest and resources (BackupRulesTest checks them).
    testOptions.unitTests.isIncludeAndroidResources = true

    // debug, staging and release, no flavors (ulap-reference.md §4.3). Each has its own
    // application ID, so all three can sit side by side on one phone.
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE_URL", "\"${apiUrl("debug", "http://10.0.2.2:8080/v1")}\"")
        }
        release {
            // R8: removes unused code and resources and shortens names.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("upload")
            // Placeholder domain until the server is deployed (api-spec.md §2).
            buildConfigField("String", "API_BASE_URL", "\"${apiUrl("release", "https://api.ulap.app/v1")}\"")
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            // Libraries publish only debug and release; staging uses their release variant.
            matchingFallbacks += "release"
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${apiUrl("staging", "https://staging-api.ulap.app/v1")}\"",
            )
        }
    }
}

dependencies {
    implementation(projects.core.auth)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.core.sync)
    implementation(projects.core.ui)
    implementation(projects.feature.account)
    implementation(projects.feature.checkin)
    implementation(projects.feature.logs)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.reports)

    // AppCompatActivity: per-app language on Android 8–12 (technical-plan.md §11).
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // The NavHost and the tab bar's back stack. Features add their own screens to it.
    implementation(libs.androidx.navigation.compose)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
