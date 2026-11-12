package app.lekto.tools.dictionaries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The sample transform (issue #297): a fixed word list and a fixed difficulty
 * table yield the six maps, one case per CEFR band, and a fixture dictionary
 * yields the glossary. The maps are the table's output composed with the
 * default-mastery rule (ADR-0030), not a palette written by hand, so the cases
 * name the arithmetic rather than a snapshot.
 */
class SampleBuilderTest {

    @Test
    fun `the A1 map keeps every word at or below A1 known and drops one level per band above`() {
        assertEquals(
            mapOf(
                "a" to "KNOWN",
                "boat" to "FAMILIAR",
                "calm" to "LEARNED",
                "near" to "KNOWN",
                "rock" to "UNKNOWN",
                "the" to "KNOWN",
                "wait" to "RECOGNIZED",
            ),
            mapFor(CefrLevelName.A1),
        )
    }

    @Test
    fun `the A2 map knows A2 and lets B1 to C1 drop one level each`() {
        assertEquals(
            mapOf(
                "a" to "KNOWN",
                "boat" to "LEARNED",
                "calm" to "KNOWN",
                "near" to "KNOWN",
                "rock" to "RECOGNIZED",
                "the" to "KNOWN",
                "wait" to "FAMILIAR",
            ),
            mapFor(CefrLevelName.A2),
        )
    }

    @Test
    fun `the B1 map knows B1 and lets B2 and C1 drop one level each`() {
        assertEquals(
            mapOf(
                "a" to "KNOWN",
                "boat" to "KNOWN",
                "calm" to "KNOWN",
                "near" to "KNOWN",
                "rock" to "FAMILIAR",
                "the" to "KNOWN",
                "wait" to "LEARNED",
            ),
            mapFor(CefrLevelName.B1),
        )
    }

    @Test
    fun `the B2 map knows B2 and lets C1 drop one level`() {
        assertEquals(
            mapOf(
                "a" to "KNOWN",
                "boat" to "KNOWN",
                "calm" to "KNOWN",
                "near" to "KNOWN",
                "rock" to "LEARNED",
                "the" to "KNOWN",
                "wait" to "KNOWN",
            ),
            mapFor(CefrLevelName.B2),
        )
    }

    @Test
    fun `the C1 map knows C1 and everything below it`() {
        assertEquals(
            mapOf(
                "a" to "KNOWN",
                "boat" to "KNOWN",
                "calm" to "KNOWN",
                "near" to "KNOWN",
                "rock" to "KNOWN",
                "the" to "KNOWN",
                "wait" to "KNOWN",
            ),
            mapFor(CefrLevelName.C1),
        )
    }

    @Test
    fun `the C2 map knows every word below it`() {
        assertEquals(
            mapOf(
                "a" to "KNOWN",
                "boat" to "KNOWN",
                "calm" to "KNOWN",
                "near" to "KNOWN",
                "rock" to "KNOWN",
                "the" to "KNOWN",
                "wait" to "KNOWN",
            ),
            mapFor(CefrLevelName.C2),
        )
    }

    @Test
    fun `a word the table does not place is unknown at every band`() {
        val sample = builder(difficulty = SampleFixtures.DIFFICULTY - "rock").build()

        sample.colourMaps.values.forEach { map ->
            assertEquals("UNKNOWN", map["rock"], "a word with no band carries no signal (ADR-0030)")
        }
        assertEquals(null, sample.difficulty["rock"], "the catalogue records the table's absent band")
    }

    @Test
    fun `an inflected surface takes the form index's word key`() {
        val source = SampleSource("en", "T", paragraphs = listOf("A boat waited near the rocks."))

        val sample = SampleBuilder(
            source,
            SampleFixtures.DIFFICULTY,
            SampleFixtures.FORM_INDEX,
            mapOf("fr" to entries()),
        )
            .build()

        assertEquals(setOf("a", "boat", "near", "rock", "the", "wait"), sample.colourMaps.getValue("C2").keys)
        assertEquals(mapOf("rocks" to "rock", "waited" to "wait"), sample.forms)
    }

    @Test
    fun `the glossary carries the highlighted words and covers every one`() {
        val sample = builder().build()

        val glossary = sample.glossaries.getValue("fr")
        assertEquals(setOf("boat", "calm", "rock", "wait"), glossary.entries.keys)
        assertEquals("The boat meaning.", glossary.entries.getValue("boat").single().senses.single().definition)
        assertEquals(listOf("fr-boat"), glossary.entries.getValue("boat").single().senses.single().translations)
    }

    @Test
    fun `the catalogue records the table's band for every word`() {
        val sample = builder().build()

        assertEquals(SampleFixtures.DIFFICULTY.toSortedMap(), sample.difficulty)
    }

    @Test
    fun `a glossary that misses a highlighted word fails the build`() {
        val incomplete = entries().filterNot { entry -> entry.lemma == "rock" }

        val error = assertFailsWith<IllegalArgumentException> {
            SampleBuilder(
                SampleFixtures.source(),
                SampleFixtures.DIFFICULTY,
                SampleFixtures.FORM_INDEX,
                mapOf("fr" to incomplete),
            )
                .build()
        }

        assertTrue("fr glossary does not cover" in error.message.orEmpty())
        assertTrue("rock" in error.message.orEmpty())
    }

    @Test
    fun `a language without spaces is segmented by ICU, not by characters`() {
        val keys = sampleWordKeys("ja", listOf("男が家にいる。"), emptyMap())

        assertEquals(listOf("いる", "が", "に", "家", "男"), keys)
    }

    @Test
    fun `the same source, table and pack yield the same catalogue`() {
        val first = SampleCatalogueDocument(SampleFormat.VERSION, listOf(builder().build()))
        val second = SampleCatalogueDocument(SampleFormat.VERSION, listOf(builder().build()))

        assertEquals(first.canonicalJson(), second.canonicalJson())
    }

    @Test
    fun `a sample without a paragraph or a title is refused`() {
        assertFailsWith<IllegalArgumentException> {
            SampleBuilder(
                SampleFixtures.source().copy(paragraphs = emptyList()),
                SampleFixtures.DIFFICULTY,
                SampleFixtures.FORM_INDEX,
                emptyMap(),
            )
                .build()
        }
        assertFailsWith<IllegalArgumentException> {
            SampleBuilder(
                SampleFixtures.source().copy(title = " "),
                SampleFixtures.DIFFICULTY,
                SampleFixtures.FORM_INDEX,
                emptyMap(),
            )
                .build()
        }
    }

    private fun mapFor(band: CefrLevelName): Map<String, String> = builder().build().colourMaps.getValue(band.name)

    private fun builder(difficulty: Map<String, String> = SampleFixtures.DIFFICULTY): SampleBuilder =
        SampleBuilder(SampleFixtures.source(), difficulty, SampleFixtures.FORM_INDEX, mapOf("fr" to entries()))

    private fun entries(): List<PackEntry> = SampleFixtures.entries()

    /** The six band names as the sample's wire format spells them. */
    private enum class CefrLevelName { A1, A2, B1, B2, C1, C2 }
}

/**
 * The committed fixture the sample transform is pinned against: one text, one
 * fixed **Difficulty table** and one small dictionary. The expected maps name the
 * six CEFR bands explicitly, so the golden and the per-band cases read the same
 * input.
 */
internal object SampleFixtures {

    fun source(): SampleSource = readSampleSource(
        requireNotNull(SampleFixtures::class.java.getResourceAsStream("/sample-en-source.json")) {
            "the committed sample fixture must be on the test classpath"
        }.use { stream -> stream.readBytes() },
    )

    fun content(): SampleContent = SampleBuilder(source(), DIFFICULTY, FORM_INDEX, mapOf("fr" to entries())).build()

    fun golden(): String = requireNotNull(SampleFixtures::class.java.getResourceAsStream("/golden/sample-en.json")) {
        "the committed sample golden must be on the test classpath"
    }.bufferedReader().use { it.readText() }.trimEnd('\n')

    val DIFFICULTY = mapOf(
        "a" to "A1",
        "the" to "A1",
        "near" to "A1",
        "calm" to "A2",
        "boat" to "B1",
        "wait" to "B2",
        "rock" to "C1",
    )

    val FORM_INDEX = mapOf("waited" to "wait", "rocks" to "rock")

    fun entries(): List<PackEntry> = listOf("a", "boat", "calm", "near", "rock", "the", "wait").map { lemma ->
        PackEntry(
            lemma = lemma,
            language = "en",
            partOfSpeech = "noun",
            senses = listOf(PackSense("The $lemma meaning.", listOf("fr-$lemma"))),
        )
    }
}
