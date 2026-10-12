package app.lekto.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The difficulty table's format handshake is declared twice — once by the
 * producer (`tools/dictionaries`, which depends on no Lekto module) and once by
 * the consumer (`core`, which cannot depend on the tool) — so a drift would ship
 * a reader that refuses every table the CI just built. This asserts the two
 * constants are equal, the synchronisation guard ADR-0029 names (ADR-0013).
 */
class DifficultyFormatSynchronisationTest :
    FunSpec({

        test("the difficulty producer and the difficulty consumer agree on the format version") {
            val root = RepositoryScanner.repositoryRoot()
            val producer = File(root, "tools/dictionaries/src/main/kotlin/app/lekto/tools/dictionaries")

            val produced = constant(file = File(producer, "DifficultyFormat.kt"), name = "VERSION")
            val consumed = constant(
                file = File(root, "core/src/commonMain/kotlin/app/lekto/core/difficulty/WordDifficulty.kt"),
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
