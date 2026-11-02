package app.lekto.library

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.LibraryListing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A row's plate on a **simulated Android runtime** (issue #259): the same-named
 * twin of `app/desktopTest`'s `LibraryCoverSemanticsTest`, so the parity rule
 * (issue #73) sees both lanes. Its **Cover** replaces the **Reading language**'s
 * vignette when one decodes, and the plaque stays the fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
class LibraryCoverSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val book = Book(
        id = "b1",
        title = "The Lantern Keeper",
        language = "en",
        format = BookFormat.EPUB,
        fileName = "lantern.epub",
    )

    @Test
    fun aBookWithACoverShowsItInPlaceOfItsLanguagePlaque() {
        val covers = BookCoverLoader {
            Bitmap.createBitmap(COVER_WIDTH, COVER_HEIGHT, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        compose.setContent { Library(covers) }

        compose.waitUntil(timeoutMillis = COVER_TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(libraryCoverTag(book.id), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        compose.onNodeWithTag(libraryCoverTag(book.id), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("EN")
        compose.onNodeWithText("EN").assertDoesNotExist()
    }

    @Test
    fun aBookWithoutACoverKeepsItsLanguagePlaque() {
        compose.setContent { Library() }

        compose.onNodeWithText("EN").assertIsDisplayed()
        compose.onNodeWithTag(libraryCoverTag(book.id), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aCoverThatCannotBeDecodedFallsBackToThePlaque() {
        compose.setContent { Library(BookCoverLoader { null }) }

        compose.waitUntil {
            compose.onAllNodesWithTag(libraryRowTag(book.id)).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("EN").assertIsDisplayed()
        compose.onNodeWithTag(libraryCoverTag(book.id), useUnmergedTree = true).assertDoesNotExist()
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
        /** A stand-in cover's pixel size: enough for an Android bitmap to exist, no pixels asserted. */
        const val COVER_WIDTH = 4
        const val COVER_HEIGHT = 6

        /** The cover decodes on the real dispatcher, so the assertion waits for it. */
        const val COVER_TIMEOUT_MILLIS = 5_000L
    }
}
