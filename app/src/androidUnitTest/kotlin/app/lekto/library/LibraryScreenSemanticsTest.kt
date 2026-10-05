package app.lekto.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The library screen on a **simulated Android runtime** (issue #74): the
 * same-named twin of `app/desktopTest`'s `LibraryScreenSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. The desktop lane proves
 * the empty state, the book list and its open tap, the inline import progress,
 * and the inline failure with a dismiss; this renders the same `LibraryScreen`
 * on the runtime Android uses, which the desktop lane cannot stand in for, with
 * the same-named tests so the two suites cannot drift apart unseen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class LibraryScreenSemanticsTest {

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
    fun showsTheEmptyStateUntilABookExists() {
        compose.setContent { Library(LibraryUiState()) }

        compose.onNodeWithText("No books yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun listsBooksAndOpensTheTappedOne() {
        var opened: Book? = null
        compose.setContent { Library(LibraryUiState(books = listOf(book)), onOpen = { opened = it }) }

        compose.onNodeWithText("The Lantern Keeper").assertIsDisplayed()
        compose.onNodeWithText("The Lantern Keeper").performClick()

        assertEquals(book, opened)
    }

    @Test
    fun showsProgressWhileImporting() {
        compose.setContent { Library(LibraryUiState(importing = true, progress = 0.4f)) }

        compose.onNodeWithText("Importing", substring = true).assertIsDisplayed()
    }

    @Test
    fun reportsAFailureAndDismissesIt() {
        var dismissed = false
        compose.setContent {
            Library(
                LibraryUiState(error = "Could not read 'broken.epub'."),
                onDismissError = { dismissed = true },
            )
        }

        compose.onNodeWithText("Could not read 'broken.epub'.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun theImportButtonIsDisabledWhileImporting() {
        compose.setContent { Library(LibraryUiState(importing = true)) }

        compose.onNodeWithText("Import").assertIsNotEnabled()
    }

    @Composable
    private fun Library(state: LibraryUiState, onOpen: (Book) -> Unit = {}, onDismissError: () -> Unit = {}) {
        MaterialTheme {
            LibraryScreen(
                state = state,
                actions = LibraryActions(
                    onOpen = onOpen,
                    onDismissError = onDismissError,
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
