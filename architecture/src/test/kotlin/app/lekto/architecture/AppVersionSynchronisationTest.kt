package app.lekto.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The version the **About** screen shows is declared in Kotlin, while the build
 * and the desktop installers read `lekto.version` from `gradle.properties`, so a
 * release that moves one and not the other would tell two different truths. This
 * asserts they agree (issues #214 and #29).
 */
class AppVersionSynchronisationTest :
    FunSpec({

        test("the About version and the build version agree") {
            val root = RepositoryScanner.repositoryRoot()

            val about = constant(
                file = File(root, "app/src/commonMain/kotlin/app/lekto/AppVersion.kt"),
                name = "APP_VERSION",
            )
            val build = File(root, "app/build.gradle.kts").readText()
            val shared = requireNotNull(DesktopPackaging.version(File(root, "gradle.properties").readText())) {
                "gradle.properties has no well-formed lekto.version"
            }

            about shouldBe shared
            build.contains("versionName = appVersion") shouldBe true
        }
    })

/** The value of `const val [name]: String = "…"` in [file]. */
private fun constant(file: File, name: String): String {
    val match = Regex("""const val $name: String = "([^"]+)"""").find(file.readText())
    return requireNotNull(match) { "no 'const val $name: String = \"…\"' in ${file.path}" }.groupValues[1]
}
