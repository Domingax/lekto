package app.lekto.tools.dictionaries

import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The language manifest's reproducibility evidence (issue #145): the manifest is
 * built from the pipeline's own output — the pack and the difficulty table the
 * same fixtures produce — so the catalogue and the artefacts cannot drift, and
 * that manifest is pinned in `golden/language-manifest.json` until a change to
 * the catalogue is deliberate.
 */
class LanguageManifestGoldenTest {

    @Test
    fun `the manifest is built from the pipeline's own output`() {
        val first = buildLanguageManifest(fixturePack(), fixtureDifficulty())
        val second = buildLanguageManifest(fixturePack(), fixtureDifficulty())

        assertEquals(first.canonicalJson(), second.canonicalJson(), "the builder must be deterministic")
        assertEquals(golden(), first.canonicalJson(), "update the golden only when the manifest changes on purpose")
    }
}

private fun fixturePack(): PackContent = PackBuilder(FIXTURE_PROVENANCE).build(
    requireNotNull(LanguageManifestGoldenTest::class.java.getResourceAsStream("/en-fr-sample.jsonl"))
        .bufferedReader(),
)

private fun fixtureDifficulty(): DifficultyContent = DifficultyBuilder(
    pack = fixturePack(),
    calibration = DifficultyCalibration(listOf(1, 2, 3, 4, 5, 6), "test", "test"),
).build(BufferedReader(StringReader(fixtureFrequency())))

private fun fixtureFrequency(): String =
    requireNotNull(LanguageManifestGoldenTest::class.java.getResourceAsStream(FREQUENCY_FIXTURE)) {
        "the committed fixture must be on the test classpath"
    }.bufferedReader().use(BufferedReader::readText)

private fun golden(): String = requireNotNull(LanguageManifestGoldenTest::class.java.getResourceAsStream(GOLDEN)) {
    "the committed golden must be on the test classpath"
}.bufferedReader().use { it.readText() }.trimEnd('\n')

private const val FREQUENCY_FIXTURE = "/difficulty-frequency-sample.tsv"
private const val GOLDEN = "/golden/language-manifest.json"
private val FIXTURE_PROVENANCE = PackProvenance("test://fixture", "0".repeat(64), "2026-10-04")
