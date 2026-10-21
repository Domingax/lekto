package app.lekto.navigation

import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.ReadingSession
import app.lekto.core.text.BlockKind
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The back stack's own behaviour (issue #185; docs/design.md §6). It is a value
 * with pure moves, so the whole history question is settled here, without a
 * composition: what the bar pushes, what it unwinds, what Back walks, and where
 * Back stops being the app's and becomes the platform's.
 */
@Suppress("MagicNumber") // The depths are the assertions themselves: one place per step back.
class BackStackTest {

    @Test
    fun opensOnTheLibraryWithNothingBehindIt() {
        assertEquals(Destination.Library, BackStack.Root.current)
        assertFalse(BackStack.Root.canGoBack, "the Library is the root: Back there belongs to the platform")
        assertEquals(1, BackStack.Root.depth)
    }

    @Test
    fun backAtTheRootChangesNothing() {
        assertEquals(BackStack.Root, BackStack.Root.back())
    }

    @Test
    fun theBarOpensEachSection() {
        ShellSection.entries.forEach { section ->
            val opened = BackStack.Root.select(section)

            assertEquals(section, opened.current.section, "$section is what its bar item opens")
        }
        assertTrue(BackStack.Root.select(ShellSection.SETTINGS).canGoBack)
    }

    @Test
    fun backFromSettingsReturnsToWhereItCameFrom() {
        val settings = BackStack.Root.select(ShellSection.VOCABULARY).select(ShellSection.SETTINGS)

        assertEquals(Destination.Settings, settings.current)
        assertEquals(Destination.Vocabulary, settings.back().current)
        assertEquals(Destination.Library, settings.back().back().current)
        assertFalse(settings.back().back().canGoBack, "back from the Library exits")
    }

    @Test
    fun tappingTheCurrentSectionChangesNothing() {
        val vocabulary = BackStack.Root.select(ShellSection.VOCABULARY)

        assertEquals(vocabulary, vocabulary.select(ShellSection.VOCABULARY))
    }

    @Test
    fun aSectionAlreadyBehindUnwindsToItRatherThanStackingASecondCopy() {
        val stack = BackStack.Root.select(ShellSection.VOCABULARY).select(ShellSection.SETTINGS)

        val unwound = stack.select(ShellSection.VOCABULARY)

        assertEquals(Destination.Vocabulary, unwound.current)
        assertEquals(listOf(Destination.Library), unwound.behind)
        assertEquals(2, unwound.depth, "the bar cannot build a loop")
    }

    @Test
    fun theReaderCoversTheShellAndBackReturnsToTheShelf() {
        val reading = BackStack.Root.push(Destination.Reading(session))

        assertNull(reading.current.section, "the reader has no section: it is full-screen over the shell")
        assertEquals(Destination.Library, reading.back().current)
        assertFalse(reading.back().canGoBack, "the shelf it was opened from still exits")
    }

    @Test
    fun aSubScreenWalksBackThroughTheSectionThatOfferedIt() {
        val stack = BackStack.Root
            .select(ShellSection.SETTINGS)
            .push(Destination.Languages)
            .push(Destination.Attribution)

        assertEquals(4, stack.depth)
        assertEquals(ShellSection.SETTINGS, stack.current.section, "a Settings entry keeps Settings current")
        assertEquals(Destination.Languages, stack.back().current)
        assertEquals(Destination.Settings, stack.back().back().current)
        assertEquals(Destination.Library, stack.back().back().back().current)
    }

    @Test
    fun pushingThePlaceAlreadyShownIsANoOp() {
        val stack = BackStack.Root.select(ShellSection.SETTINGS)

        assertSame(stack, stack.push(Destination.Settings))
    }

    @Test
    fun theSettingsLinkInTheReaderLeavesTheBookBehindIt() {
        // The phrase panel's settings link takes the reader out of the book, so the
        // book is not left on the stack: back would otherwise reopen a session
        // captured before the reader had turned a page.
        val reading = BackStack.Root.push(Destination.Reading(session))

        val settings = reading.replace(Destination.Settings)

        assertEquals(Destination.Settings, settings.current)
        assertEquals(listOf(Destination.Library), settings.behind)
        assertEquals(Destination.Library, settings.back().current)
    }

    @Test
    fun replacingAtTheRootStillLeavesTheLibraryBehindTheNewPlace() {
        val settings = BackStack.Root.replace(Destination.Settings)

        assertEquals(Destination.Settings, settings.current)
        assertEquals(Destination.Library, settings.back().current, "the root is never lost")
    }

    private val session = ReadingSession(
        book = Book(
            id = "b1",
            title = "The Lantern Keeper",
            language = "en",
            format = BookFormat.EPUB,
            fileName = "lantern.epub",
        ),
        text = StructuredText(
            title = "The Lantern Keeper",
            language = "en",
            blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("On the shelf.")))),
        ),
    )
}
