package app.ulap.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

// Adapted from android/nowinandroid build-logic AndroidCompose.kt (Apache-2.0).

/** Turns on Compose and adds the BOM, so Compose libraries need no versions of their own. */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    commonExtension.apply {
        buildFeatures.apply {
            compose = true
        }
    }
    dependencies {
        val bom = platform(libs.findLibrary("androidx-compose-bom").get())
        "implementation"(bom)
        "androidTestImplementation"(bom)
        "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        // The @Preview renderer and layout inspector; never shipped in release builds.
        "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}
