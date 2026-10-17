package app.lekto.core.language

import app.lekto.testkit.testOfferedLanguage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The **Translation language** choices the **Language manifest** offers
 * (ADR-0034): only the languages a published pack translates into, and the
 * first-launch default a device's own language earns.
 */
class TranslationLanguageChoicesTest :
    FunSpec({

        test("the published translation languages are the distinct base tags of the packs, by name") {
            val offered = listOf(
                testOfferedLanguage(
                    "en",
                    dictionaries = listOf(
                        OfferedDictionary("fr", "en-fr"),
                        OfferedDictionary("es", "en-es"),
                    ),
                ),
                testOfferedLanguage("fr", dictionaries = listOf(OfferedDictionary("en", "fr-en"))),
            )

            publishedTranslationLanguages(offered) shouldContainExactly listOf("en", "fr", "es")
        }

        test("a manifest with no packs offers no translation language") {
            publishedTranslationLanguages(listOf(testOfferedLanguage("en"))) shouldBe emptyList()
        }

        test("the device's language is the default when a pack translates into it") {
            val offered = listOf(testOfferedLanguage("en", dictionaries = listOf(OfferedDictionary("fr", "en-fr"))))

            defaultTranslationLanguage("fr", offered) shouldBe "fr"
        }

        test("the device language is collapsed to its base tag before it is matched") {
            val offered = listOf(testOfferedLanguage("en", dictionaries = listOf(OfferedDictionary("es", "en-es"))))

            defaultTranslationLanguage("es-419", offered) shouldBe "es"
        }

        test("a device language no pack translates into defaults to nothing") {
            val offered = listOf(testOfferedLanguage("en", dictionaries = listOf(OfferedDictionary("fr", "en-fr"))))

            defaultTranslationLanguage("de", offered) shouldBe null
        }

        test("an unknown or absent device language defaults to nothing") {
            val offered = listOf(testOfferedLanguage("en", dictionaries = listOf(OfferedDictionary("fr", "en-fr"))))

            defaultTranslationLanguage(null, offered) shouldBe null
            defaultTranslationLanguage("", offered) shouldBe null
        }
    })
