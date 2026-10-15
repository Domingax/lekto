package app.lekto.tools.dictionaries

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The language manifest's reproducibility evidence (issues #145, #161): the
 * manifest is a pure union of the packs and difficulty tables a run published, so
 * the catalogue and the artefacts cannot drift, and that manifest is pinned in
 * `golden/language-manifest.json` until a change to the catalogue is deliberate.
 */
class LanguageManifestGoldenTest {

    @Test
    fun `the manifest is built from the pipeline's own output`() {
        val first = buildLanguageManifest(DICTIONARIES, DIFFICULTIES)
        val second = buildLanguageManifest(DICTIONARIES, DIFFICULTIES)

        assertEquals(first.canonicalJson(), second.canonicalJson(), "the builder must be deterministic")
        assertEquals(golden(), first.canonicalJson(), "update the golden only when the manifest changes on purpose")
    }
}

private val DICTIONARIES = listOf(LanguagePair("en", "fr"))
private val DIFFICULTIES = listOf("en")

private fun golden(): String = requireNotNull(LanguageManifestGoldenTest::class.java.getResourceAsStream(GOLDEN)) {
    "the committed golden must be on the test classpath"
}.bufferedReader().use { it.readText() }.trimEnd('\n')

private const val GOLDEN: String = "/golden/language-manifest.json"
