package app.lekto.core.language

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The languages the app ships and the first-launch default (ADR-0034): English
 * and French, with the device's language chosen when the app ships it and English
 * otherwise.
 */
class AppLanguagesTest :
    FunSpec({

        test("the app ships English and French, the base first") {
            AppLanguages.SUPPORTED shouldContainExactly listOf("en", "fr")
        }

        test("only a shipped language is supported, by base tag") {
            AppLanguages.supports("en") shouldBe true
            AppLanguages.supports("fr-CA") shouldBe true
            AppLanguages.supports("es") shouldBe false
            AppLanguages.supports(null) shouldBe false
        }

        test("the device's language is the default when the app ships it") {
            defaultAppLanguage("fr") shouldBe "fr"
            defaultAppLanguage("fr-CA") shouldBe "fr"
        }

        test("the default is English when the app does not ship the device's language") {
            defaultAppLanguage("es") shouldBe "en"
            defaultAppLanguage("de-DE") shouldBe "en"
            defaultAppLanguage(null) shouldBe "en"
            defaultAppLanguage("") shouldBe "en"
        }
    })
