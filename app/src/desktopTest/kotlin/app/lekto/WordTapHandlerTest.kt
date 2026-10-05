package app.lekto

import app.lekto.core.dictionary.DictionaryLookup
import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.text.WordKey
import app.lekto.core.text.WordToken
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.dictionary.DictionaryController
import app.lekto.dictionary.DictionaryServices
import app.lekto.reader.WordTap
import app.lekto.testkit.FakeDictionaryPackFiles
import app.lekto.testkit.InMemoryVaultFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reader's word tap (issue #18): it resolves the token with the dictionary on
 * a background dispatcher, or reports the offline dictionary unavailable when no
 * services are wired, so the honest message still shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WordTapHandlerTest {

    private val tap = WordTap(WordToken("blorple", 0, 7, WordKey("en", "blorple")), "a blorple in context")

    @Test
    fun `without a dictionary it reports the lookup unavailable`() {
        var result: WordLookup? = null

        wordTapHandler(null, CoroutineScope(UnconfinedTestDispatcher()), { _, lookup -> result = lookup })(tap)

        assertEquals(WordLookup.Unavailable(DictionaryLookup.NOT_INSTALLED), result)
    }

    @Test
    fun `with an installed dictionary it resolves the word off the UI thread`() {
        val derived = DerivedAssetStore(InMemoryVaultFileSystem())
        val files = FakeDictionaryPackFiles(derived)
        val installer = DictionaryPackInstaller(derived, files, files, "https://example.test/pack.sqlite.gz")
        installer.install()
        val controller = DictionaryController(DictionaryServices(installer), UnconfinedTestDispatcher())
        var result: WordLookup? = null

        wordTapHandler(controller, CoroutineScope(UnconfinedTestDispatcher()), { _, lookup -> result = lookup })(tap)

        assertTrue(result is WordLookup.NotInDictionary, "the empty fake pack does not know the word: $result")
    }

    @Test
    fun `the handler carries the tapped word back with the result`() {
        var tapped: WordTap? = null

        wordTapHandler(null, CoroutineScope(UnconfinedTestDispatcher()), { word, _ -> tapped = word })(tap)

        assertEquals(tap, tapped)
    }
}
