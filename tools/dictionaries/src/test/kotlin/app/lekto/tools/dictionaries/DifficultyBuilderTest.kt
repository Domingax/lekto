package app.lekto.tools.dictionaries

import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The difficulty transform's rules, pinned against the committed pack fixture and
 * a small frequency fixture: form frequencies aggregated onto word keys, ranking,
 * calibration cuts and deterministic ordering (ADR-0029).
 *
 * The production cut sizes (1500…5000) are far larger than a fixture can hold, so
 * these cases use small cut sizes; the production cuts are pinned separately by
 * [CalibrationTest].
 */
class DifficultyBuilderTest {

    @Test
    fun `aggregates a form's frequency onto its lemma`() {
        val rows = fixtureDifficulty().rows.associate { it.word to it.band }

        // blorpled (100) + blorples (50) + blorple (30) = 180, the heaviest key.
        assertEquals("A1", rows["blorple"])
    }

    @Test
    fun `maps an inflected form through the pack's form index`() {
        val rows = fixtureDifficulty().rows.associate { it.word to it.band }

        assertEquals("B1", rows["plim"], "plimmer resolves to its lemma plim")
        assertEquals("B2", rows["frungé"], "frungés resolves to its lemma frungé")
    }

    @Test
    fun `ranks keys by aggregated weight and cuts them into bands`() {
        val content = fixtureDifficulty()

        assertEquals(
            listOf("blorple", "glik", "plim", "frungé", "quor", "vrok"),
            content.rows.map(DifficultyRow::word),
        )
        assertEquals(listOf("A1", "A2", "B1", "B2", "C1", "C2"), content.rows.map(DifficultyRow::band))
    }

    @Test
    fun `a key below the calibrated top carries no band`() {
        val content = buildDifficulty(cutSizes = listOf(1, 2, 3))

        assertEquals(listOf("blorple", "glik", "plim"), content.rows.map(DifficultyRow::word))
        assertEquals(3, content.stats.bandedWords)
        assertEquals(6, content.stats.wordKeys)
    }

    @Test
    fun `a form the pack does not know is keyed by its normalised surface`() {
        val rows = buildDifficulty(frequencies = "Zebra\t100\n").rows.associate { it.word to it.band }

        assertTrue(rows.containsKey("zebra"), "an unknown form keeps its normalised surface as the key")
    }

    @Test
    fun `a key with no frequency has no band`() {
        val words = fixtureDifficulty().rows.map(DifficultyRow::word)

        assertNull(words.firstOrNull { it == "plom" }, "plom is in the pack but not in the frequency list")
    }

    @Test
    fun `the same input yields the same table`() {
        assertEquals(fixtureDifficulty().canonicalJson(), fixtureDifficulty().canonicalJson())
    }

    @Test
    fun `records the calibration and the measured shape`() {
        val content = fixtureDifficulty()

        assertEquals(CALIBRATION, content.calibration)
        assertEquals(8, content.stats.frequencyForms)
        assertEquals(6, content.stats.wordKeys)
        assertEquals(6, content.stats.bandedWords)
    }

    @Test
    fun `a malformed frequency line is skipped`() {
        val content = buildDifficulty(frequencies = "blorple\t10\nnot-a-line\nbroken\tnot-a-number\n")

        assertEquals(1, content.stats.frequencyForms)
    }

    @Test
    fun `an empty form index keys every word by its normalised surface`() {
        // A reading language with no dictionary pack (issue #161) has no lemmas to
        // key by, so the normalised surface form is the key — the same fallback the
        // tokeniser uses when the dictionary knows no lemma.
        val content = DifficultyBuilder(forms = emptyList(), provenance = FIXTURE_PROVENANCE, language = "es")
            .build(BufferedReader(StringReader("Zebra\t100\n")))

        assertEquals(listOf("zebra"), content.rows.map(DifficultyRow::word))
        assertEquals(listOf("es"), content.rows.map(DifficultyRow::language))
    }
}

/** The small calibration the fixture is built against, so six words span all six bands. */
private val CALIBRATION = DifficultyCalibration(listOf(1, 2, 3, 4, 5, 6), "test", "test")

private val FIXTURE_PROVENANCE = PackProvenance("test://fixture", "0".repeat(64), "2026-10-04")

private fun fixtureDifficulty(): DifficultyContent = buildDifficulty()

private fun buildDifficulty(
    frequencies: String = fixtureFrequencyText(),
    cutSizes: List<Int> = CALIBRATION.cutSizes,
): DifficultyContent {
    val pack = fixturePack()
    return DifficultyBuilder(
        forms = pack.forms,
        provenance = pack.provenance,
        calibration = CALIBRATION.copy(cutSizes = cutSizes),
    ).build(BufferedReader(StringReader(frequencies)))
}

private fun fixtureFrequencyText(): String =
    requireNotNull(DifficultyBuilderTest::class.java.getResourceAsStream("/difficulty-frequency-sample.tsv")) {
        "the committed frequency fixture must be on the test classpath"
    }.bufferedReader().use(BufferedReader::readText)

private fun fixturePack(): PackContent = PackBuilder(FIXTURE_PROVENANCE).build(
    requireNotNull(DifficultyBuilderTest::class.java.getResourceAsStream("/en-fr-sample.jsonl")) {
        "the committed pack fixture must be on the test classpath"
    }.bufferedReader(),
)
