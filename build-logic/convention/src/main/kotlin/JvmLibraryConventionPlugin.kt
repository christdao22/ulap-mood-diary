import app.ulap.buildlogic.addUnitTestDependencies
import app.ulap.buildlogic.configureKotlinJvm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * A plain Kotlin module with no Android dependency, such as `core:model` and `core:domain`.
 * Its tests run on the JVM in seconds, without an emulator.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.jvm")
            // Lets the app's Android Lint run (checkDependencies) analyze this module too.
            apply(plugin = "com.android.lint")
            configureKotlinJvm()
            addUnitTestDependencies()
        }
    }
}
