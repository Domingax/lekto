package app.lekto

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.BookProgress
import app.lekto.core.book.ImportProgress
import app.lekto.core.book.LibraryListing
import app.lekto.core.book.ReadingPosition
import app.lekto.core.book.ReadingProgress
import app.lekto.core.book.ReadingSession
import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.difficulty.DifficultyInstaller
import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.LanguageCatalogState
import app.lekto.core.language.LanguageSettings
import app.lekto.core.language.LanguageSettingsStore
import app.lekto.core.language.OfferedDictionary
import app.lekto.core.llm.LlmModelsResult
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.reading.ReadingPreferencesStore
import app.lekto.core.readinglevel.ReaderLevels
import app.lekto.core.secret.SecretStore
import app.lekto.core.speech.Pronouncer
import app.lekto.core.speech.SpeechResult
import app.lekto.core.sync.SyncEngine
import app.lekto.core.sync.SyncSettings
import app.lekto.core.text.BlockKind
import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.text.WordKey
import app.lekto.core.theme.AppTheme
import app.lekto.core.theme.AppThemeStore
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.core.vocabulary.VaultVocabulary
import app.lekto.core.vocabulary.Vocabulary
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.dictionary.DictionaryRelease
import app.lekto.dictionary.DictionaryServices
import app.lekto.dictionary.masteryTag
import app.lekto.difficulty.DifficultyRelease
import app.lekto.difficulty.DifficultyServices
import app.lekto.language.LANGUAGES_ATTRIBUTION_TAG
import app.lekto.language.LANGUAGES_RELOAD_TAG
import app.lekto.language.TRANSLATION_LANGUAGE_PICKER_TAG
import app.lekto.language.languageDifficultyDownloadTag
import app.lekto.language.languageLevelClearTag
import app.lekto.language.languageLevelOptionTag
import app.lekto.language.languageLevelPickerTag
import app.lekto.language.translationLanguageOptionTag
import app.lekto.library.LIBRARY_SEARCH_TAG
import app.lekto.library.LIBRARY_TITLE_TAG
import app.lekto.navigation.SHELL_NAV_TAG
import app.lekto.navigation.ShellSection
import app.lekto.navigation.shellSectionTag
import app.lekto.reader.READER_BACK_TAG
import app.lekto.reader.READER_PAGE_TAG
import app.lekto.reader.READING_LARGER_TAG
import app.lekto.reader.READING_PREFERENCES_CLOSE_TAG
import app.lekto.reader.READING_PREFERENCES_TAG
import app.lekto.settings.API_KEY_FIELD_TAG
import app.lekto.settings.APPEARANCE_LINK_TAG
import app.lekto.settings.APP_LANGUAGE_PICKER_TAG
import app.lekto.settings.LANGUAGES_LINK_TAG
import app.lekto.settings.MODEL_FIELD_TAG
import app.lekto.settings.PROVIDER_PICKER_TAG
import app.lekto.settings.ProviderController
import app.lekto.settings.SETTINGS_TITLE_TAG
import app.lekto.settings.SYNC_HOST_FIELD_TAG
import app.lekto.settings.SYNC_SAVE_TAG
import app.lekto.settings.SYNC_USERNAME_FIELD_TAG
import app.lekto.settings.SyncServices
import app.lekto.settings.TEST_CONNECTION_TAG
import app.lekto.settings.VaultTransfer
import app.lekto.settings.appLanguageOptionTag
import app.lekto.settings.appearanceOptionTag
import app.lekto.testkit.FakeDictionaryPackFiles
import app.lekto.testkit.FakeDifficultyFiles
import app.lekto.testkit.FakeLlmClient
import app.lekto.testkit.FakePronouncer
import app.lekto.testkit.InMemoryAppThemeStore
import app.lekto.testkit.InMemoryLanguageCatalog
import app.lekto.testkit.InMemoryLanguageSettingsStore
import app.lekto.testkit.InMemoryLlmSettingsStore
import app.lekto.testkit.InMemoryReaderLevels
import app.lekto.testkit.InMemoryReadingPreferencesStore
import app.lekto.testkit.InMemoryRemoteIndexStore
import app.lekto.testkit.InMemorySecretStore
import app.lekto.testkit.InMemorySyncOutcomeStore
import app.lekto.testkit.InMemorySyncSettingsStore
import app.lekto.testkit.InMemorySyncTarget
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.TestClock
import app.lekto.testkit.WhitespaceTextSegmenter
import app.lekto.testkit.deterministicSeams
import app.lekto.testkit.testOfferedLanguage
import app.lekto.translation.TRANSLATION_RESULT_TAG
import app.lekto.translation.TRANSLATION_SETTINGS_TAG
import app.lekto.translation.TRANSLATION_SHORTCUT_TAG
import app.lekto.vocabulary.VOCABULARY_SEARCH_TAG
import app.lekto.vocabulary.VOCABULARY_TITLE_TAG
import app.lekto.vocabulary.vocabularyDeleteTag
import app.lekto.vocabulary.vocabularyFilterTag
import app.lekto.vocabulary.vocabularyMasteryTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.time.Instant

