package app.lekto

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.language.LANGUAGES_RELOAD_TAG
import app.lekto.library.LIBRARY_TITLE_TAG
import app.lekto.navigation.SHELL_NAV_TAG
import app.lekto.navigation.ShellSection
import app.lekto.navigation.shellSectionTag
import app.lekto.reader.READER_BACK_TAG
import app.lekto.reader.READER_CHAPTER_SHEET_TAG
import app.lekto.reader.READER_END_PANEL_TAG
import app.lekto.reader.READER_PAGE_TAG
import app.lekto.reader.READER_POSITION_TAG
import app.lekto.settings.LANGUAGES_LINK_TAG
import app.lekto.settings.SETTINGS_TITLE_TAG
import app.lekto.testkit.inMemoryVocabulary
import app.lekto.vocabulary.VOCABULARY_LEVEL_SHEET_TAG
import app.lekto.vocabulary.VOCABULARY_TITLE_TAG
import app.lekto.vocabulary.vocabularyRowTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android's own back gesture, walking the app's back stack (issue #185;
 * docs/design.md §6). The defect this fixes is Android-only — back used to leave
 * the app from anywhere, from Settings, from the **Languages** surface, from the
 * reader — so it is proved here, on the platform runtime, through the activity's
 * `OnBackPressedDispatcher`: a host test is the fast lane that catches it
 * (docs/testing.md, "Android host lane").
 *
 * The semantics of the stack itself are `BackStackTest`'s; this file proves the
 * seam the stack is wired to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AndroidBackTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backFromTheLibraryLeavesTheApp() {
        compose.setContent { App(environment(InMemoryLibrary())) }

        pressBack()

        // The Library is the root: the app does not swallow Back there, so the
        // platform finishes the activity — "back from Library exits".
        assertTrue("the app leaves from its root", compose.activity.isFinishing)
    }

    @Test
    fun backFromSettingsReturnsToTheLibrary() {
        compose.setContent { App(environment(InMemoryLibrary())) }
        compose.onNodeWithTag(shellSectionTag(ShellSection.SETTINGS)).performClick()
        compose.onNodeWithTag(SETTINGS_TITLE_TAG).assertIsDisplayed()

        pressBack()

        compose.onNodeWithTag(LIBRARY_TITLE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(shellSectionTag(ShellSection.LIBRARY)).assertIsDisplayed()
        assertTrue("back does not leave the app from a section", !compose.activity.isFinishing)
    }

    @Test
    fun backFromSettingsReturnsToTheSectionItCameFrom() {
        // The defect the ticket names: back used to leave the app from Settings.
        // Here the reader came from Vocabulary, so back lands there.
        compose.setContent { App(environment(InMemoryLibrary())) }
        compose.onNodeWithTag(shellSectionTag(ShellSection.VOCABULARY)).performClick()
        compose.onNodeWithTag(SETTINGS_TITLE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(shellSectionTag(ShellSection.SETTINGS)).performClick()
        compose.onNodeWithTag(SETTINGS_TITLE_TAG).assertIsDisplayed()

        pressBack()

        compose.onNodeWithTag(VOCABULARY_TITLE_TAG).assertIsDisplayed()
        assertTrue("back does not leave the app from Settings", !compose.activity.isFinishing)
    }

    @Test
    fun backFromTheLanguagesSurfaceReturnsToSettings() {
        compose.setContent { App(environment(InMemoryLibrary())) }
        compose.onNodeWithTag(shellSectionTag(ShellSection.SETTINGS)).performClick()
        compose.onNodeWithTag(LANGUAGES_LINK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(LANGUAGES_RELOAD_TAG).assertIsDisplayed()

        pressBack()

        // The surface is an entry inside Settings, so back lands on the index it
        // was offered from, not on the Library (ADR-0034).
        compose.onNodeWithTag(SETTINGS_TITLE_TAG).assertIsDisplayed()
    }

    @Test
    fun backFromTheReaderReturnsToTheShelf() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }
        openBook()

        pressBack()

        compose.onNodeWithTag(LIBRARY_TITLE_TAG).assertIsDisplayed()
        compose.onNodeWithText("The Lantern Keeper").assertIsDisplayed()
        assertTrue("back does not leave the app from the reader", !compose.activity.isFinishing)
    }

    @Test
    fun backClosesTheLookupPanelBeforeLeavingTheReader() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }
        openBook()
        tapFirstWord()

        // The lookup resolves on the environment's dispatcher, so wait for the
        // panel rather than assuming it is composed when the tap returns. No pack
        // is wired, so the panel says so — that sentence is the panel.
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("isn't installed yet", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()

        pressBack()

        // The panel is open over the page: Back closes it, the reader keeps the
        // page it was on (issue #185).
        compose.onNodeWithText("isn't installed yet", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(READER_PAGE_TAG).assertIsDisplayed()
    }

    @Test
    fun backClosesTheChapterSheetBeforeTheReader() {
        val library = InMemoryLibrary(chapters = true).apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }
        openBook()
        compose.onNodeWithTag(READER_POSITION_TAG).performClick()
        compose.onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertIsDisplayed()

        pressBack()

        // The sheet is what is open over the reader, so Back closes it and the
        // page stays where it was; the reader is still in front of the shell.
        compose.onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertDoesNotExist()
        compose.onNodeWithTag(READER_PAGE_TAG).assertIsDisplayed()
        assertTrue("back does not leave the reader while it is open", !compose.activity.isFinishing)
    }

    @Test
    fun backClosesTheEndStateBeforeLeavingTheBook() {
        val library = InMemoryLibrary().apply {
            import("lantern.epub", byteArrayOf(1))
            readAt("b1", offset = Int.MAX_VALUE)
        }
        compose.setContent { App(environment(library)) }
        openBook()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput {
            click(Offset(visibleSize.width - 4f, center.y))
        }
        compose.onNodeWithTag(READER_END_PANEL_TAG).assertIsDisplayed()

        pressBack()

        // The end state closes onto the last page it was raised from, and the
        // reader stays in front of the shell (issue #200).
        compose.onNodeWithTag(READER_END_PANEL_TAG).assertDoesNotExist()
        compose.onNodeWithTag(READER_PAGE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()
        assertTrue("back does not leave the reader while it is open", !compose.activity.isFinishing)
    }

    @Test
    fun backClosesTheVocabularyLevelSheetBeforeTheSection() {
        // The level sheet is what is open over the list, so Back closes it before
        // the shell sees it, exactly as the reader's panels do (issue #178).
        val vocabulary = inMemoryVocabulary()
        val key = WordKey("en", "lantern")
        vocabulary.save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.RECOGNIZED))
        compose.setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }
        compose.onNodeWithTag(shellSectionTag(ShellSection.VOCABULARY)).performClick()
        compose.onNodeWithTag(vocabularyRowTag(key)).performClick()
        compose.onNodeWithTag(VOCABULARY_LEVEL_SHEET_TAG).assertIsDisplayed()

        pressBack()

        compose.onNodeWithTag(VOCABULARY_LEVEL_SHEET_TAG).assertDoesNotExist()
        compose.onNodeWithTag(VOCABULARY_TITLE_TAG).assertIsDisplayed()
        assertTrue("back does not leave the section while the sheet is open", !compose.activity.isFinishing)
    }

    @Test
    fun theReaderLeavesTheShellAndCoversTheBar() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }
        compose.onNodeWithTag(SHELL_NAV_TAG).assertIsDisplayed()

        openBook()

        compose.onNodeWithTag(SHELL_NAV_TAG).assertDoesNotExist()
        compose.onNodeWithTag(READER_BACK_TAG).performClick()
        compose.onNodeWithTag(SHELL_NAV_TAG).assertIsDisplayed()
    }

    /** Presses the activity's back gesture, the way the platform delivers it. */
    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Opens the one book every test imports and waits for its reader. */
    private fun openBook() {
        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithTag(READER_PAGE_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Taps the first word link on the page: word links carry no text, unlike the chrome. */
    private fun tapFirstWord() {
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodes(WORD_LINK)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onAllNodes(WORD_LINK)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
    }
}

/** Work on the injected dispatcher crosses a real thread; the Compose default is one second (issue #194). */
private const val AWAIT_DISPATCHED_WORK_MILLIS: Long = 10_000

/** A word link on the page: clickable, with neither text nor a description (the reader's back icon has one). */
private val WORD_LINK = hasClickAction() and
    SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
    SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)
