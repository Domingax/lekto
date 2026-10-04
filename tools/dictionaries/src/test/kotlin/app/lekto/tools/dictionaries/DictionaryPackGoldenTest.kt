package app.lekto.tools.dictionaries

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reproducibility evidence (ticket #17): the same committed fixture yields the
 * same canonical pack every time, and that pack is pinned in
 * `golden/en-fr-sample.pack.json` so a transform change that silently reorders,
 * re-words or re-groups the output fails here until the golden is deliberately
 * updated.
 *
 * The golden is on the canonical JSON, not the SQLite bytes: the bytes are proven
 * stable by [SqlitePackWriterTest.is reproducible byte for byte] under the pinned
 * driver, while this test pins the *meaning* of the transform across drivers.
 */
class DictionaryPackGoldenTest {

    @Test
    fun `the same fixture yields the same canonical pack`() {
        val first = fixturePack()
        val second = fixturePack()

        assertEquals(first.canonicalJson(), second.canonicalJson(), "the transform must be deterministic")
        assertEquals(golden(), first.canonicalJson(), "update the golden only when the transform changes on purpose")
    }
}

private fun fixturePack(): PackContent = PackBuilder(FIXTURE_PROVENANCE).build(
    requireNotNull(DictionaryPackGoldenTest::class.java.getResourceAsStream("/en-fr-sample.jsonl")) {
        "the committed fixture must be on the test classpath"
    }.bufferedReader(),
)

private fun golden(): String =
    requireNotNull(DictionaryPackGoldenTest::class.java.getResourceAsStream("/golden/en-fr-sample.pack.json")) {
        "the committed golden must be on the test classpath"
    }.bufferedReader().use { it.readText() }.trimEnd('\n')

private val FIXTURE_PROVENANCE = PackProvenance("test://fixture", "0".repeat(64), "2026-10-04")