/**
 * The application's reading-loop flow on a **simulated Android runtime** (issue
 * #79): the same-named twin of `app/desktopTest`'s `AppSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. It drives the whole app —
 * import → library → reader → resume, word lookup and save, settings and
 * attribution, the vocabulary list with its search, filter and delete — through
 * the same composable on the runtime Android uses, which the desktop lane cannot
 * stand in for.
 *
 * It is a deliberate mirror, not a shared body: the desktop lane drives
 * `runComposeUiTest` and this one a Robolectric `createAndroidComposeRule`, and
 * `app` has no test source set both lanes compile, so the two files are kept in
 * step by hand (issue #79). The book's text is deliberately made of one-line
 * paragraphs: Robolectric lays glyphs out far more tightly than a device font
 * does, and a page boundary then always falls between paragraphs, so the second
 * page starts with the same word the first does and the resume and lemma
 * assertions hold (cf. the reader twin, which lengthens its chapter instead).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
// One flow, one test per step; splitting the class hides the whole path.
@Suppress("TooManyFunctions", "LargeClass", "MagicNumber")
class AppSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun opensOnTheLibraryAndImportsABook() {
        val library = InMemoryLibrary()
        compose.setContent { App(environment(library, pickFile = { PickedFile("lantern.epub", byteArrayOf(1)) })) }

        // The first frame is the shelf in skeleton (docs/design.md §7.7); the
        // empty state lands once the local vault read returns (issue #208).
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("No books yet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Import a book").performClick()

        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("The Lantern Keeper").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("The Lantern Keeper").assertIsDisplayed()
    }

    @Test
    fun openingABookStartsAReadingSession() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.openBook()

        compose.awaitPage(1)
        compose.onNodeWithTag(READER_BACK_TAG).assertIsDisplayed()
    }

    @Test
    fun eachSectionIsReachedFromTheBar() {
        compose.setContent { App(environment(InMemoryLibrary())) }

        compose.tapSection(ShellSection.VOCABULARY)
        compose.onNodeWithTag(VOCABULARY_TITLE_TAG).assertIsDisplayed()

        compose.tapSection(ShellSection.SETTINGS)
        compose.onNodeWithTag(SETTINGS_TITLE_TAG).assertIsDisplayed()

        compose.tapSection(ShellSection.LIBRARY)
        compose.onNodeWithTag(LIBRARY_TITLE_TAG).assertIsDisplayed()
    }

    @Test
    fun theReaderCoversTheShell() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }
        compose.onNodeWithTag(SHELL_NAV_TAG).assertIsDisplayed()

        compose.openBook()

        // The reader leaves the shell and is full-screen over it (issue #185).
        compose.onNodeWithTag(SHELL_NAV_TAG).assertDoesNotExist()
        compose.leaveReader()
        compose.onNodeWithTag(SHELL_NAV_TAG).assertIsDisplayed()
    }

    @Test
    fun reopeningABookResumesWhereTheReaderLeftOff() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.openBook()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        compose.awaitPage(2)
        // Opening records the first page and the turn records the second,
        // both off the UI thread; wait for the second before leaving the book.
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { (library.savedOffset() ?: 0) > 0 }

        compose.leaveReader()
        compose.openBook()

        // The reopened reader resumes past page one: the position line names it.
        compose.awaitPage(2)
    }

    @Test
    fun aPageTurnedJustBeforeLeavingLeadsTheLibrary() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.onNodeWithText("Continue reading").assertDoesNotExist()
        compose.openBook()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        // Leave at once: the position write is still in flight, and the library
        // still has to re-read when it lands (issue #188).
        compose.leaveReader()

        // Only a refresh after the write can fill the section, so its
        // appearance proves the library re-read the vault (issue #188).
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("Continue reading").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Continue reading").assertIsDisplayed()
    }

    @Test
    fun leavingTheLibraryClearsTheSearch() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.onNodeWithTag(LIBRARY_SEARCH_TAG).performTextInput("zzz")
        compose.onNodeWithText("No books match \"zzz\".").assertIsDisplayed()

        // The query is view state, so it lives no longer than the screen (issue #173).
        compose.tapSection(ShellSection.VOCABULARY)
        compose.tapSection(ShellSection.LIBRARY)

        compose.onNodeWithText("No books match \"zzz\".").assertDoesNotExist()
        compose.onNodeWithText("The Lantern Keeper").assertIsDisplayed()
    }

    @Test
    fun leavingTheLibraryForSettingsOrLanguagesClearsTheSearch() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.typeSearch("zzz")
        compose.tapSection(ShellSection.SETTINGS)
        compose.tapSection(ShellSection.LIBRARY)
        compose.onNodeWithText("No books match \"zzz\".").assertDoesNotExist()

        compose.typeSearch("zzz")
        compose.openLanguages()
        compose.onNodeWithText("Back").performClick()
        compose.tapSection(ShellSection.LIBRARY)
        compose.onNodeWithText("No books match \"zzz\".").assertDoesNotExist()
    }

    @Test
    fun leavingTheLibraryForABookClearsTheSearch() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.typeSearch("lantern")
        compose.openBook()
        compose.leaveReader()

        compose.onNodeWithTag(LIBRARY_SEARCH_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
    }

    @Test
    fun theReadingPreferenceIsKeptAcrossSessions() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val preferences = InMemoryReadingPreferencesStore()
        compose.setContent { App(environment(library, readingPreferences = preferences)) }

        compose.openBook()
        // Opening a book runs on the environment's dispatcher, so wait for the
        // reader's chrome before touching its **Aa** control.
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithTag(READING_PREFERENCES_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        compose.onNodeWithTag(READING_LARGER_TAG).performClick()

        assertEquals(20f, preferences.load().size)

        // Leave the book and reopen it: the choice is device-local configuration,
        // not session state, so the reader starts from it (ADR-0035).
        compose.onNodeWithTag(READING_PREFERENCES_CLOSE_TAG).performClick()
        compose.leaveReader()
        compose.openBook()
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithTag(READING_PREFERENCES_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        compose.onNodeWithTag(READING_LARGER_TAG).performClick()

        assertEquals(22f, preferences.load().size)
    }

    @Test
    fun theAppThemeChoiceIsKeptAndMarkedAtOnce() {
        val library = InMemoryLibrary()
        val appTheme = InMemoryAppThemeStore()
        compose.setContent { App(environment(library, appTheme = appTheme), systemInDarkTheme = false) }

        compose.tapSection(ShellSection.SETTINGS)
        compose.onNodeWithTag(APPEARANCE_LINK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(appearanceOptionTag(AppTheme.DARK)).performClick()

        // The store is the truth and the screen is already repainted in the new
        // paper: choosing one writes through and marks it, with no toast (ADR-0036).
        assertEquals(AppTheme.DARK, appTheme.load())
        compose.onNodeWithTag(appearanceOptionTag(AppTheme.DARK)).assertIsSelected()
        compose.onNodeWithText("Current").assertIsDisplayed()
    }

    @Test
    fun tappingAWordOpensTheLookupPanelWithTheShortcuts() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, languageSettings = frenchTranslations())) }

        compose.openBook()
        compose.tapFirstWord()

        compose.onNodeWithText("Look it up online").assertIsDisplayed()
        // No pack is wired, so the panel degrades to the honest message and the
        // reference shortcuts (issue #19).
        compose.onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Reverso").assertIsDisplayed()
    }

    @Test
    fun aReferenceShortcutOpensTheCanonicalPageInTheBrowser() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        var opened: String? = null
        compose.setContent {
            App(
                environment(
                    library,
                    languageSettings = frenchTranslations(),
                    openUrl = { url -> opened = url },
                ),
            )
        }

        compose.openBook()
        compose.tapFirstWord()
        compose.onNodeWithText("Reverso").performClick()

        assertTrue(
            "the shortcut must open Reverso's canonical page: $opened",
            opened.orEmpty().startsWith("https://context.reverso.net/translation/english-french/"),
        )
    }

    @Test
    fun closingTheLookupPanelReturnsToTheSameReadingPosition() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, languageSettings = frenchTranslations())) }

        compose.openBook()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        compose.awaitPage(2)

        compose.tapFirstWord()
        compose.onNodeWithText("Look it up online").assertIsDisplayed()
        // The reader stays put behind the panel, at the same page. The panel is
        // bottom-aligned on Android and covers the page bar's position, so the
        // bar exists but is not visible; the page number is what matters.
        compose.onNodeWithText("Page 2 of", substring = true).assertExists()

        compose.onNodeWithText("Close").performClick()

        compose.onNodeWithText("Look it up online").assertDoesNotExist()
        compose.awaitPage(2)
    }

    @Test
    fun tappingOutsideTheWordLookupPanelClosesItWithoutTurningThePage() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, languageSettings = frenchTranslations())) }

        compose.openBook()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        compose.awaitPage(2)

        compose.tapFirstWord()
        compose.onNodeWithText("Look it up online").assertIsDisplayed()

        // A tap on the page, above the panel, is outside it: it closes the panel
        // and does not turn the page.
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(center.x, 4f)) }

        compose.onNodeWithText("Look it up online").assertDoesNotExist()
        compose.awaitPage(2)
    }

    @Test
    fun selectingAPhraseOffersTheTranslationShortcutWithNoProviderConfigured() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        var opened: String? = null
        compose.setContent {
            App(
                environment(
                    library,
                    languageSettings = frenchTranslations(),
                    openUrl = { url -> opened = url },
                ),
            )
        }

        compose.openBook()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        compose.awaitPage(2)

        // Long-press and drag across the page's words to select a phrase.
        compose.selectFirstPhrase()

        // No provider and no key are configured: the shortcut is the whole offer.
        compose.onNodeWithText("Translate online").assertIsDisplayed()
        compose.onNodeWithText("Page 2 of", substring = true).assertExists()
        compose.onNodeWithTag(TRANSLATION_SHORTCUT_TAG).performClick()
        assertTrue(
            "the shortcut must open the translation service's prefilled page: $opened",
            opened.orEmpty().startsWith("https://translate.google.com/"),
        )

        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Translate online").assertDoesNotExist()
        compose.awaitPage(2)
    }

    @Test
    fun tappingOutsideTheTranslationPanelClosesItWithoutLookingAWordUp() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, languageSettings = frenchTranslations())) }

        compose.openBook()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        compose.awaitPage(2)

        compose.selectFirstPhrase()
        compose.onNodeWithText("Translate online").assertIsDisplayed()

        // A tap on the page, above the panel, is outside it: it closes the panel
        // and does not look the word under it up, nor turn the page.
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(center.x, 4f)) }

        compose.onNodeWithText("Translate online").assertDoesNotExist()
        compose.onNodeWithText("Look it up online").assertDoesNotExist()
        compose.awaitPage(2)
    }

    @Test
    fun selectingAPhraseStreamsATranslationFromTheConnectedProvider() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val settings = InMemoryLlmSettingsStore(LlmProviderConfig(LlmProvider.ANTHROPIC, "claude-3"))
        val secrets = InMemorySecretStore().apply {
            put(ProviderController.providerSecretKey(LlmProvider.ANTHROPIC), "sk-live-123")
        }
        val client = FakeLlmClient(translation = listOf("La lanterne", " brille"))
        compose.setContent {
            App(
                environment(
                    library,
                    languageSettings = frenchTranslations(),
                    secrets = secrets,
                    llm = LlmServices(settings, client),
                ),
            )
        }

        compose.openBook()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        compose.awaitPage(2)

        compose.selectFirstPhrase()

        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { client.translated.isNotEmpty() }
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithTag(TRANSLATION_RESULT_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        // The selection and its containing sentence leave the device; the answer streams in.
        val user = client.translated.single().messages.single { message -> message.role == "user" }
        assertTrue("the request discloses the context: ${user.content}", user.content.contains("Selected phrase:"))
        compose.onNodeWithTag(TRANSLATION_RESULT_TAG).assertTextEquals("La lanterne brille")
        // The reader stays put behind the panel.
        compose.onNodeWithText("Page 2 of", substring = true).assertExists()
    }

    @Test
    fun selectingAPhraseWithoutAProviderLinksToSettings() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, dictionary = dictionaryServices())) }

        compose.openBook()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        compose.awaitPage(2)

        compose.selectFirstPhrase()

        compose.onNodeWithText("Connect an LLM provider", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(TRANSLATION_SETTINGS_TAG).performClick()

        // The non-blocking prompt lands on settings, leaving the book behind.
        compose.onNodeWithTag(SETTINGS_TITLE_TAG).assertIsDisplayed()
    }

    @Test
    fun listeningToAWordSpeaksItThroughThePlatform() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val pronouncer = FakePronouncer()
        compose.setContent { App(environment(library, pronouncer = pronouncer)) }

        compose.openBook()
        compose.tapFirstWord()
        compose.onNodeWithText("Listen").performClick()

        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { pronouncer.utterances.isNotEmpty() }
        val (text, language) = pronouncer.utterances.single()
        assertTrue("the tapped word must be spoken", text.isNotBlank())
        assertEquals("en", language)
    }

    @Test
    fun aLanguageWithNoVoiceSaysSoRatherThanFailing() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val pronouncer = FakePronouncer(SpeechResult.NoVoice("en"))
        compose.setContent { App(environment(library, pronouncer = pronouncer)) }

        compose.openBook()
        compose.tapFirstWord()
        compose.onNodeWithText("Listen").performClick()

        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { pronouncer.utterances.isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("No voice is installed", substring = true).assertIsDisplayed()
    }

    @Test
    fun speakingWithNoEngineWiredSaysSo() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.openBook()
        compose.tapFirstWord()
        compose.onNodeWithText("Listen").performClick()

        compose.onNodeWithText("No speech engine is available", substring = true).assertIsDisplayed()
    }

    @Test
    fun reachesTheLanguagesSurfaceAndItsAttributionFromTheLibrary() {
        compose.setContent {
            App(environment(InMemoryLibrary(), languages = dictionaryCatalog(), dictionary = dictionaryServices()))
        }

        compose.openLanguages()
        compose.onNodeWithText("English → French").assertIsDisplayed()

        compose.onNodeWithTag(LANGUAGES_ATTRIBUTION_TAG).performClick()

        compose.onNodeWithText("Dictionary data is derived from Wiktionary contributors", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun theLanguagesSurfaceShowsAnInstalledDictionaryAndItsAttribution() {
        val installed = dictionaryServices().also { services ->
            services.installer("en", "fr").install(DictionaryRelease.url(DictionaryRelease.artifact("en", "fr")))
        }
        compose.setContent {
            App(environment(InMemoryLibrary(), languages = dictionaryCatalog(), dictionary = installed))
        }

        compose.openLanguages()
        compose.onNodeWithText("is installed", substring = true).assertIsDisplayed()

        compose.onNodeWithTag(LANGUAGES_ATTRIBUTION_TAG).performClick()
        compose.onNodeWithText("Wiktionary contributors").assertIsDisplayed()
    }

    @Test
    fun connectingALanguageModelFromSettingsRunsAConnectionTest() {
        val settings = InMemoryLlmSettingsStore()
        val secrets = InMemorySecretStore()
        val client = FakeLlmClient()
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    secrets = secrets,
                    llm = LlmServices(settings, client),
                ),
            )
        }

        compose.tapSection(ShellSection.SETTINGS)
        compose.onNodeWithTag(PROVIDER_PICKER_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Anthropic").performClick()
        compose.onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performTextInput("claude-3")
        compose.onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        compose.onNodeWithTag(TEST_CONNECTION_TAG).performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { client.tested.isNotEmpty() }
        assertEquals("sk-live-123", client.tested.single().second)
        compose.onNodeWithText("The provider answered.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosingAModelFromTheProvidersListFillsTheModel() {
        val client = FakeLlmClient(models = LlmModelsResult.Loaded(listOf("claude-3", "claude-3-5")))
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    secrets = InMemorySecretStore(),
                    llm = LlmServices(InMemoryLlmSettingsStore(), client),
                ),
            )
        }

        compose.tapSection(ShellSection.SETTINGS)
        compose.onNodeWithTag(PROVIDER_PICKER_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Anthropic").performClick()

        // Choosing the provider loads its models with no separate action.
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { client.listed.isNotEmpty() }
        compose.onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        compose.onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performClick()
        compose.onNodeWithText("claude-3-5").performClick()

        compose.onNodeWithTag(MODEL_FIELD_TAG).performScrollTo()
        compose.onNodeWithText("claude-3-5").assertIsDisplayed()
    }

    @Test
    fun configuresSyncFromSettings() {
        val settings = InMemorySyncSettingsStore()
        val secrets = InMemorySecretStore()
        val sync = SyncServices(
            settings = settings,
            target = { _, _ -> InMemorySyncTarget() },
            engine = { target ->
                SyncEngine(
                    InMemoryVaultStore(),
                    InMemoryTombstoneStore(),
                    InMemoryRemoteIndexStore(),
                    target,
                    TestClock(),
                )
            },
            outcomes = InMemorySyncOutcomeStore(),
        )
        compose.setContent { App(environment(InMemoryLibrary(), secrets = secrets, sync = sync)) }

        compose.tapSection(ShellSection.SETTINGS)
        compose.onNodeWithTag(SYNC_HOST_FIELD_TAG).performScrollTo().performTextInput("https://cloud.example.test/dav")
        compose.onNodeWithTag(SYNC_USERNAME_FIELD_TAG).performScrollTo().performTextInput("reader")
        compose.onNodeWithTag(SYNC_SAVE_TAG).performScrollTo().performClick()

        assertEquals(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader"),
            settings.load(),
        )
    }

    @Test
    fun savingAWordFromThePanelStoresItWithItsContextAndLevel() {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, vocabulary = vocabulary)) }

        compose.openBook()
        compose.tapFirstWord()
        compose.onNodeWithText("Save").performClick()

        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vocabulary.all().isNotEmpty() }
        val entry = vocabulary.all().single()
        assertEquals("on", entry.key.key)
        assertTrue(
            "the context sentence is kept: ${entry.contextSentence}",
            entry.contextSentence.orEmpty().contains("On"),
        )
        // The word has no signal (no reader level, no difficulty), so the fresh
        // save defaults to the familiar level (ADR-0030).
        assertEquals(MasteryLevel.FAMILIAR, entry.mastery)
        // No toast, no dialog: the saved state on the panel is the confirmation.
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("Saved").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Saved").assertIsDisplayed()
    }

    @Test
    fun theMasteryLevelCanBeChangedAfterSaving() {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, vocabulary = vocabulary)) }

        compose.openBook()
        compose.tapFirstWord()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vocabulary.all().isNotEmpty() }

        compose.onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            vocabulary.all().single().mastery == MasteryLevel.LEARNED
        }
        assertEquals("changing the level must not add a second entry", 1, vocabulary.all().size)
    }

    @Test
    fun aWordSavedInAnEarlierSessionIsShownAsSaved() {
        val vault = InMemoryVaultStore()
        val vocabulary = VaultVocabulary(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a"))
        vocabulary.save(VocabularyEntry(WordKey("en", "on"), "On", mastery = MasteryLevel.LEARNED))
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, vocabulary = vocabulary)) }

        compose.openBook()
        compose.tapFirstWord()

        // The entry survived the restart: the panel opens already saved.
        compose.onNodeWithText("Saved").assertIsDisplayed()
        compose.onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savingAnInflectedFormDoesNotCreateASecondEntryForTheLemma() {
        val vocabulary = inMemoryVocabulary()
        // The pack resolves the page's first word to a lemma, so the two pages'
        // spellings collapse onto one key rather than adding a second entry.
        val lemmas = LemmaLookup { surface, _ -> if (surface == "On") "lantern" else null }
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, vocabulary = vocabulary, lemmas = lemmas)) }

        compose.openBook()
        compose.tapFirstWord()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vocabulary.all().isNotEmpty() }
        assertEquals("the save must key on the lemma", "lantern", vocabulary.all().single().key.key)

        // The word panel is open over the page bar; close it before turning the page.
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        compose.tapFirstWord()
        // The same lemma again, so its entry updates in place rather than adding a
        // second one.
        compose.onNodeWithText("Saved").assertIsDisplayed()
        compose.onNodeWithText("Save").assertDoesNotExist()
        assertEquals(1, vocabulary.all().size)
    }

    @Test
    fun theVocabularyListShowsSavedWordsWithTheirDetails() {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(
            VocabularyEntry(
                key = WordKey("en", "lantern"),
                surface = "lantern",
                translation = "lanterne",
                contextSentence = "The lantern burned all night.",
                mastery = MasteryLevel.LEARNED,
            ),
        )
        compose.setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        compose.tapSection(ShellSection.VOCABULARY)

        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("lanterne").assertIsDisplayed()
        compose.onNodeWithText("The lantern burned all night.").assertIsDisplayed()
        compose.onNodeWithTag(vocabularyMasteryTag(WordKey("en", "lantern"))).assertTextEquals("Learned")
    }

    @Test
    fun theVocabularyListDerivesItsTranslationFromTheInstalledPack() {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(
            VocabularyEntry(
                key = WordKey("en", "blorple"),
                surface = "blorpled",
                translation = "lanterne",
                mastery = MasteryLevel.LEARNED,
            ),
        )
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    dictionary = installedDictionary(),
                    vocabulary = vocabulary,
                    languageSettings = frenchTranslations(),
                ),
            )
        }

        compose.tapSection(ShellSection.VOCABULARY)
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("blorper").fetchSemanticsNodes().isNotEmpty()
        }

        // The pack's gloss for the lemma, not the frozen translation the entry kept.
        compose.onNodeWithText("blorper").assertIsDisplayed()
        compose.onNodeWithText("lanterne").assertDoesNotExist()
    }

    @Test
    fun theVocabularyListFallsBackToTheStoredTranslationWhenThePackDoesNotKnowTheWord() {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", translation = "lanterne"))
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    dictionary = installedDictionary(),
                    vocabulary = vocabulary,
                    languageSettings = frenchTranslations(),
                ),
            )
        }

        compose.tapSection(ShellSection.VOCABULARY)
        compose.waitForIdle()

        compose.onNodeWithText("lanterne").assertIsDisplayed()
    }

    @Test
    fun theVocabularyListFallsBackToTheStoredTranslationWithNoPackInstalled() {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "blorple"), "blorpled", translation = "lanterne"))
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    dictionary = dictionaryServices(),
                    vocabulary = vocabulary,
                    languageSettings = frenchTranslations(),
                ),
            )
        }

        compose.tapSection(ShellSection.VOCABULARY)
        compose.waitForIdle()

        compose.onNodeWithText("lanterne").assertIsDisplayed()
    }

    @Test
    fun theVocabularyListFallsBackToTheStoredTranslationWhenNoTranslationLanguageIsChosen() {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "blorple"), "blorpled", translation = "lanterne"))
        compose.setContent {
            App(environment(InMemoryLibrary(), dictionary = installedDictionary(), vocabulary = vocabulary))
        }

        compose.tapSection(ShellSection.VOCABULARY)
        compose.waitForIdle()

        compose.onNodeWithText("lanterne").assertIsDisplayed()
    }

    @Test
    fun theVocabularySearchesThePackDerivedTranslation() {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(
            VocabularyEntry(WordKey("en", "blorple"), "blorpled", translation = "lanterne"),
        )
        vocabulary.save(VocabularyEntry(WordKey("en", "harbour"), "harbour", translation = "port"))
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    dictionary = installedDictionary(),
                    vocabulary = vocabulary,
                    languageSettings = frenchTranslations(),
                ),
            )
        }

        compose.tapSection(ShellSection.VOCABULARY)
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("blorper").fetchSemanticsNodes().isNotEmpty()
        }

        // The row searches what it shows: the derived gloss, not the frozen one.
        compose.onNodeWithTag(VOCABULARY_SEARCH_TAG).performTextInput("blorper")

        compose.onNodeWithText("blorpled").assertIsDisplayed()
        compose.onNodeWithText("harbour").assertDoesNotExist()
    }

    @Test
    fun theVocabularyCanBeSearched() {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.LEARNED))
        vocabulary.save(VocabularyEntry(WordKey("en", "harbour"), "harbour", mastery = MasteryLevel.RECOGNIZED))
        compose.setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        compose.tapSection(ShellSection.VOCABULARY)
        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("harbour").assertIsDisplayed()

        compose.onNodeWithTag(VOCABULARY_SEARCH_TAG).performTextInput("har")

        compose.onNodeWithText("harbour").assertIsDisplayed()
        compose.onNodeWithText("lantern").assertDoesNotExist()
    }

    @Test
    fun theVocabularyCanBeFilteredByMastery() {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.LEARNED))
        vocabulary.save(VocabularyEntry(WordKey("en", "harbour"), "harbour", mastery = MasteryLevel.RECOGNIZED))
        compose.setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        compose.tapSection(ShellSection.VOCABULARY)
        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("harbour").assertIsDisplayed()

        compose.onNodeWithTag(vocabularyFilterTag(MasteryLevel.LEARNED)).performScrollTo().performClick()

        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("harbour").assertDoesNotExist()
    }

    @Test
    fun anEmptyVocabularyShowsTheInstructionalEmptyState() {
        compose.setContent { App(environment(InMemoryLibrary(), vocabulary = inMemoryVocabulary())) }

        compose.tapSection(ShellSection.VOCABULARY)

        compose.onNodeWithText("No words saved yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun deletingAWordDropsItFromTheReader() {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, vocabulary = vocabulary)) }

        // Save the first word from the panel, then find it in the list.
        compose.openBook()
        compose.tapFirstWord()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vocabulary.all().isNotEmpty() }
        val saved = vocabulary.all().single()

        compose.leaveReader()
        compose.tapSection(ShellSection.VOCABULARY)
        compose.onNodeWithTag(vocabularyDeleteTag(saved.key)).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vocabulary.all().isEmpty() }
        // The list recomposes after the write; wait for the empty state before asserting.
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("No words saved yet", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No words saved yet", substring = true).assertIsDisplayed()

        // Reopening the book, the word is unsaved again — the reader's colour
        // dropped with the entry, so list and text agree.
        compose.tapSection(ShellSection.LIBRARY)
        compose.openBook()
        compose.tapFirstWord()

        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Saved").assertDoesNotExist()
    }

    @Test
    fun reachesTheLanguagesSurfaceFromSettings() {
        compose.setContent { App(environment(InMemoryLibrary(), languages = languageCatalog())) }

        compose.openLanguages()

        compose.onNodeWithText("English").assertIsDisplayed()
    }

    @Test
    fun theAppLanguageOffersTheLanguagesTheAppShips() {
        compose.setContent { App(environment(InMemoryLibrary(), languageSettings = InMemoryLanguageSettingsStore())) }

        compose.tapSection(ShellSection.SETTINGS)
        compose.onNodeWithTag(APP_LANGUAGE_PICKER_TAG).performScrollTo().performClick()

        compose.onNodeWithTag(appLanguageOptionTag("en")).assertIsDisplayed()
        compose.onNodeWithTag(appLanguageOptionTag("fr")).assertIsDisplayed()
        // A language the app does not ship is never offered.
        compose.onNodeWithTag(appLanguageOptionTag("es")).assertDoesNotExist()
    }

    @Test
    fun choosingAnAppLanguageInSettingsStoresIt() {
        val settings = InMemoryLanguageSettingsStore()
        compose.setContent { App(environment(InMemoryLibrary(), languageSettings = settings)) }

        compose.tapSection(ShellSection.SETTINGS)
        compose.onNodeWithTag(APP_LANGUAGE_PICKER_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(appLanguageOptionTag("fr")).performClick()

        assertEquals("fr", settings.load().appLanguage)
        // Settings shows the choice without a relaunch — and the whole interface
        // follows it into French (ADR-0034).
        compose.onNodeWithText("français").assertIsDisplayed()
    }

    @Test
    fun theFirstLaunchDefaultsTheAppLanguageToTheDeviceLanguage() {
        val settings = InMemoryLanguageSettingsStore()
        compose.setContent {
            App(environment(InMemoryLibrary(), languageSettings = settings, systemLanguage = "fr"))
        }

        // The default lands with no question and no surface opened.
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { settings.load().appLanguage != null }
        assertEquals("fr", settings.load().appLanguage)
    }

    @Test
    fun theFirstLaunchDefaultsTheAppLanguageToEnglishWhenTheAppDoesNotShipTheDeviceLanguage() {
        val settings = InMemoryLanguageSettingsStore()
        compose.setContent {
            App(environment(InMemoryLibrary(), languageSettings = settings, systemLanguage = "de"))
        }

        compose.waitForIdle()

        assertEquals("en", settings.load().appLanguage)
    }

    @Test
    fun theLanguagesSurfaceOffersOnlyThePublishedTranslationLanguages() {
        compose.setContent { App(environment(InMemoryLibrary(), languages = translationCatalog())) }

        compose.openLanguages()
        compose.onNodeWithTag(TRANSLATION_LANGUAGE_PICKER_TAG).performClick()

        compose.onNodeWithTag(translationLanguageOptionTag("fr")).assertIsDisplayed()
        compose.onNodeWithTag(translationLanguageOptionTag("es")).assertIsDisplayed()
        // English is a Reading language, not one the packs translate into.
        compose.onNodeWithTag(translationLanguageOptionTag("en")).assertDoesNotExist()
    }

    @Test
    fun choosingATranslationLanguageOnTheLanguagesSurfaceStoresIt() {
        val settings = InMemoryLanguageSettingsStore()
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = translationCatalog(),
                    languageSettings = settings,
                ),
            )
        }

        compose.openLanguages()
        compose.onNodeWithTag(TRANSLATION_LANGUAGE_PICKER_TAG).performClick()
        compose.onNodeWithTag(translationLanguageOptionTag("es")).performClick()

        assertEquals("es", settings.load().translationLanguage)
        // The surface shows the choice without a relaunch.
        compose.onNodeWithText("Spanish").assertIsDisplayed()
    }

    @Test
    fun theFirstLaunchDefaultsTheTranslationLanguageToTheDeviceLanguage() {
        val settings = InMemoryLanguageSettingsStore()
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = translationCatalog(),
                    languageSettings = settings,
                    systemLanguage = "fr",
                ),
            )
        }

        compose.openLanguages()

        // The default lands once the manifest is read; no question is asked.
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { settings.load().translationLanguage != null }
        assertEquals("fr", settings.load().translationLanguage)
    }

    @Test
    fun anEnglishDeviceKeepsEnglishForTheInterfaceButTheReaderChoosesTheTranslationLanguage() {
        // The bug this fixes: an English interface must not force English translations.
        val settings = InMemoryLanguageSettingsStore()
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = translationCatalog(),
                    languageSettings = settings,
                    systemLanguage = "en",
                ),
            )
        }

        compose.openLanguages()
        compose.onNodeWithTag(TRANSLATION_LANGUAGE_PICKER_TAG).performClick()
        compose.onNodeWithTag(translationLanguageOptionTag("fr")).performClick()

        assertEquals("en", settings.load().appLanguage)
        assertEquals("fr", settings.load().translationLanguage)
    }

    @Test
    fun reloadingTheLanguageListRefetchesItWhenNoneIsCached() {
        // The first open finds no cache and the fetch fails: the honest empty state.
        val catalog = InMemoryLanguageCatalog().apply { failure = IOException("offline") }
        compose.setContent { App(environment(InMemoryLibrary(), languages = catalog)) }

        compose.openLanguages()
        compose.onNodeWithText("hasn't loaded the list of languages", substring = true).assertIsDisplayed()

        catalog.failure = null
        catalog.onReload = LanguageCatalogState.Available(listOf(testOfferedLanguage("en")))
        compose.onNodeWithTag(LANGUAGES_RELOAD_TAG).performClick()

        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("English").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("English").assertIsDisplayed()
    }

    @Test
    fun settingAReaderLevelOnTheLanguagesSurfaceStoresIt() {
        val levels = InMemoryReaderLevels()
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = languageCatalog(),
                    vocabulary = inMemoryVocabulary(),
                    readerLevels = levels,
                ),
            )
        }

        compose.openLanguages()
        compose.onNodeWithTag(languageLevelPickerTag("en")).performClick()
        compose.onNodeWithTag(languageLevelOptionTag("en", CefrLevel.B1)).performClick()

        assertEquals(CefrLevel.B1, levels.levelFor("en"))
    }

    @Test
    fun clearingAReaderLevelOnTheLanguagesSurfaceDeletesIt() {
        val levels = InMemoryReaderLevels(mapOf("en" to CefrLevel.B2))
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = languageCatalog(),
                    vocabulary = inMemoryVocabulary(),
                    readerLevels = levels,
                ),
            )
        }

        compose.openLanguages()
        compose.onNodeWithText("B2").assertIsDisplayed()
        compose.onNodeWithTag(languageLevelPickerTag("en")).performClick()
        compose.onNodeWithTag(languageLevelClearTag("en")).performClick()

        assertNull(levels.levelFor("en"))
    }

    @Test
    fun downloadingDifficultyOnTheLanguagesSurfaceMarksItInstalled() {
        compose.setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = difficultyCatalog(),
                    vocabulary = inMemoryVocabulary(),
                    readerLevels = InMemoryReaderLevels(),
                    difficulty = difficultyServices(),
                ),
            )
        }

        compose.openLanguages()
        // The manifest publishes a table for English, so the surface offers its download.
        compose.onNodeWithTag(languageDifficultyDownloadTag("en")).performClick()

        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("Download again").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("is installed", substring = true).assertIsDisplayed()
    }

    /** Taps one of the shell's three sections: the bar on a phone, the column on desktop (issue #185). */
    private fun ComposeTestRule.tapSection(section: ShellSection) {
        onNodeWithTag(shellSectionTag(section)).performClick()
    }

    /** Leaves the reader for the shelf through its own back control, an icon in its band (issue #185). */
    private fun ComposeTestRule.leaveReader() {
        onNodeWithTag(READER_BACK_TAG).performClick()
    }

    /**
     * Reaches the **Languages** surface: an entry inside Settings now that the
     * shell owns the sections, so the way in is Settings and its doorway
     * (ADR-0034, issue #185).
     */
    private fun ComposeTestRule.openLanguages() {
        tapSection(ShellSection.SETTINGS)
        onNodeWithTag(LANGUAGES_LINK_TAG).performScrollTo().performClick()
    }
}

