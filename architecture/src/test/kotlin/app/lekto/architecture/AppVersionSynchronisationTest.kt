package app.lekto.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The version the **About** screen shows is declared twice — once by the Kotlin
 * constant an installed copy reads, and once by the Gradle build the store sees —
 * so a release that moves one and not the other would tell two different truths.
 * This asserts they agree (issue #214).
 */
class AppVersionSynchronisationTest :
    FunSpec({

        test("the About version and the Android versionName agree") {
            val root = RepositoryScanner.repositoryRoot()

            val about = constant(
                file = File(root, "app/src/commonMain/kotlin/app/lekto/AppVersion.kt"),
                name = "APP_VERSION",
            )
            val build = versionName(File(root, "app/build.gradle.kts"))

            about shouldBe build
        }
    })

/** The value of `const val [name]: String = "…"` in [file]. */
private fun constant(file: File, name: String): String {
    val match = Regex("""const val $name: String = "([^"]+)"""").find(file.readText())
    return requireNotNull(match) { "no 'const val $name: String = \"…\"' in ${file.path}" }.groupValues[1]
}

/** The literal `versionName = "…"` the Android build declares in [file]. */
private fun versionName(file: File): String {
    val match = Regex("""versionName = "([^"]+)"""").find(file.readText())
    return requireNotNull(match) { "no 'versionName = \"…\"' in ${file.path}" }.groupValues[1]
}
