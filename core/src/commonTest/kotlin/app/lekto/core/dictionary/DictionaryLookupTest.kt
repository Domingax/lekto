package app.lekto.core.dictionary

import app.lekto.testkit.InMemoryDictionaryPack
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * The word query the reading loop makes (issue #18): it resolves an inflected
 * surface form to its lemma through the pack, returns definition, translation and
 * pronunciation, and degrades honestly when the word is unknown, the book's
 * language is unknown or the pack is absent — all with no network.
 */
class DictionaryLookupTest :
    FunSpec({

        fun lookup(pack: DictionaryPack? = InMemoryDictionaryPack()): DictionaryLookup = DictionaryLookup { pack }
        test("an inflected surface returns its lemma's definition, translation and pronunciation") {
            val result = lookup().lookup("Blorpled", "en")

            val found = result.shouldBeInstanceOf<WordLookup.Found>()
            found.lemma shouldBe "blorple"
            found.entries.flatMap { entry -> entry.senses.map { sense -> sense.definition } } shouldBe
                listOf("To move swiftly.", "To flow.", "A point scored.")
            found.entries.flatMap(DictionaryEntry::translations) shouldBe
                listOf("blorper", "fluxer", "blorpoint")
            found.entries.first().pronunciation shouldBe "/ˈblɔːpəl/"
        }

        test("a regional language tag collapses to the pack's base language") {
            lookup().lookup("blorple", "en-US") shouldBe
                WordLookup.Found("blorple", "en-US", "blorple", listOf(app.lekto.testkit.BLORPLE))
        }

        test("a word the pack does not know reports not-in-dictionary, not a failure") {
            lookup().lookup("zzzz", "en") shouldBe WordLookup.NotInDictionary("zzzz", "en")
        }

        test("a lookup with no pack installed degrades to unavailable") {
            lookup(pack = null).lookup("blorple", "en") shouldBe
                WordLookup.Unavailable(DictionaryLookup.NOT_INSTALLED)
        }

        test("a word with no language cannot be keyed and says so honestly") {
            lookup().lookup("blorple", null) shouldBe
                WordLookup.Unavailable(DictionaryLookup.UNKNOWN_LANGUAGE)
        }

        test("the pack is resolved by the book's reading language") {
            // One pack per Reading language (issue #149): the pack that translates
            // the book's own language is the one queried, not whichever was installed.
            val lookup = DictionaryLookup { code -> if (code == "en") InMemoryDictionaryPack() else null }

            lookup.lookup("blorple", "en-US").shouldBeInstanceOf<WordLookup.Found>()
            lookup.lookup("bonjour", "fr") shouldBe WordLookup.Unavailable(DictionaryLookup.NOT_INSTALLED)
        }

        // The first gloss is the rule both the translation a save keeps (issue #22)
        // and the gloss the **Vocabulary** list derives from the pack (issue #67;
        // ADR-0032) share.
        test("the first gloss is the first entry's first translation, not a spliced list") {
            val found = found(
                DictionarySense("To move swiftly.", listOf("blorper", "filtrer")),
                DictionarySense("To shimmer.", listOf("scintiller")),
            )

            found.firstGloss() shouldBe "blorper"
        }

        test("a lookup that found nothing has no first gloss") {
            WordLookup.NotInDictionary("zzzz", "en").firstGloss() shouldBe null
            WordLookup.Unavailable("offline").firstGloss() shouldBe null
        }

        test("a found entry with no translation has no first gloss rather than an empty string") {
            found(DictionarySense("A name.", emptyList())).firstGloss() shouldBe null
        }
    })

private fun found(vararg senses: DictionarySense): WordLookup.Found = WordLookup.Found(
    query = "blorpled",
    language = "en",
    lemma = "blorple",
    entries = listOf(DictionaryEntry("blorple", "en", "verb", null, null, senses.toList())),
)
