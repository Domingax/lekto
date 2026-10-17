package app.lekto.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionaryLookup
import app.lekto.core.dictionary.DictionarySense
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The gloss the **Vocabulary** list derives from the installed packs (issue #67;
 * ADR-0032): an entry the pack knows is glossed by the pack's first gloss, an
 * entry the pack cannot answer — not installed, unknown word, corrupt pack — keeps
 * no derived gloss so the list falls back to the entry's stored translation, and
 * each entry is queried in its own **Reading language**.
 */
class PackTranslationsTest {

    private val lantern = VocabularyEntry(
        key = WordKey("en", "lantern"),
        surface = "lantern",
        translation = "lanterne",
        mastery = MasteryLevel.LEARNED,
    )

    @Test
    fun `an entry the pack knows is glossed by the pack's first gloss`() {
        val glosses = packTranslations(listOf(lantern)) { _, _ -> found("lantern", "fanal", "falot") }

        assertEquals(mapOf(lantern.key to "fanal"), glosses)
    }

    @Test
    fun `an entry the pack does not know keeps no derived gloss`() {
        val glosses = packTranslations(listOf(lantern)) { _, _ ->
            WordLookup.NotInDictionary("lantern", "en")
        }

        assertEquals(emptyMap(), glosses)
    }

    @Test
    fun `an entry with no pack installed keeps no derived gloss`() {
        val glosses = packTranslations(listOf(lantern)) { _, _ ->
            WordLookup.Unavailable(DictionaryLookup.NOT_INSTALLED)
        }

        assertEquals(emptyMap(), glosses)
    }

    @Test
    fun `each entry is looked up in its own reading language and surface`() {
        val requests = mutableListOf<Pair<String?, String>>()
        val french = VocabularyEntry(WordKey("fr", "lanterne"), "lanterne")

        packTranslations(listOf(lantern, french)) { reading, surface ->
            requests += reading to surface
            WordLookup.NotInDictionary(surface, reading)
        }

        assertEquals(listOf<Pair<String?, String>>("en" to "lantern", "fr" to "lanterne"), requests.toList())
    }

    @Test
    fun `a blank gloss is no translation`() {
        val glosses = packTranslations(listOf(lantern)) { _, _ -> found("lantern", "   ") }

        assertEquals(emptyMap(), glosses)
    }

    private fun found(query: String, vararg translations: String): WordLookup.Found = WordLookup.Found(
        query = query,
        language = "en",
        lemma = query,
        entries = listOf(
            DictionaryEntry(
                lemma = query,
                language = "en",
                partOfSpeech = null,
                pronunciation = null,
                audioUrl = null,
                senses = listOf(DictionarySense("A definition.", translations.toList())),
            ),
        ),
    )
}
