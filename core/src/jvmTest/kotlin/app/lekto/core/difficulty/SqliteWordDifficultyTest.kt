package app.lekto.core.difficulty

import app.lekto.core.dictionary.SqlitePackDatabaseFactory
import app.lekto.testkit.TestResources
import app.lekto.testkit.WordDifficultyContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

/**
 * The real **Difficulty table** reader on the JVM: the shared [WordDifficultyContract]
 * against the SQLite artifact the pipeline actually produces
 * (`tools/dictionaries` output committed as a test fixture), plus the wordfreq
 * attribution the screen renders (ADR-0029; docs/testing.md, "Test levels").
 */
class SqliteWordDifficultyTest :
    FunSpec({

        val roots = mutableListOf<File>()

        fun fixture(): String {
            val dir = Files.createTempDirectory("lekto-difficulty").toFile().also(roots::add)
            val file = File(dir, "en-sample.sqlite")
            file.writeBytes(TestResources.bytes("/difficulty/en-sample.sqlite"))
            return file.path
        }

        afterSpec { roots.forEach(File::deleteRecursively) }

        WordDifficultyContract { SqlWordDifficulty(SqlitePackDatabaseFactory.open(fixture())) }
            .cases()
            .forEach { case -> test(case.name) { case.body() } }

        test("the table carries the wordfreq attribution the screen renders") {
            val table = SqlWordDifficulty(SqlitePackDatabaseFactory.open(fixture()))

            table.metadata.license shouldBe "CC BY-SA 4.0"
            table.metadata.attribution?.contains("Robyn Speer") shouldBe true
            table.metadata.source?.contains("wordfreq") shouldBe true
            table.metadata.sourceData?.contains("SUBTLEX") shouldBe true
            table.close()
        }
    })
