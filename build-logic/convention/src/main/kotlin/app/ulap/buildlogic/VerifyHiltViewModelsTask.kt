package app.ulap.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Fails the build when a `@HiltViewModel` class has no Hilt code generated for it.
 *
 * Hilt finds ViewModels through a map it builds at compile time. If KSP's output for a module goes
 * missing (Gradle once reused an empty cached result), the app still builds, but opening that
 * screen crashes with "Cannot create an instance of class …ViewModel". This check turns that
 * crash on the phone into a build error.
 */
@DisableCachingByDefault(because = "A quick file check. It must run every build to catch a bad cached KSP result.")
abstract class VerifyHiltViewModelsTask : DefaultTask() {
    /** The module's own Kotlin sources, scanned for `@HiltViewModel`. */
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** KSP's output for this variant, e.g. build/generated/ksp/debug. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kspOutput: DirectoryProperty

    @TaskAction
    fun verify() {
        val generated = kspOutput.get().asFile
            .walkTopDown()
            .filter { it.isFile }
            .map { it.nameWithoutExtension }
            .toSet()

        val missing = sources.asFileTree.files
            .filter { it.extension == "kt" }
            .flatMap { file -> HILT_VIEW_MODEL.findAll(file.readText()).map { it.groupValues[1] }.toList() }
            .filter { "${it}_HiltModules" !in generated }
            .sorted()

        if (missing.isNotEmpty()) {
            throw GradleException(
                "Hilt code is missing for ${missing.joinToString()}. The app would crash when it " +
                    "opens these screens. Regenerate it with:\n" +
                    "  ./gradlew ${path.substringBeforeLast(':')}:${kspTaskName()} --rerun",
            )
        }
    }

    private fun kspTaskName(): String = "ksp" + name.removePrefix("verify").removeSuffix("HiltViewModels") + "Kotlin"

    private companion object {
        // @HiltViewModel, then any other annotations or modifiers, then the class name.
        val HILT_VIEW_MODEL = Regex("""@HiltViewModel\b[\s\S]*?\bclass\s+(\w+)""")
    }
}
