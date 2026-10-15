package app.lekto.tools.dictionaries

import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The difficulty transform's reproducibility evidence (ADR-0029): the same
 * committed fixtures yield the same canonical table every time, and that table is
 * pinned in `golden/difficulty-en-sample.json` so a transform change that silently
 * reorders, re-bands or re-keys the output fails here until the golden is
 * deliberately updated.
 *
 * The golden is on the canonical JSON, not the SQLite bytes: the bytes are proven
 * stable by [SqliteDifficultyWriterTest] under the pinned driver, while this test
 * pins the *meaning* of the transform across drivers.
 */
class DifficultyGoldenTest {

    @Test
    fun `the same fixtures yield the same canonical table`() {
        val first = fixtureDifficulty()
        val second = fixtureDifficulty()

        assertEquals(first.canonicalJson(), second.canonicalJson(), "the transform must be deterministic")
        assertEquals(golden(), first.canonicalJson(), "update the golden only when the transform changes on purpose")
    }
}

private fun fixtureDifficulty(): DifficultyContent {
    val pack = PackBuilder(FIXTURE_PROVENANCE).build(
        requireNotNull(DifficultyGoldenTest::class.java.getResourceAsStream("/en-fr-sample.jsonl"))
            .bufferedReader(),
    )
    return DifficultyBuilder(
        forms = pack.forms,
        provenance = pack.provenance,
        calibration = DifficultyCalibration(listOf(1, 2, 3, 4, 5, 6), "test", "test"),
    ).build(
        BufferedReader(
            StringReader(
                requireNotNull(DifficultyGoldenTest::class.java.getResourceAsStream("/difficulty-frequency-sample.tsv"))
                    .bufferedReader().use(BufferedReader::readText),
            ),
        ),
    )
}

private fun golden(): String =
    requireNotNull(DifficultyGoldenTest::class.java.getResourceAsStream("/golden/difficulty-en-sample.json")) {
        "the committed golden must be on the test classpath"
    }.bufferedReader().use { it.readText() }.trimEnd('\n')

private val FIXTURE_PROVENANCE = PackProvenance("test://fixture", "0".repeat(64), "2026-10-04")
