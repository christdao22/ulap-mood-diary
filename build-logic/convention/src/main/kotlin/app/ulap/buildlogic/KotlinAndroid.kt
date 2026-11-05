package app.ulap.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Adapted from android/nowinandroid build-logic KotlinAndroid.kt (Apache-2.0).

private val JAVA_VERSION = JavaVersion.VERSION_17
private val JVM_TARGET = JvmTarget.JVM_17

/** Compile SDK, min SDK, Java level and Kotlin options for an Android module. */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = Sdk.COMPILE
        defaultConfig.apply {
            minSdk = Sdk.MIN
        }
        compileOptions.apply {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }
    }
    configureKotlin<KotlinAndroidProjectExtension>()

    // Robolectric simulates Android inside the JVM. For Android 16 it reaches into JDK
    // internals that modern JDKs lock away unless the test JVM opens them, and it loads a
    // native SQLite, which the JDK otherwise warns about.
    tasks.withType<Test>().configureEach {
        jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--enable-native-access=ALL-UNNAMED",
        )
    }
}

/** Java level and Kotlin options for a plain Kotlin (JVM) module. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    configureKotlin<KotlinJvmProjectExtension>()
}

/**
 * Targets and Kotlin options for the KMP `shared` module: `jvm()` for the server and the
 * `android` target (AGP's KMP library plugin) for the app. Both compile for Java 17.
 */
internal fun Project.configureKotlinMultiplatform(minSdk: Int, compileSdk: Int) {
    configure<KotlinMultiplatformExtension> {
        jvm {
            compilerOptions.jvmTarget = JVM_TARGET
        }
        targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
            this.compileSdk = compileSdk
            this.minSdk = minSdk
            compilerOptions.jvmTarget = JVM_TARGET
            // commonTest also runs as an Android host test, so the rules are checked on both targets.
            withHostTest {}
        }
        compilerOptions {
            allWarningsAsErrors = warningsAsErrors()
        }
    }
}

// Off by default so a deprecation warning doesn't block local work; CI passes
// -PwarningsAsErrors=true.
private fun Project.warningsAsErrors() =
    providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false)

private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() = configure<T> {
    val warningsAsErrors = warningsAsErrors()
    when (this) {
        is KotlinAndroidProjectExtension -> compilerOptions
        is KotlinJvmProjectExtension -> compilerOptions
        else -> error("Unsupported Kotlin extension ${T::class}")
    }.apply {
        jvmTarget = JVM_TARGET
        allWarningsAsErrors = warningsAsErrors
    }
}
