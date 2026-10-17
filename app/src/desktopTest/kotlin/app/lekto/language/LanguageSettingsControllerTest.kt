package app.lekto.language

import app.lekto.core.language.LanguageSettings
import app.lekto.core.language.OfferedDictionary
import app.lekto.testkit.InMemoryLanguageSettingsStore
import app.lekto.testkit.testOfferedLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The live language settings (ADR-0034): the **app language** and the
 * **Translation language** are separate choices, each written through to the
 * device-local store; the app language takes the device's own language when the
 * app ships it, and the translation language takes it when a published pack
 * translates into it.
 */
class LanguageSettingsControllerTest {

    private val offered = listOf(
        testOfferedLanguage(
            "en",
            dictionaries = listOf(
                OfferedDictionary("fr", "lekto-dictionary-en-fr.sqlite.gz"),
                OfferedDictionary("es", "lekto-dictionary-en-es.sqlite.gz"),
            ),
        ),
    )

    @Test
    fun `an unset store starts in English and chooses no translation language`() {
        val controller = LanguageSettingsController(InMemoryLanguageSettingsStore())

        assertEquals("en", controller.appLanguage)
        assertNull(controller.translationLanguage)
    }

    @Test
    fun `a stored choice is shown at once`() {
        val controller = LanguageSettingsController(
            InMemoryLanguageSettingsStore(LanguageSettings(appLanguage = "fr", translationLanguage = "es")),
        )

        assertEquals("fr", controller.appLanguage)
        assertEquals("es", controller.translationLanguage)
    }

    @Test
    fun `the app language and the translation language are chosen independently`() {
        val store = InMemoryLanguageSettingsStore()
        val controller = LanguageSettingsController(store)

        controller.chooseAppLanguage("fr")
        controller.chooseTranslationLanguage("es")

        // The bug this fixes: an English interface may translate into French, and vice versa.
        assertEquals("fr", controller.appLanguage)
        assertEquals("es", controller.translationLanguage)
        assertEquals(LanguageSettings(appLanguage = "fr", translationLanguage = "es"), store.load())
    }

    @Test
    fun `a language the app does not ship is refused as the app language`() {
        val controller = LanguageSettingsController(InMemoryLanguageSettingsStore())

        controller.chooseAppLanguage("es")

        assertEquals("en", controller.appLanguage)
    }

    @Test
    fun `the app-language default is the device language when the app ships it, English otherwise`() {
        val french = LanguageSettingsController(InMemoryLanguageSettingsStore())
        val german = LanguageSettingsController(InMemoryLanguageSettingsStore())

        french.applyAppLanguageDefault("fr")
        german.applyAppLanguageDefault("de")

        assertEquals("fr", french.appLanguage)
        assertEquals("en", german.appLanguage)
    }

    @Test
    fun `the translation-language default is the device language when a pack translates into it`() {
        val controller = LanguageSettingsController(InMemoryLanguageSettingsStore())

        controller.applyTranslationLanguageDefault("fr", offered)

        assertEquals("fr", controller.translationLanguage)
    }

    @Test
    fun `the translation-language default is nothing when no published pack translates into the device language`() {
        val controller = LanguageSettingsController(InMemoryLanguageSettingsStore())

        controller.applyTranslationLanguageDefault("de", offered)

        assertNull(controller.translationLanguage)
    }

    @Test
    fun `a choice already made is never overwritten by a default`() {
        val controller = LanguageSettingsController(
            InMemoryLanguageSettingsStore(LanguageSettings(appLanguage = "fr", translationLanguage = "es")),
        )

        controller.applyAppLanguageDefault("en")
        controller.applyTranslationLanguageDefault("en", offered)

        assertEquals("fr", controller.appLanguage)
        assertEquals("es", controller.translationLanguage)
    }
}
