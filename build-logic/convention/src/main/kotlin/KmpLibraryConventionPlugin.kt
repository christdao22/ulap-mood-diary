import app.ulap.buildlogic.Sdk
import app.ulap.buildlogic.configureKotlinMultiplatform
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * The Kotlin Multiplatform `shared` module (ulap-reference.md §4.1): one copy of the models,
 * rules and API DTOs, compiled for the server (`jvm()`) and the app (`android`). iOS targets
 * join later (PHASES.md, Phase 11). No Android or DI library may enter commonMain.
 *
 * The module's own build file sets the Android namespace; everything else is here.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
            // AGP 9's plugin for the Android target of a KMP module (replaces com.android.library there).
            apply(plugin = "com.android.kotlin.multiplatform.library")
            // Lets the app's Android Lint (checkDependencies) analyze this module too.
            apply(plugin = "com.android.lint")
            configureKotlinMultiplatform(minSdk = Sdk.MIN, compileSdk = Sdk.COMPILE)
        }
    }
}
