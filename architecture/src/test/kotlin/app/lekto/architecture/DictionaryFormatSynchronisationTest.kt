package app.lekto.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The pack format handshake is declared twice — once by the producer
 * (`tools/dictionaries`, which depends on no Lekto module) and once by the
 * consumer (`core`, which cannot depend on the tool) — so a drift would ship a
 * reader that refuses every pack the CI just built. This asserts the two
 * constants are equal, the synchronisation guard `PackFormat` names (ADR-0013).
 */
class DictionaryFormatSynchronisationTest :
    FunSpec({

        test("the pack producer and the pack consumer agree on the format version") {
            val root = RepositoryScanner.repositoryRoot()

            val produced = constant(
                file = File(root, "tools/dictionaries/src/main/kotlin/app/lekto/tools/dictionaries/PackFormat.kt"),
                name = "VERSION",
            )
            val consumed = constant(
                file = File(root, "core/src/commonMain/kotlin/app/lekto/core/dictionary/DictionaryPack.kt"),
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
