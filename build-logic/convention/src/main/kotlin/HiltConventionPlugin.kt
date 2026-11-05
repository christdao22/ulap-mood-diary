import app.ulap.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

// Adapted from android/nowinandroid build-logic HiltConventionPlugin.kt (Apache-2.0).

/**
 * Hilt dependency injection. Works in both plain Kotlin modules (only the annotations and
 * @Module support) and Android modules (the full Android integration).
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // KSP runs Hilt's code generator at compile time, so a missing binding is a
            // build error rather than a crash on the phone.
            apply(plugin = "com.google.devtools.ksp")
            dependencies {
                "ksp"(libs.findLibrary("hilt-compiler").get())
                "ksp"(libs.findLibrary("kotlin-metadata").get())
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    "implementation"(libs.findLibrary("hilt-core").get())
                }
            }

            // com.android.base is applied by both the application and library plugins.
            pluginManager.withPlugin("com.android.base") {
                apply(plugin = "com.google.dagger.hilt.android")
                dependencies {
                    "implementation"(libs.findLibrary("hilt-android").get())
                }
            }
        }
    }
}
