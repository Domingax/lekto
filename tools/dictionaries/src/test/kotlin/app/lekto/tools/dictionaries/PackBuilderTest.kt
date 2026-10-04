package app.lekto.tools.dictionaries

import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transform's rules, pinned against the committed fixture: filtering,
 * normalisation, per-sense translations, duplicate-key merging, form reversal
 * and deterministic ambiguity resolution.
 */
class PackBuilderTest {

    @Test
    fun `filters to English entries carrying a French translation`() {
        val pack = fixturePack()

        assertNull(pack.entries.firstOrNull { it.lemma == "quib" }, "no French translation")
        assertNull(pack.entries.firstOrNull { it.lemma == "zork" }, "not an English entry")
        assertNull(pack.entries.firstOrNull { it.lemma == "glim glom" }, "multi-word headword")
        assertEquals(9, pack.stats.englishEntriesWithFrench)
    }

    @Test
    fun `normalises keys and drops multi-word surfaces`() {
        val pack = fixturePack()

        assertTrue(pack.entries.any { it.lemma == "frungé" })
        assertTrue(pack.forms.any { it.surface == "frungés" && it.lemma == "frungé" })
        assertFalse(pack.forms.any { it.surface == "having blorple" })
    }

    @Test
    fun `attaches translations to their sense and merges duplicate keys`() {
        val blorple = fixturePack().entries.single { it.lemma == "blorple" && it.partOfSpeech == "verb" }

        assertEquals(
            listOf("To move swiftly.", "To flow.", "A point scored."),
            blorple.senses.map(PackSense::definition),
        )
        assertEquals(listOf("blorper"), blorple.senses[0].translations)
        assertEquals(listOf("fluxer"), blorple.senses[1].translations)
        assertEquals(listOf("blorpoint"), blorple.senses[2].translations)
    }

    @Test
    fun `reverses forms and maps inflections through form_of`() {
        val forms = fixturePack().forms.associate { it.surface to it.lemma }

        assertEquals("blorple", forms["blorpled"])
        assertEquals("blorple", forms["blorples"])
        assertEquals("frungé", forms["frungés"])
        assertEquals("quor", forms["quor"])
    }

    @Test
    fun `resolves surface ambiguity deterministically`() {
        val forms = fixturePack().forms.associate { it.surface to it.lemma }

        assertEquals("glik", forms["glik"], "a surface's own lemma wins over a competitor")
        assertEquals("plim", forms["plimmer"], "a tie breaks on the shorter, then the smallest lemma")
    }

    @Test
    fun `gives a sense-less entry a placeholder sense`() {
        val vrok = fixturePack().entries.single { it.lemma == "vrok" }

        assertEquals(SENSE_WITHOUT_DEFINITION, vrok.senses.single().definition)
        assertEquals(listOf("vrok"), vrok.senses.single().translations)
    }

    @Test
    fun `counts a malformed line without failing the whole build`() {
        val pack = buildPack(
            """
            |{"word":"x","lang_code": "en",
            |{"word":"blorple","lang_code":"en","pos":"verb","senses":[{"glosses":["To move swiftly."]}],"translations":[{"lang_code":"fr","word":"blorper"}]}
            """.trimMargin(),
        )

        assertEquals(2, pack.stats.totalLines)
        assertEquals(1, pack.stats.malformedLines)
        assertEquals("blorple", pack.entries.single().lemma)
    }
}

private val FIXTURE_PROVENANCE = PackProvenance("test://fixture", "0".repeat(64), "2026-10-04")

private fun fixturePack(): PackContent = buildPack(fixtureText())

private fun buildPack(jsonl: String): PackContent =
    PackBuilder(FIXTURE_PROVENANCE).build(BufferedReader(StringReader(jsonl)))

private fun fixtureText(): String =
    requireNotNull(PackBuilderTest::class.java.getResourceAsStream("/en-fr-sample.jsonl")) {
        "the committed fixture must be on the test classpath"
    }.bufferedReader().use(BufferedReader::readText)
