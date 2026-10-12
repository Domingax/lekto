package app.lekto.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The language manifest's format handshake is declared twice — once by the
 * producer (`tools/dictionaries`, which depends on no Lekto module) and once by
 * the consumer (`core`, which cannot depend on the tool) — so a drift would ship
 * an app that refuses the very catalogue the CI just published. This asserts the
 * two constants are equal, the synchronisation guard `LanguageManifestFormat`
 * names (ADR-0013).
 */
class LanguageManifestFormatSynchronisationTest :
    FunSpec({

        test("the manifest producer and the manifest consumer agree on the format version") {
            val root = RepositoryScanner.repositoryRoot()

            val produced = constant(
                file = File(
                    root,
                    "tools/dictionaries/src/main/kotlin/app/lekto/tools/dictionaries/LanguageManifestFormat.kt",
                ),
                name = "VERSION",
            )
            val consumed = constant(
                file = File(root, "core/src/commonMain/kotlin/app/lekto/core/language/LanguageManifest.kt"),
                name = "FORMAT_VERSION",
            )

            consumed shouldBe produced
        }
    })

/** The integer value of `const val [name]: Int = <n>` in [file]. */
private fun constant(file: File, name: String): Int {
    val match = Regex("""const val $name: Int = (\d+)""").find(file.readText())
    return requireNotNull(match) { "no 'const val $name: Int = …' in ${file.path}" }.groupValues[1].toInt()
}
