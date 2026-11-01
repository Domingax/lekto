package app.lekto.text

import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.secret.SecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The per-language text tables (ADR-0034): every [TextKey] resolves in both
 * languages the app ships, a parameterised message fills its values, a domain
 * outcome reads in the chosen language, and a message survives storage so a kept
 * sync outcome still reads after a restart.
 */
class AppTextsTest {

    @Test
    fun `every key has an English and a French text`() {
        val english = appTextsFor("en")
        val french = appTextsFor("fr")

        TextKey.entries.forEach { key ->
            assertTrue(english.defines(key), "no English text for $key")
            assertTrue(french.defines(key), "no French text for $key")
        }
    }

    /**
     * The desktop's system dialog displays its own filter, so the app keeps no
     * filter text (issue #219): three titles, and no filter description that
     * would never be shown.
     */
    @Test
    fun `the platform file dialogs keep their titles and no filter text`() {
        val names = TextKey.entries.map { it.name }

        assertTrue("BOOKS_FILTER" !in names, "BOOKS_FILTER is dead text: the system dialog shows no filter")
        assertTrue("VAULT_FILTER" !in names, "VAULT_FILTER is dead text: the system dialog shows no filter")
        assertEquals("Import a book", appTextsFor("en")[TextKey.PICK_BOOK_TITLE])
        assertEquals("Importer un livre", appTextsFor("fr")[TextKey.PICK_BOOK_TITLE])
        assertEquals("Export vault", appTextsFor("en")[TextKey.EXPORT_VAULT_TITLE])
        assertEquals("Exporter le coffre", appTextsFor("fr")[TextKey.EXPORT_VAULT_TITLE])
        assertEquals("Import a vault", appTextsFor("en")[TextKey.IMPORT_VAULT_TITLE])
        assertEquals("Importer un coffre", appTextsFor("fr")[TextKey.IMPORT_VAULT_TITLE])
    }

    @Test
    fun `the two tables differ, so a language is a real choice`() {
        assertNotEquals(appTextsFor("en")[TextKey.LIBRARY], appTextsFor("fr")[TextKey.LIBRARY])
    }

    @Test
    fun `the base language is English for the app's tag, a subtag, an unknown tag and none`() {
        assertEquals(appTextsFor("en")[TextKey.SETTINGS], appTextsFor(null)[TextKey.SETTINGS])
        assertEquals(appTextsFor("en")[TextKey.SETTINGS], appTextsFor("de")[TextKey.SETTINGS])
        assertEquals(appTextsFor("fr")[TextKey.SETTINGS], appTextsFor("fr-CA")[TextKey.SETTINGS])
    }

    @Test
    fun `a parameterised message fills its values in each language`() {
        val progress = AppMessage.of(TextKey.READING_PROGRESS, 42)

        assertEquals("42%", appTextsFor("en").message(progress))
        assertEquals("42 %", appTextsFor("fr").message(progress))
    }

    @Test
    fun `a language is named in the app language`() {
        assertEquals("French", appTextsFor("en").languageName("fr"))
        assertEquals("français", appTextsFor("fr").languageName("fr"))
    }

    @Test
    fun `a known domain outcome reads in the app language`() {
        assertEquals(
            "Le fournisseur a refusé la clé API.",
            appTextsFor("fr").coreMessage(LlmConnectionResult.REJECTED_KEY),
        )
        assertEquals(
            "Le stockage sécurisé n'est pas disponible sur cet appareil.",
            appTextsFor("fr").coreMessage(SecretStore.UNAVAILABLE),
        )
    }

    @Test
    fun `an unknown domain text passes through unchanged`() {
        assertEquals("a transport failure", appTextsFor("fr").coreMessage("a transport failure"))
    }

    @Test
    fun `a message survives storage and reads in the new language`() {
        val report = AppMessage.SyncReportText(
            uploaded = 3,
            downloaded = 2,
            deletions = 1,
            attachments = 1,
            unresolved = 0,
            resynced = true,
        )

        val restored = AppMessage.decode(AppMessage.encode(report))

        assertEquals(report, restored)
        assertEquals(
            appTextsFor("en").message(report),
            appTextsFor("en").message(restored),
        )
        assertNotEquals(appTextsFor("en").message(restored), appTextsFor("fr").message(restored))
    }

    @Test
    fun `a stored form this build does not know reads back as its raw text`() {
        assertEquals(AppMessage.Core("Synced: uploaded 3."), AppMessage.decode("Synced: uploaded 3."))
    }
}
