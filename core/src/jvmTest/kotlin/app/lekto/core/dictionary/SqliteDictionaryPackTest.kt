package app.lekto.core.dictionary

import app.lekto.testkit.DictionaryPackContract
import app.lekto.testkit.TestResources
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files
import kotlin.test.assertFailsWith

/**
 * The real dictionary reader on the JVM: the shared [DictionaryPackContract]
 * against the SQLite pack the pipeline actually produces
 * (`tools/dictionaries` output committed as a test fixture), plus the attribution
 * the screen reads (issue #18; docs/testing.md, "Test levels").
 */
class SqliteDictionaryPackTest :
    FunSpec({

        val roots = mutableListOf<File>()

        fun fixture(): String {
            val dir = Files.createTempDirectory("lekto-dictionary").toFile().also(roots::add)
            val file = File(dir, "en-fr-sample.sqlite")
            file.writeBytes(TestResources.bytes("/dictionary/en-fr-sample.sqlite"))
            return file.path
        }

        afterSpec { roots.forEach(File::deleteRecursively) }

        DictionaryPackContract { SqlDictionaryPack(SqlitePackDatabaseFactory.open(fixture())) }
            .cases()
            .forEach { case -> test(case.name) { case.body() } }

        test("opening a path with no pack reports it missing rather than creating an empty one") {
            val missing = File(Files.createTempDirectory("lekto-dictionary").toFile().also(roots::add), "absent.sqlite")

            assertFailsWith<DictionaryPackMissing> { SqlitePackDatabaseFactory.open(missing.path) }
            missing.exists() shouldBe false
        }

        test("the pack carries the full attribution the screen renders") {
            val pack = SqlDictionaryPack(SqlitePackDatabaseFactory.open(fixture()))

            pack.metadata.license shouldBe "CC BY-SA 4.0"
            pack.metadata.attribution shouldBe "Wiktionary contributors"
            pack.metadata.modifications shouldNotBe null
            pack.metadata.notice?.contains("CC BY-SA") shouldBe true
            pack.close()
        }
    })
