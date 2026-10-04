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
    })
