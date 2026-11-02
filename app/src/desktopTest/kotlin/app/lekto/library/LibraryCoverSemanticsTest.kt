package app.lekto.library

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.LibraryListing
import kotlin.test.Test

/**
 * A row's plate (issue #259; docs/design.md §7.1): its **Cover** when one decodes,
 * the **Reading language**'s vignette otherwise. The cover carries the language
 * the plaque used to show, so a screen reader still hears it.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryCoverSemanticsTest {

    private val book = Book(
        id = "b1",
        title = "The Lantern Keeper",
        language = "en",
        format = BookFormat.EPUB,
        fileName = "lantern.epub",
    )

    @Test
    fun aBookWithACoverShowsItInPlaceOfItsLanguagePlaque() = runComposeUiTest {
        val covers = BookCoverLoader { ImageBitmap(width = COVER_WIDTH, height = COVER_HEIGHT) }
        setContent { Library(covers) }

        waitUntil(timeoutMillis = COVER_TIMEOUT_MILLIS) {
            onAllNodesWithTag(libraryCoverTag(book.id), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(libraryCoverTag(book.id), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("EN")
        onNodeWithText("EN").assertDoesNotExist()
    }

    @Test
    fun aBookWithoutACoverKeepsItsLanguagePlaque() = runComposeUiTest {
        setContent { Library() }

        onNodeWithText("EN").assertIsDisplayed()
        onNodeWithTag(libraryCoverTag(book.id), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aCoverThatCannotBeDecodedFallsBackToThePlaque() = runComposeUiTest {
        setContent { Library(BookCoverLoader { null }) }

        waitUntil { onAllNodesWithTag(libraryRowTag(book.id)).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("EN").assertIsDisplayed()
        onNodeWithTag(libraryCoverTag(book.id), useUnmergedTree = true).assertDoesNotExist()
    }

    @Composable
    private fun Library(covers: BookCoverLoader = BookCoverLoader.None) {
        MaterialTheme {
            LibraryScreen(
                state = LibraryUiState(listing = LibraryListing(others = listOf(book))),
                covers = covers,
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }

    private companion object {
        /** A stand-in cover's pixel size: enough for an [ImageBitmap] to exist, no pixels asserted. */
        const val COVER_WIDTH = 4
        const val COVER_HEIGHT = 6

        /** The cover decodes on the real dispatcher, so the assertion waits for it. */
        const val COVER_TIMEOUT_MILLIS = 5_000L
    }
}
