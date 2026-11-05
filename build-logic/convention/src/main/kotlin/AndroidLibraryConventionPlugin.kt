import app.ulap.buildlogic.Sdk
import app.ulap.buildlogic.addUnitTestDependencies
import app.ulap.buildlogic.configureKotlinAndroid
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/** A `core:*` or `feature:*` module that needs Android (resources, Context, Compose, Room). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                // A library has no targetSdk of its own; these keep its tests and lint
                // checks at the app's level.
                testOptions.targetSdk = Sdk.TARGET
                lint.targetSdk = Sdk.TARGET
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            addUnitTestDependencies()
        }
    }
}
