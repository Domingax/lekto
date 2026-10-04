package app.lekto.core.dictionary

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/**
 * The reference shortcuts the lookup panel offers (issue #19): each source's
 * canonical page for a word, built from the book's language and the pack's
 * target language. The URLs are pure domain output, so they are pinned here; a
 * pair a source cannot address is omitted rather than handed a wrong page.
 */
class DictionarySourceTest :
    FunSpec({

        test("each source builds its canonical English-to-French page for the word") {
            DictionarySource.WORD_REFERENCE.canonicalUrl("lantern", "en", "fr") shouldBe
                "https://www.wordreference.com/enfr/lantern"
            DictionarySource.REVERSO.canonicalUrl("lantern", "en", "fr") shouldBe
                "https://context.reverso.net/translation/english-french/lantern"
            DictionarySource.LINGUEE.canonicalUrl("lantern", "en", "fr") shouldBe
                "https://www.linguee.com/english-french/search?source=auto&query=lantern"
            DictionarySource.GOOGLE_TRANSLATE.canonicalUrl("lantern", "en", "fr") shouldBe
                "https://translate.google.com/?sl=en&tl=fr&text=lantern&op=translate"
        }

        test("the whole panel offers one shortcut per source, in a stable order") {
            dictionaryShortcuts("lantern", "en", "fr").map { shortcut -> shortcut.source } shouldContainExactly
                DictionarySource.entries.toList()
        }

        test("a word with apostrophes and accents is percent-encoded so the URL stays ASCII") {
            DictionarySource.WORD_REFERENCE.canonicalUrl("café", "en", "fr") shouldBe
                "https://www.wordreference.com/enfr/caf%C3%A9"
        }

        test("a source that cannot address the language pair is omitted, not guessed") {
            // This build knows the English and French names Reverso and Linguee
            // need, but no German one; Google takes plain codes and still answers.
            DictionarySource.REVERSO.canonicalUrl("Laterne", "de", "fr").shouldBeNull()
            DictionarySource.LINGUEE.canonicalUrl("Laterne", "de", "fr").shouldBeNull()
            DictionarySource.GOOGLE_TRANSLATE.canonicalUrl("Laterne", "de", "fr").shouldNotBeNull()
        }

        test("an unknown source language still reaches Google Translate with auto-detect") {
            DictionarySource.GOOGLE_TRANSLATE.canonicalUrl("lantern", null, "fr") shouldBe
                "https://translate.google.com/?sl=auto&tl=fr&text=lantern&op=translate"
            dictionaryShortcuts("lantern", from = null, to = "fr")
                .map { shortcut -> shortcut.source } shouldContainExactly listOf(DictionarySource.GOOGLE_TRANSLATE)
        }

        test("with no target language no source can address the word, so none is offered") {
            DictionarySource.GOOGLE_TRANSLATE.canonicalUrl("lantern", "en", null).shouldBeNull()
            dictionaryShortcuts("lantern", "en", to = null).shouldBeEmpty()
        }

        test("a blank word yields no shortcut") {
            dictionaryShortcuts("   ", "en", "fr").shouldBeEmpty()
        }

        test("every built URL is HTTPS") {
            dictionaryShortcuts("lantern", "en", "fr").forEach { shortcut ->
                shortcut.url shouldStartWith "https://"
            }
        }
    })
