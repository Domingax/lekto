package app.lekto.testkit

import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.sample.Sample
import app.lekto.core.sample.SampleCatalog
import app.lekto.core.sample.SampleGlossary
import app.lekto.core.sample.SampleGlossaryEntry
import app.lekto.core.sample.SampleSense
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A **Sample** shaped like the one a build ships, for tests that read the
 * catalogue or drive the first-launch guide (issue #297). Every value is
 * overridable so a test can break exactly the field it is about.
 */
@Suppress("LongParameterList") // One fixture mirroring a sample's fields, each independently overridable.
fun testSample(
    language: String = "en",
    title: String = "A Test Sample",
    author: String? = "Test Author",
    attribution: String? = "Public domain.",
    paragraphs: List<String> = listOf("A calm boat waited near the rocks."),
    difficulty: Map<String, CefrLevel?> = mapOf("boat" to null),
    forms: Map<String, String> = emptyMap(),
    colourMaps: Map<CefrLevel, Map<String, MasteryLevel>> = testColourMaps(),
    glossaries: Map<String, SampleGlossary> = mapOf("fr" to testSampleGlossary()),
): Sample = Sample(language, title, author, attribution, paragraphs, difficulty, forms, colourMaps, glossaries)

/** The six maps a sample always carries, all words known by default. */
fun testColourMaps(
    levels: Map<String, MasteryLevel> = mapOf("boat" to MasteryLevel.UNKNOWN),
): Map<CefrLevel, Map<String, MasteryLevel>> = CefrLevel.entries.associateWith { levels }

/** One translation language's meanings, covering [testSample]'s words. */
fun testSampleGlossary(): SampleGlossary = SampleGlossary(
    mapOf(
        "boat" to listOf(
            SampleGlossaryEntry(
                lemma = "boat",
                partOfSpeech = "noun",
                pronunciation = "/bəʊt/",
                senses = listOf(SampleSense("A small vessel for travelling on water.", listOf("bateau"))),
            ),
        ),
    ),
)

/**
 * A **Sample** shaped for the first-launch guide (issue #277): one paragraph in
 * which four words carry the four highlighted **Mastery level**s at every band,
 * each with a meaning in French, so a test can drive the coloured page's four
 * notes and the tap's real sheet from one fixture.
 */
fun guideSample(language: String = "en"): Sample {
    val levels = mapOf(
        "old" to MasteryLevel.RECOGNIZED,
        "fisherman" to MasteryLevel.UNKNOWN,
        "row" to MasteryLevel.LEARNED,
        "small" to MasteryLevel.KNOWN,
        "boat" to MasteryLevel.KNOWN,
        "across" to MasteryLevel.KNOWN,
        "calm" to MasteryLevel.FAMILIAR,
        "water" to MasteryLevel.KNOWN,
    )
    return testSample(
        language = language,
        title = "A Guide Sample",
        paragraphs = listOf("The old fisherman rowed his small boat across the calm water."),
        difficulty = mapOf(
            "old" to CefrLevel.A1,
            "fisherman" to CefrLevel.B2,
            "row" to CefrLevel.A2,
            "small" to CefrLevel.A1,
            "boat" to CefrLevel.A2,
            "across" to CefrLevel.A1,
            "calm" to CefrLevel.B1,
            "water" to CefrLevel.A1,
        ),
        forms = mapOf("rowed" to "row"),
        colourMaps = CefrLevel.entries.associateWith { levels },
        glossaries = mapOf("fr" to guideGlossary()),
    )
}

/** The French meanings the guide's fixture sample carries. */
fun guideGlossary(): SampleGlossary = SampleGlossary(
    mapOf(
        "old" to listOf(meaning("old", "adj", "/əʊld/", "Having lived for a long time.", "vieux")),
        "fisherman" to listOf(meaning("fisherman", "noun", "/ˈfɪʃəmən/", "A person who catches fish.", "pêcheur")),
        "row" to listOf(meaning("row", "verb", "/rəʊ/", "To propel a boat with oars.", "ramer")),
        "calm" to listOf(meaning("calm", "adj", "/kɑːm/", "Free from disturbance.", "calme")),
    ),
)

@Suppress("LongParameterList") // The fixture names each field of one meaning; a bundle would hide it.
private fun meaning(lemma: String, part: String, pronunciation: String, definition: String, translation: String) =
    SampleGlossaryEntry(lemma, part, pronunciation, listOf(SampleSense(definition, listOf(translation))))

/**
 * The producer-shaped bytes of a **Sample catalogue** carrying [samples].
 * [formatVersion] defaults to the handshake this build reads; a test passes a
 * different one to prove a catalogue this build cannot read is refused.
 */
fun testSampleCatalog(
    samples: List<Sample> = listOf(testSample()),
    formatVersion: Int = SampleCatalog.FORMAT_VERSION,
): ByteArray = buildJsonObject {
    put("formatVersion", JsonPrimitive(formatVersion))
    put("samples", JsonArray(samples.map(Sample::toJson)))
}.toString().encodeToByteArray()

private fun Sample.toJson(): JsonObject = buildJsonObject {
    put("language", language)
    put("title", title)
    author?.let { put("author", it) }
    attribution?.let { put("attribution", it) }
    put("paragraphs", JsonArray(paragraphs.map(::JsonPrimitive)))
    put(
        "difficulty",
        buildJsonObject {
            difficulty.forEach { (word, band) ->
                put(word, band?.name?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
            }
        },
    )
    put(
        "forms",
        buildJsonObject { forms.forEach { (surface, word) -> put(surface, word) } },
    )
    put(
        "colourMaps",
        buildJsonObject {
            colourMaps.forEach { (band, map) ->
                put(band.name, buildJsonObject { map.forEach { (word, level) -> put(word, level.name) } })
            }
        },
    )
    put(
        "glossaries",
        buildJsonObject {
            glossaries.forEach { (translation, glossary) ->
                put(translation, glossary.toJson())
            }
        },
    )
}

private fun SampleGlossary.toJson(): JsonObject = buildJsonObject {
    put(
        "entries",
        buildJsonObject {
            entries.forEach { (word, meanings) ->
                put(word, JsonArray(meanings.map(SampleGlossaryEntry::toJson)))
            }
        },
    )
}

private fun SampleGlossaryEntry.toJson(): JsonObject = buildJsonObject {
    put("lemma", lemma)
    partOfSpeech?.let { put("partOfSpeech", it) }
    pronunciation?.let { put("pronunciation", it) }
    put(
        "senses",
        buildJsonArray { senses.forEach { sense -> add(sense.toJson()) } },
    )
}

private fun SampleSense.toJson(): JsonObject = buildJsonObject {
    put("definition", definition)
    put("translations", JsonArray(translations.map(::JsonPrimitive)))
}
