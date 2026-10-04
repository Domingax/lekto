package app.lekto

import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionarySense
import app.lekto.core.dictionary.WordLookup
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The translation a save keeps (issue #22): the French the offline dictionary
 * offered for the tapped word — the first gloss of the first entry, so a
 * polysemous word stores one coherent translation rather than a spliced list —
 * or nothing when the lookup found none, because the word itself is the user's.
 */
class VocabularySaveTest {

    @Test
    fun `the saved translation is the offline result's first gloss`() {
        val found = WordLookup.Found(
            query = "blorpled",
            language = "en",
            lemma = "blorple",
            entries = listOf(
                DictionaryEntry(
                    lemma = "blorple",
                    language = "en",
                    partOfSpeech = "verb",
                    pronunciation = null,
                    audioUrl = null,
                    senses = listOf(
                        DictionarySense("To move swiftly.", listOf("blorper", "filtrer")),
                        DictionarySense("To shimmer.", listOf("scintiller")),
                    ),
                ),
            ),
        )

        assertEquals("blorper", translationOf(found))
    }

    @Test
    fun `a lookup that found nothing saves no translation`() {
        assertEquals(null, translationOf(WordLookup.NotInDictionary("zzzz", "en")))
        assertEquals(null, translationOf(WordLookup.Unavailable("offline")))
    }

    @Test
    fun `a found entry with no translation saves none rather than an empty string`() {
        val found = WordLookup.Found(
            query = "nom",
            language = "fr",
            lemma = "nom",
            entries = listOf(
                DictionaryEntry("nom", "fr", null, null, null, listOf(DictionarySense("A name.", emptyList()))),
            ),
        )

        assertEquals(null, translationOf(found))
    }
}