/** An **in-memory catalog** offering English, so the Languages surface lists something. */
private fun languageCatalog(): LanguageCatalog =
    InMemoryLanguageCatalog(LanguageCatalogState.Available(listOf(testOfferedLanguage("en"))))

/** A catalog whose English entry publishes a **Difficulty table**, so its download is offered. */
private fun difficultyCatalog(): LanguageCatalog = InMemoryLanguageCatalog(
    LanguageCatalogState.Available(
        listOf(testOfferedLanguage("en", difficulty = DifficultyRelease.artifact("en"))),
    ),
)

/** A catalog whose English entry publishes an English → French **Dictionary pack**, so its download is offered. */
private fun dictionaryCatalog(): LanguageCatalog = InMemoryLanguageCatalog(
    LanguageCatalogState.Available(
        listOf(
            testOfferedLanguage(
                "en",
                dictionaries = listOf(
                    OfferedDictionary("fr", DictionaryRelease.artifact("en", "fr")),
                ),
            ),
        ),
    ),
)

/** A catalog whose English entry publishes French and Spanish packs, so both are offered as translation languages. */
private fun translationCatalog(): LanguageCatalog = InMemoryLanguageCatalog(
    LanguageCatalogState.Available(
        listOf(
            testOfferedLanguage(
                "en",
                dictionaries = listOf(
                    OfferedDictionary("fr", DictionaryRelease.artifact("en", "fr")),
                    OfferedDictionary("es", DictionaryRelease.artifact("en", "es")),
                ),
            ),
        ),
    ),
)

