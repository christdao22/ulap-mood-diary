package app.ulap.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** The version catalog (gradle/libs.versions.toml), for use inside convention plugins. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** JUnit 4 plus kotlin-test's assertions for every module's unit tests. */
internal fun Project.addUnitTestDependencies() {
    dependencies {
        "testImplementation"(libs.findLibrary("kotlin-test-junit").get())
        "testImplementation"(libs.findLibrary("junit").get())
    }
}
