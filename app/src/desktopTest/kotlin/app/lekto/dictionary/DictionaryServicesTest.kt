package app.lekto.dictionary

import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.testkit.FakeDictionaryPackFiles
import app.lekto.testkit.InMemoryVaultFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The dictionary's device-local services (issues #18, #149; ADR-0034): every
 * query resolves its pack by the (Reading language × **Translation language**)
 * pair it is handed, so the reader's lookup and the tokeniser's lemma index both
 * follow the language the reader chose rather than a fixed one.
 */
class DictionaryServicesTest {

    private val derived = DerivedAssetStore(InMemoryVaultFileSystem())

    /** The pairs the installer factory was asked for, in order, so resolution is observable. */
    private val pairs = mutableListOf<Pair<String, String>>()

    private val services = DictionaryServices { reading, translation ->
        pairs += reading to translation
        val files = FakeDictionaryPackFiles(derived, reading, translation)
        DictionaryPackInstaller(derived, files, files, reading, translation)
    }

    @Test
    fun `the lemma index resolves the pack for the chosen translation language`() {
        services.lemmas("es").lemmaOf("lantern", "en")

        assertEquals(listOf("en" to "es"), pairs)
    }

    @Test
    fun `the lemma index has no pack when the translation language is unset`() {
        assertNull(services.lemmas(null).lemmaOf("lantern", "en"))
        assertTrue(pairs.isEmpty(), "no pack is resolved without a translation language")
    }

    @Test
    fun `the lemma index collapses both tags to their base pair`() {
        services.lemmas("es-419").lemmaOf("lantern", "en-US")

        assertEquals(listOf("en" to "es"), pairs)
    }

    @Test
    fun `the lookup resolves the pack for the chosen translation language`() {
        val lookup = services.lookup("en", "es")

        assertTrue(lookup.lookup("lantern", "en") is WordLookup.Unavailable, "no pack is installed yet")
        assertEquals(listOf("en" to "es"), pairs)
    }

    @Test
    fun `the lookup has no pack when the translation language is unset`() {
        assertTrue(services.lookup("en", null).lookup("lantern", "en") is WordLookup.Unavailable)
        assertTrue(pairs.isEmpty(), "no pack is resolved without a translation language")
    }

    @Test
    fun `the lookup collapses both tags to their base pair`() {
        services.lookup("en-US", "es-419")

        assertEquals(listOf("en" to "es"), pairs)
    }
}