/** Language settings with French translations, as a reader who reads English books and wants French. */
private fun frenchTranslations(): LanguageSettingsStore =
    InMemoryLanguageSettingsStore(LanguageSettings(translationLanguage = "fr"))

/** A **Difficulty table** bundle over the in-memory fakes, so a download needs no network. */
private fun difficultyServices(): DifficultyServices {
    val derived = DerivedAssetStore(InMemoryVaultFileSystem())
    val files = FakeDifficultyFiles(derived)
    return DifficultyServices { language ->
        DifficultyInstaller(
            derived,
            files,
            files,
            language,
        )
    }
}

@Suppress("LongParameterList") // The environment's inputs are independent; a bundle would only hide that.
internal fun environment(
    library: BookLibrary,
    pickFile: (suspend () -> PickedFile?)? = null,
    dictionary: DictionaryServices? = null,
    languages: LanguageCatalog? = null,
    difficulty: DifficultyServices? = null,
    pronouncer: Pronouncer? = null,
    openUrl: (String) -> Unit = {},
    vaultTransfer: VaultTransfer? = null,
    vocabulary: Vocabulary? = null,
    readerLevels: ReaderLevels? = null,
    languageSettings: LanguageSettingsStore? = null,
    systemLanguage: String? = null,
    readingPreferences: ReadingPreferencesStore? = null,
    appTheme: AppThemeStore? = null,
    lemmas: LemmaLookup = LemmaLookup.None,
    secrets: SecretStore? = null,
    llm: LlmServices? = null,
    sync: SyncServices? = null,
) = AppEnvironment(
    segmenter = WhitespaceTextSegmenter(),
    library = library,
    mastery = MasteryLookup.AllKnown,
    lemmas = lemmas,
    vocabulary = vocabulary,
    readerLevels = readerLevels,
    languageSettings = languageSettings,
    systemLanguage = systemLanguage,
    readingPreferences = readingPreferences,
    appTheme = appTheme,
    pickFile = pickFile,
    dictionary = dictionary,
    languages = languages,
    difficulty = difficulty,
    pronouncer = pronouncer,
    openUrl = openUrl,
    vaultTransfer = vaultTransfer,
    secrets = secrets,
    llm = llm,
    sync = sync,
)

