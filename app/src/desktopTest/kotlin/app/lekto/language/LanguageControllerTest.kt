package app.lekto.language

import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.language.LanguageCatalogState
import app.lekto.testkit.InMemoryLanguageCatalog
import app.lekto.testkit.testOfferedLanguage
import app.lekto.text.AppMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The **Languages** state holder (issue #146): it reads the cached catalogue on
 * first entry, fetches only when there is none, orders the offered languages by
 * the reader's books, and reports an honest failure, all off the UI thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageControllerTest {

    private val catalog = InMemoryLanguageCatalog()

    @Test
    fun itPublishesTheCachedCatalogueWithoutFetching() = runTest {
        val seeded = InMemoryLanguageCatalog(LanguageCatalogState.Available(listOf(testOfferedLanguage("fr"))))
        val controller = LanguageController(
            catalog = seeded,
            books = { emptyList() },
            dispatcher = StandardTestDispatcher(testScheduler),
            scope = this,
        )

        controller.start()
        advanceUntilIdle()

        assertEquals(listOf("fr"), controller.state.value.languages.map { offered -> offered.language })
        assertEquals(0, seeded.reloads, "the cached catalogue must not be fetched again")
    }

    @Test
    fun itFetchesWhenNoCatalogueIsCached() = runTest {
        catalog.onReload = LanguageCatalogState.Available(listOf(testOfferedLanguage("en")))
        val controller = controller()

        controller.start()
        advanceUntilIdle()

        assertEquals(listOf("en"), controller.state.value.languages.map { offered -> offered.language })
        assertEquals(1, catalog.reloads)
    }

    @Test
    fun anOfflineFirstLaunchShowsTheHonestEmptyState() = runTest {
        catalog.failure = IOException("offline")
        val controller = controller()

        controller.start()
        advanceUntilIdle()

        assertTrue(controller.state.value.languages.isEmpty())
        assertEquals(false, controller.state.value.available)
        assertEquals(AppMessage.core("offline"), controller.state.value.error)
    }

    @Test
    fun anAvailableButEmptyCatalogueIsReportedAvailableSoTheSurfaceCanSaySo() = runTest {
        catalog.onReload = LanguageCatalogState.Available(emptyList())
        val controller = controller()

        controller.start()
        advanceUntilIdle()

        assertTrue(controller.state.value.languages.isEmpty())
        assertTrue(controller.state.value.available, "an empty but readable manifest is available, not a failure")
    }

    @Test
    fun reloadRefetchesTheList() = runTest {
        catalog.onReload = LanguageCatalogState.Available(listOf(testOfferedLanguage("en")))
        val controller = controller()
        controller.start()
        advanceUntilIdle()
        catalog.onReload = LanguageCatalogState.Available(listOf(testOfferedLanguage("fr")))

        controller.reload()
        advanceUntilIdle()

        assertEquals(listOf("fr"), controller.state.value.languages.map { offered -> offered.language })
        assertNull(controller.state.value.error)
    }

    @Test
    fun aReloadWhileOneIsInFlightIsCoalesced() = runTest {
        catalog.onReload = LanguageCatalogState.Available(listOf(testOfferedLanguage("en")))
        val controller = controller()

        controller.reload()
        controller.reload()
        advanceUntilIdle()

        assertEquals(1, catalog.reloads)
    }

    @Test
    fun itOrdersTheLanguagesByTheBooksTheLibraryHolds() = runTest {
        catalog.onReload = LanguageCatalogState.Available(
            listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"), testOfferedLanguage("de")),
        )
        val controller = controller(
            books = { listOf(book("fr"), book("fr"), book("en")) },
        )

        controller.start()
        advanceUntilIdle()

        // Two French books, one English, none German: French, English, then German.
        assertEquals(
            listOf("fr", "en", "de"),
            controller.state.value.languages.map { offered -> offered.language },
        )
    }

    private fun TestScope.controller(books: () -> List<Book> = { emptyList() }) = LanguageController(
        catalog = catalog,
        books = books,
        dispatcher = StandardTestDispatcher(testScheduler),
        scope = this,
    )

    private fun book(language: String): Book =
        Book(id = "b-$language", title = "A book", language = language, format = BookFormat.TXT, fileName = "b.txt")
}
