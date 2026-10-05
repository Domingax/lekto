package app.lekto.core.dictionary

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/**
 * The zero-configuration Translation shortcut (issue #87): Google Translate's
 * page for a phrase, built from the book's language and the pack's target
 * language. The URL is pure domain output, so it is pinned here; it is the whole
 * offer — an outbound link the browser opens — so no provider and no key is
 * needed.
 */
class TranslationShortcutTest :
    FunSpec({

        test("a phrase is percent-encoded with spaces as plus and the pair filled in") {
            translationShortcut("the lantern glows", "en", "fr").shouldNotBeNull().url shouldBe
                "https://translate.google.com/?sl=en&tl=fr&text=the+lantern+glows&op=translate"
        }

        test("the shortcut names Google Translate and opens an external HTTPS page") {
            val shortcut = translationShortcut("the lantern glows", "en", "fr").shouldNotBeNull()

            shortcut.source shouldBe DictionarySource.GOOGLE_TRANSLATE
            shortcut.source.label shouldBe "Google Translate"
            shortcut.url shouldStartWith "https://translate.google.com/"
        }

        test("an accented phrase is percent-encoded so the URL stays ASCII") {
            translationShortcut("le café est ouvert", "en", "fr").shouldNotBeNull().url shouldBe
                "https://translate.google.com/?sl=en&tl=fr&text=le+caf%C3%A9+est+ouvert&op=translate"
        }

        test("an unknown source language is auto-detected, so a phrase still gets a shortcut") {
            translationShortcut("the lantern glows", null, "fr").shouldNotBeNull().url shouldBe
                "https://translate.google.com/?sl=auto&tl=fr&text=the+lantern+glows&op=translate"
        }

        test("a blank phrase yields no shortcut") {
            translationShortcut("   ", "en", "fr").shouldBeNull()
        }

        test("with no target language there is no shortcut, because the service cannot address it") {
            translationShortcut("the lantern glows", "en", null).shouldBeNull()
        }
    })