/** A vocabulary over an in-memory vault, so a test can inspect and restart it. */
private fun inMemoryVocabulary(): Vocabulary =
    VaultVocabulary(InMemoryVaultStore(), InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a"))

/**
 * Opens the one book every test imports and waits for its reader.
 *
 * Opening runs on the injected dispatcher, so on a loaded machine the reader is
 * not on screen when the click returns; a test that drove it at once would fail
 * on timing rather than behaviour (issue #194).
 */
private fun ComposeTestRule.openBook() {
    onNodeWithText("The Lantern Keeper").performClick()
    waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
        onAllNodesWithTag(READER_PAGE_TAG).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Types [query] into the Library's search field, which the screen owns as view state. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeTestRule.typeSearch(query: String) {
    onNodeWithTag(LIBRARY_SEARCH_TAG).performTextInput(query)
}

/** Taps the first word link on the page: word links carry no text, unlike the chrome buttons. */
private fun ComposeTestRule.tapFirstWord() {
    // The word layer arrives a frame after the reader opens (the page is
    // paginated and tokenised asynchronously), so wait for it before tapping.
    waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
        onAllNodes(WORD_LINK)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    onAllNodes(WORD_LINK)
        .onFirst()
        .performSemanticsAction(SemanticsActions.OnClick)
}

/** Waits until the reader's page bar reports page [page], then asserts it is there. */
private fun ComposeTestRule.awaitPage(page: Int) {
    waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
        onAllNodesWithText("Page $page of", substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Page $page of", substring = true).assertExists()
}

/** Long-presses the first word link and drags across the line, selecting a phrase. */
@Suppress("MagicNumber") // The long-press delay and the drag distance are the test's parameters.
private fun ComposeTestRule.selectFirstPhrase() {
    waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
        onAllNodes(WORD_LINK)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    val page = onNodeWithTag(READER_PAGE_TAG).fetchSemanticsNode().boundsInRoot
    val word = onAllNodes(WORD_LINK)
        .onFirst()
        .fetchSemanticsNode()
        .boundsInRoot
    val start = word.center - page.topLeft
    onNodeWithTag(READER_PAGE_TAG).performTouchInput {
        down(start)
        advanceEventTime(1000)
        moveTo(Offset(start.x + 80f, start.y))
        moveTo(Offset(start.x + 160f, start.y))
        up()
    }
}

private fun dictionaryServices(): DictionaryServices {
    val derived = DerivedAssetStore(InMemoryVaultFileSystem())
    return DictionaryServices { reading, translation ->
        val files = FakeDictionaryPackFiles(derived, reading, translation)
        DictionaryPackInstaller(derived, files, files, reading, translation)
    }
}

/** The dictionary services with the en→fr pack installed, so a lookup answers the fixture words. */
private fun installedDictionary(): DictionaryServices = dictionaryServices().also { services ->
    services.installer("en", "fr").install(DictionaryRelease.url(DictionaryRelease.artifact("en", "fr")))
}

/** Enough one-line paragraphs to span several pages at any test viewport. */
private const val PARAGRAPHS = 40

/**
 * The budget for a wait that follows work on the app's injected dispatcher.
 * That work crosses a real thread, so the wait has to outlast a busy machine
 * rather than the one-second Compose default (issue #194).
 */
private const val AWAIT_DISPATCHED_WORK_MILLIS: Long = 10_000

/**
 * A [BookLibrary] held in memory: import, open and reading progress without a
 * vault. Shared with `AppWindowInsetsSemanticsTest`, which needs a book to open
 * without standing a vault up.
 */
internal class InMemoryLibrary : BookLibrary {

    private var stored: List<Book> = emptyList()
    private val positions = mutableMapOf<String, Int>()
    private val lastRead = mutableMapOf<String, Long>()
    private var reads = 0L

    @Synchronized override fun books(): List<Book> = stored

    @Synchronized override fun listing(): LibraryListing = LibraryListing.of(
        books = stored,
        lastReadByBook = lastRead.mapValues { (_, order) -> Instant.fromEpochMilliseconds(order) },
        progressByBook = stored.mapNotNull { book ->
            positions[book.id]?.let { offset ->
                book.id to BookProgress.forPosition(
                    ReadingPosition(book.id, offset),
                    longText(book).renderedLength(),
                )
            }
        }.toMap(),
    )

    @Synchronized override fun measure(bookId: String): ReadingProgress? {
        val book = stored.firstOrNull { it.id == bookId }
        val offset = positions[bookId]
        return if (book != null && offset != null) ReadingProgress(offset, longText(book).renderedLength()) else null
    }

    @Synchronized override fun import(fileName: String, bytes: ByteArray, onProgress: (ImportProgress) -> Unit): Book {
        val book = Book(
            id = "b${stored.size + 1}",
            title = "The Lantern Keeper",
            language = "en",
            format = BookFormat.ofFileName(fileName) ?: BookFormat.TXT,
            fileName = fileName,
        )
        stored = stored + book
        return book
    }

    @Synchronized override fun open(id: String): ReadingSession? {
        val book = stored.firstOrNull { it.id == id } ?: return null
        return ReadingSession(
            book = book,
            text = longText(book),
            position = positions[book.id]?.let { offset -> ReadingPosition(book.id, offset) },
        )
    }

    @Synchronized override fun delete(id: String) {
        stored = stored.filterNot { book -> book.id == id }
        positions.remove(id)
        lastRead.remove(id)
    }

    @Synchronized
    override fun position(bookId: String): ReadingPosition? =
        positions[bookId]?.let { offset -> ReadingPosition(bookId, offset) }

    @Synchronized override fun savePosition(position: ReadingPosition) {
        positions[position.bookId] = position.offset
        lastRead[position.bookId] = ++reads
    }

    /** The last offset written, so a test can wait for the asynchronous save. */
    @Synchronized fun savedOffset(): Int? = positions.values.firstOrNull()

    /**
     * One short paragraph per line, each starting with "On": Robolectric's tight
     * glyph metrics then put the page boundary between paragraphs, so page two
     * starts with the same word as page one and the resume and lemma assertions
     * are deterministic instead of layout-dependent.
     */
    private fun longText(book: Book): StructuredText = StructuredText(
        title = book.title,
        language = book.language,
        blocks = List(PARAGRAPHS) {
            TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("On the quiet evening.")))
        },
    )
}

/**
 * A word link on the page: a clickable node that carries neither text nor a
 * description. The reader's chrome has an icon since the navigation shell
 * (issue #185) — clickable, described, and not a word — so a word is the one
 * clickable node with nothing to read on it.
 */
private val WORD_LINK = hasClickAction() and
    SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
    SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)
