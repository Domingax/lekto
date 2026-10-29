package app.lekto

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
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
import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore
import app.lekto.core.speech.Pronouncer
import app.lekto.core.speech.SpeechResult
import app.lekto.core.sync.SyncEngine
import app.lekto.core.sync.SyncSettings
import app.lekto.core.text.BlockKind
import app.lekto.core.text.Chapter
import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.text.WordKey
import app.lekto.core.theme.AppTheme
import app.lekto.core.theme.AppThemeStore
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VersionedRecord
import app.lekto.core.vocabulary.VaultVocabulary
import app.lekto.core.vocabulary.Vocabulary
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.core.vocabulary.VocabularyRecord
import app.lekto.dictionary.DictionaryRelease
import app.lekto.dictionary.DictionaryServices
import app.lekto.dictionary.LOOKUP_CLOSE_TAG
import app.lekto.dictionary.LOOKUP_PANEL_TAG
import app.lekto.dictionary.PRONOUNCE_TAG
import app.lekto.dictionary.masteryTag
import app.lekto.difficulty.DifficultyRelease
import app.lekto.difficulty.DifficultyServices
import app.lekto.language.LANGUAGES_ATTRIBUTION_TAG
import app.lekto.language.LANGUAGES_BACK_TAG
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
import app.lekto.settings.ABOUT_LINK_TAG
import app.lekto.settings.ABOUT_SOURCE_TAG
import app.lekto.settings.ABOUT_VERSION_TAG
import app.lekto.settings.API_KEY_FIELD_TAG
import app.lekto.settings.APPEARANCE_LINK_TAG
import app.lekto.settings.APP_LANGUAGE_BACK_TAG
import app.lekto.settings.APP_LANGUAGE_LINK_TAG
import app.lekto.settings.ATTRIBUTION_BACK_TAG
import app.lekto.settings.ATTRIBUTION_LINK_TAG
import app.lekto.settings.LANGUAGES_LINK_TAG
import app.lekto.settings.LEKTO_SOURCE_URL
import app.lekto.settings.MODEL_FIELD_TAG
import app.lekto.settings.PROVIDER_CONNECT_TAG
import app.lekto.settings.PROVIDER_LINK_TAG
import app.lekto.settings.PROVIDER_PICKER_TAG
import app.lekto.settings.ProviderController
import app.lekto.settings.SETTINGS_TITLE_TAG
import app.lekto.settings.SYNC_CONNECT_TAG
import app.lekto.settings.SYNC_HOST_FIELD_TAG
import app.lekto.settings.SYNC_PASSWORD_FIELD_TAG
import app.lekto.settings.SYNC_USERNAME_FIELD_TAG
import app.lekto.settings.SyncController
import app.lekto.settings.SyncServices
import app.lekto.settings.VAULT_LINK_TAG
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
import app.lekto.translation.PHRASE_CLOSE_TAG
import app.lekto.translation.TRANSLATION_RESULT_TAG
import app.lekto.translation.TRANSLATION_SETTINGS_TAG
import app.lekto.translation.TRANSLATION_SHORTCUT_TAG
import app.lekto.ui.CONFIRM_DIALOG_CONFIRM_TAG
import app.lekto.vocabulary.VOCABULARY_SEARCH_TAG
import app.lekto.vocabulary.VOCABULARY_TITLE_TAG
import app.lekto.vocabulary.vocabularyDeleteTag
import app.lekto.vocabulary.vocabularyFilterTag
import app.lekto.vocabulary.vocabularyMasteryTag
import app.lekto.vocabulary.vocabularyRowTag
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The application's reading-loop flow through semantics (issues #15 and #16): it
 * opens on the library, an import lands a book, tapping that book starts a
 * reading session over its parsed text, and leaving and reopening the book
 * resumes at the page the reader left.
 */
@OptIn(ExperimentalTestApi::class)
// One flow, one test per step; splitting the class hides the whole path.
@Suppress("TooManyFunctions", "LargeClass", "MagicNumber")
class AppSemanticsTest {

    @Test
    fun opensOnTheLibraryAndImportsABook() = runComposeUiTest {
        val library = InMemoryLibrary()
        setContent { App(environment(library, pickFile = { PickedFile("lantern.epub", byteArrayOf(1)) })) }

        // The first frame is the shelf in skeleton (docs/design.md §7.7); the
        // empty state lands once the local vault read returns (issue #208).
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("No books yet").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Import a book").performClick()

        onNodeWithText("The Lantern Keeper").assertIsDisplayed()
    }

    @Test
    fun openingABookStartsAReadingSession() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        openBook()

        awaitPage(1)
        onNodeWithTag(READER_BACK_TAG).assertIsDisplayed()
    }

    @Test
    fun eachSectionIsReachedFromTheBar() = runComposeUiTest {
        setContent { App(environment(InMemoryLibrary())) }

        tapSection(ShellSection.VOCABULARY)
        onNodeWithTag(VOCABULARY_TITLE_TAG).assertIsDisplayed()

        tapSection(ShellSection.SETTINGS)
        onNodeWithTag(SETTINGS_TITLE_TAG).assertIsDisplayed()

        tapSection(ShellSection.LIBRARY)
        onNodeWithTag(LIBRARY_TITLE_TAG).assertIsDisplayed()
    }

    @Test
    fun theReaderCoversTheShell() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }
        onNodeWithTag(SHELL_NAV_TAG).assertIsDisplayed()

        openBook()

        // The reader leaves the shell and is full-screen over it (issue #185).
        onNodeWithTag(SHELL_NAV_TAG).assertDoesNotExist()
        leaveReader()
        onNodeWithTag(SHELL_NAV_TAG).assertIsDisplayed()
    }

    @Test
    fun reopeningABookResumesWhereTheReaderLeftOff() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        openBook()
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        awaitPage(2)
        // Opening records the first page and the turn records the second,
        // both off the UI thread; wait for the second before leaving the book.
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { (library.savedOffset() ?: 0) > 0 }

        leaveReader()
        openBook()

        awaitPage(2)
    }

    @Test
    fun aPageTurnedJustBeforeLeavingLeadsTheLibrary() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        onNodeWithText("Continue reading").assertDoesNotExist()
        openBook()
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        // Leave at once: the position write is still in flight, and the library
        // still has to re-read when it lands (issue #188).
        leaveReader()

        // Only a refresh after the write can fill the section, so its
        // appearance proves the library re-read the vault (issue #188).
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("Continue reading").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Continue reading").assertIsDisplayed()
    }

    @Test
    fun leavingTheLibraryClearsTheSearch() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        onNodeWithTag(LIBRARY_SEARCH_TAG).performTextInput("zzz")
        onNodeWithText("No books match \"zzz\".").assertIsDisplayed()

        // The query is view state, so it lives no longer than the screen (issue #173).
        tapSection(ShellSection.VOCABULARY)
        tapSection(ShellSection.LIBRARY)

        onNodeWithText("No books match \"zzz\".").assertDoesNotExist()
        onNodeWithText("The Lantern Keeper").assertIsDisplayed()
    }

    @Test
    fun leavingTheLibraryForSettingsOrLanguagesClearsTheSearch() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        typeSearch("zzz")
        tapSection(ShellSection.SETTINGS)
        tapSection(ShellSection.LIBRARY)
        onNodeWithText("No books match \"zzz\".").assertDoesNotExist()

        typeSearch("zzz")
        openLanguages()
        onNodeWithTag(LANGUAGES_BACK_TAG).performClick()
        tapSection(ShellSection.LIBRARY)
        onNodeWithText("No books match \"zzz\".").assertDoesNotExist()
    }

    @Test
    fun leavingTheLibraryForABookClearsTheSearch() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        typeSearch("lantern")
        openBook()
        leaveReader()

        onNodeWithTag(LIBRARY_SEARCH_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
    }

    @Test
    fun theReadingPreferenceIsKeptAcrossSessions() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val preferences = InMemoryReadingPreferencesStore()
        setContent { App(environment(library, readingPreferences = preferences)) }

        openBook()
        // Opening a book runs on the environment's dispatcher, so wait for the
        // reader's chrome before touching its **Aa** control.
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithTag(READING_PREFERENCES_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        onNodeWithTag(READING_LARGER_TAG).performClick()

        assertEquals(20f, preferences.load().size)

        // Leave the book and reopen it: the choice is device-local configuration,
        // not session state, so the reader starts from it (ADR-0035).
        onNodeWithTag(READING_PREFERENCES_CLOSE_TAG).performClick()
        leaveReader()
        openBook()
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithTag(READING_PREFERENCES_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        onNodeWithTag(READING_LARGER_TAG).performClick()

        assertEquals(22f, preferences.load().size)
    }

    @Test
    fun theAppThemeChoiceIsKeptAndMarkedAtOnce() = runComposeUiTest {
        val library = InMemoryLibrary()
        val appTheme = InMemoryAppThemeStore()
        setContent { App(environment(library, appTheme = appTheme), systemInDarkTheme = false) }

        tapSection(ShellSection.SETTINGS)
        onNodeWithTag(APPEARANCE_LINK_TAG).performScrollTo().performClick()
        onNodeWithTag(appearanceOptionTag(AppTheme.DARK)).performClick()

        // The store is the truth and the screen is already repainted in the new
        // paper: choosing one writes through and marks it, with no toast (ADR-0036).
        assertEquals(AppTheme.DARK, appTheme.load())
        onNodeWithTag(appearanceOptionTag(AppTheme.DARK)).assertIsSelected()
    }

    @Test
    fun tappingAWordOpensTheLookupPanelWithTheShortcuts() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, languageSettings = frenchTranslations())) }

        openBook()
        tapFirstWord()

        onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        // No pack is wired, so the panel degrades to the honest message and the
        // reference shortcuts (issue #19).
        onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        onNodeWithText("Reverso").assertIsDisplayed()
    }

    @Test
    fun aReferenceShortcutOpensTheCanonicalPageInTheBrowser() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        var opened: String? = null
        setContent {
            App(
                environment(
                    library,
                    languageSettings = frenchTranslations(),
                    openUrl = { url -> opened = url },
                ),
            )
        }

        openBook()
        tapFirstWord()
        onNodeWithText("Reverso").performClick()

        assertTrue(
            opened.orEmpty().startsWith("https://context.reverso.net/translation/english-french/"),
            "the shortcut must open Reverso's canonical page: $opened",
        )
    }

    @Test
    fun closingTheLookupPanelReturnsToTheSameReadingPosition() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, languageSettings = frenchTranslations())) }

        openBook()
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        awaitPage(2)

        tapFirstWord()
        onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        // The reader stays put behind the panel, at the same page.
        awaitPage(2)

        onNodeWithTag(LOOKUP_CLOSE_TAG).performClick()

        onNodeWithTag(LOOKUP_PANEL_TAG).assertDoesNotExist()
        awaitPage(2)
    }

    @Test
    fun tappingOutsideTheWordLookupPanelClosesItWithoutTurningThePage() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, languageSettings = frenchTranslations())) }

        openBook()
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        awaitPage(2)

        tapFirstWord()
        onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()

        // A tap on the page, above the panel, is outside it: it closes the panel
        // and does not turn the page.
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(center.x, 4f)) }

        onNodeWithTag(LOOKUP_PANEL_TAG).assertDoesNotExist()
        awaitPage(2)
    }

    @Test
    fun aWordAtThePageBottomIsLiftedAboveTheLookupPanel() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, languageSettings = frenchTranslations())) }

        openBook()
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodes(WORD_LINK).fetchSemanticsNodes().isNotEmpty()
        }
        val lastWord = onAllNodes(WORD_LINK).onLast()
        lastWord.performClick()
        onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()

        // The sheet never covers the word the reader touched: the page is lifted
        // under it, without repaginating. The page number is unchanged.
        awaitPage(1)
        val word = lastWord.getUnclippedBoundsInRoot()
        val panel = onNodeWithTag(LOOKUP_PANEL_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            word.bottom.value <= panel.top.value + 1f,
            "the tapped word ends at ${word.bottom} dp, under the panel's ${panel.top} dp",
        )
    }

    @Test
    fun selectingAPhraseOffersTheTranslationShortcutWithNoProviderConfigured() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        var opened: String? = null
        setContent {
            App(
                environment(
                    library,
                    languageSettings = frenchTranslations(),
                    openUrl = { url -> opened = url },
                ),
            )
        }

        openBook()
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        awaitPage(2)

        // Long-press and drag across the page's words to select a phrase.
        selectFirstPhrase()

        // No provider and no key are configured: the shortcut is the whole offer.
        onNodeWithText("Translate online").assertIsDisplayed()
        // The reader stays put behind the panel, at the same page.
        awaitPage(2)
        onNodeWithTag(TRANSLATION_SHORTCUT_TAG).performClick()
        assertTrue(
            opened.orEmpty().startsWith("https://translate.google.com/"),
            "the shortcut must open the translation service's prefilled page: $opened",
        )

        onNodeWithTag(PHRASE_CLOSE_TAG).performClick()
        onNodeWithText("Translate online").assertDoesNotExist()
        awaitPage(2)
    }

    @Test
    fun tappingOutsideTheTranslationPanelClosesItWithoutLookingAWordUp() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, languageSettings = frenchTranslations())) }

        openBook()
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        awaitPage(2)

        selectFirstPhrase()
        onNodeWithText("Translate online").assertIsDisplayed()

        // A tap on the page, above the panel, is outside it: it closes the panel
        // and does not look the word under it up, nor turn the page.
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(center.x, 4f)) }

        onNodeWithText("Translate online").assertDoesNotExist()
        onNodeWithTag(LOOKUP_PANEL_TAG).assertDoesNotExist()
        awaitPage(2)
    }

    @Test
    fun selectingAPhraseStreamsATranslationFromTheConnectedProvider() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val settings = InMemoryLlmSettingsStore(LlmProviderConfig(LlmProvider.ANTHROPIC, "claude-3"))
        val secrets = InMemorySecretStore().apply {
            put(ProviderController.providerSecretKey(LlmProvider.ANTHROPIC), "sk-live-123")
        }
        val client = FakeLlmClient(translation = listOf("La lanterne", " brille"))
        setContent {
            App(
                environment(
                    library,
                    languageSettings = frenchTranslations(),
                    secrets = secrets,
                    llm = LlmServices(settings, client),
                ),
            )
        }

        openBook()
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        awaitPage(2)

        selectFirstPhrase()

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { client.translated.isNotEmpty() }
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithTag(TRANSLATION_RESULT_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        // The selection and its containing sentence leave the device; the answer streams in.
        val user = client.translated.single().messages.single { message -> message.role == "user" }
        assertTrue(user.content.contains("Selected phrase:"), "the request discloses the context: ${user.content}")
        onNodeWithTag(TRANSLATION_RESULT_TAG).assertTextEquals("La lanterne brille")
        // The reader stays put behind the panel.
        awaitPage(2)
    }

    @Test
    fun selectingAPhraseWithoutAProviderLinksToSettings() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, dictionary = dictionaryServices())) }

        openBook()
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        awaitPage(2)

        selectFirstPhrase()

        onNodeWithText("A phrase needs an AI", substring = true).assertIsDisplayed()
        onNodeWithTag(TRANSLATION_SETTINGS_TAG).performClick()

        // The non-blocking prompt lands on settings, leaving the book behind.
        onNodeWithTag(SETTINGS_TITLE_TAG).assertIsDisplayed()
    }

    @Test
    fun listeningToAWordSpeaksItThroughThePlatform() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val pronouncer = FakePronouncer()
        setContent { App(environment(library, pronouncer = pronouncer)) }

        openBook()
        tapFirstWord()
        onNodeWithTag(PRONOUNCE_TAG).performClick()

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { pronouncer.utterances.isNotEmpty() }
        val (text, language) = pronouncer.utterances.single()
        assertTrue(text.isNotBlank(), "the tapped word must be spoken")
        assertEquals("en", language)
    }

    @Test
    fun aLanguageWithNoVoiceSaysSoRatherThanFailing() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val pronouncer = FakePronouncer(SpeechResult.NoVoice("en"))
        setContent { App(environment(library, pronouncer = pronouncer)) }

        openBook()
        tapFirstWord()
        onNodeWithTag(PRONOUNCE_TAG).performClick()

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { pronouncer.utterances.isNotEmpty() }
        waitForIdle()
        onNodeWithText("No voice is installed", substring = true).assertIsDisplayed()
    }

    @Test
    fun speakingWithNoEngineWiredSaysSo() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        openBook()
        tapFirstWord()
        onNodeWithTag(PRONOUNCE_TAG).performClick()

        onNodeWithText("No speech engine is available", substring = true).assertIsDisplayed()
    }

    @Test
    fun reachesTheLanguagesSurfaceAndItsAttributionFromTheLibrary() = runComposeUiTest {
        setContent {
            App(environment(InMemoryLibrary(), languages = dictionaryCatalog(), dictionary = dictionaryServices()))
        }

        openLanguages()
        onNodeWithText("English → French", substring = true).assertIsDisplayed()

        onNodeWithTag(LANGUAGES_ATTRIBUTION_TAG).performClick()

        onNodeWithText("Dictionary data is derived from Wiktionary contributors", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun theLanguagesSurfaceShowsAnInstalledDictionaryAndItsAttribution() = runComposeUiTest {
        val installed = dictionaryServices().also { services ->
            services.installer("en", "fr").install(DictionaryRelease.url(DictionaryRelease.artifact("en", "fr")))
        }
        setContent { App(environment(InMemoryLibrary(), languages = dictionaryCatalog(), dictionary = installed)) }

        openLanguages()
        onNodeWithText("is installed", substring = true).assertIsDisplayed()

        onNodeWithTag(LANGUAGES_ATTRIBUTION_TAG).performClick()
        onNodeWithText("Wiktionary contributors").assertIsDisplayed()
    }

    @Test
    fun connectingALanguageModelFromSettingsRunsAConnectionTest() = runComposeUiTest {
        val settings = InMemoryLlmSettingsStore()
        val secrets = InMemorySecretStore()
        val client = FakeLlmClient()
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    secrets = secrets,
                    llm = LlmServices(settings, client),
                ),
            )
        }

        openProvider()
        onNodeWithTag(PROVIDER_PICKER_TAG).performScrollTo().performClick()
        onNodeWithText("Anthropic").performClick()
        onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performTextInput("claude-3")
        onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        onNodeWithTag(PROVIDER_CONNECT_TAG).performScrollTo().performClick()

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { client.tested.isNotEmpty() }
        assertEquals("sk-live-123", client.tested.single().second)
        // Connection stores the key only after the provider answers (issue #213).
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            secrets.get(ProviderController.providerSecretKey(LlmProvider.ANTHROPIC)) ==
                SecretResult.Found("sk-live-123")
        }
        onNodeWithText("The provider answered.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosingAModelFromTheProvidersListFillsTheModel() = runComposeUiTest {
        val client = FakeLlmClient(models = LlmModelsResult.Loaded(listOf("claude-3", "claude-3-5")))
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    secrets = InMemorySecretStore(),
                    llm = LlmServices(InMemoryLlmSettingsStore(), client),
                ),
            )
        }

        openProvider()
        onNodeWithTag(PROVIDER_PICKER_TAG).performScrollTo().performClick()
        onNodeWithText("Anthropic").performClick()

        // Choosing the provider loads its models with no separate action.
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { client.listed.isNotEmpty() }
        onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performClick()
        onNodeWithText("claude-3-5").performClick()

        onNodeWithTag(MODEL_FIELD_TAG).performScrollTo()
        onNodeWithText("claude-3-5").assertIsDisplayed()
    }

    @Test
    fun configuresSyncFromSettings() = runComposeUiTest {
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
        setContent { App(environment(InMemoryLibrary(), secrets = secrets, sync = sync)) }

        openVault()
        onNodeWithTag(SYNC_HOST_FIELD_TAG).performScrollTo().performTextInput("https://cloud.example.test/dav")
        onNodeWithTag(SYNC_USERNAME_FIELD_TAG).performScrollTo().performTextInput("reader")

        // The endpoint is reversible, so it applies on change (issue #213).
        assertEquals(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader"),
            settings.load(),
        )
        onNodeWithTag(SYNC_PASSWORD_FIELD_TAG).performScrollTo().performTextInput("app-password")
        onNodeWithTag(SYNC_CONNECT_TAG).performScrollTo().performClick()

        // Connection tests the server, stores the password and turns sync on.
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { settings.load().enabled }
        assertEquals(SecretResult.Found("app-password"), secrets.get(SyncController.PASSWORD_KEY))
        assertEquals(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = true),
            settings.load(),
        )
    }

    @Test
    fun aWordSyncedFromAnotherDeviceAppearsWithoutARestart() = runComposeUiTest {
        // A mastery change (or a new word) on another device reaches this one by
        // sync; the reader must show it without relaunching the app (issue #132).
        val vault = InMemoryVaultStore()
        val target = InMemorySyncTarget(changeCursor = false)
        val entry = VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.RECOGNIZED)
        val record = VocabularyRecord.of(entry, deterministicSeams().clock.now(), DeviceId("device-b"))
        runBlocking { target.put(VersionedRecord.of(record), null) }
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val vocabulary = VaultVocabulary(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a"))
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    vocabulary = vocabulary,
                    secrets = secrets,
                    sync = enabledSyncServices(vault, target),
                ),
            )
        }

        tapSection(ShellSection.VOCABULARY)

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("lantern").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("lantern").assertIsDisplayed()
    }

    @Test
    fun savingAWordFromThePanelStoresItWithItsContextAndLevel() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, vocabulary = vocabulary)) }

        openBook()
        tapFirstWord()
        // The level is the save: one tap on the fresh level's chip stores the word.
        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vocabulary.all().isNotEmpty() }
        val entry = vocabulary.all().single()
        assertEquals("on", entry.key.key)
        assertTrue(
            entry.contextSentence.orEmpty().contains("On"),
            "the context sentence is kept: ${entry.contextSentence}",
        )
        // The word has no signal (no reader level, no difficulty), so the fresh
        // save defaults to the familiar level (ADR-0030).
        assertEquals(MasteryLevel.FAMILIAR, entry.mastery)
        // No toast, no dialog and no Save control: the reader's colour is the
        // confirmation, and the footer already names the level.
        onNodeWithText("Familiar").assertIsDisplayed()
    }

    @Test
    fun theMasteryLevelCanBeChangedAfterSaving() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, vocabulary = vocabulary)) }

        openBook()
        tapFirstWord()
        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vocabulary.all().isNotEmpty() }

        onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).performClick()

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            vocabulary.all().single().mastery == MasteryLevel.LEARNED
        }
        assertEquals(1, vocabulary.all().size, "changing the level must not add a second entry")
    }

    @Test
    fun aWordSavedInAnEarlierSessionIsShownAsSaved() = runComposeUiTest {
        val vault = InMemoryVaultStore()
        val vocabulary = VaultVocabulary(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a"))
        vocabulary.save(VocabularyEntry(WordKey("en", "on"), "On", mastery = MasteryLevel.LEARNED))
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, vocabulary = vocabulary)) }

        openBook()
        tapFirstWord()

        // The entry survived the restart: the sheet opens on its level.
        onNodeWithText("Learned").assertIsDisplayed()
        onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).assertIsSelected()
    }

    @Test
    fun savingAnInflectedFormDoesNotCreateASecondEntryForTheLemma() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        // The pack knows "lanterns" resolves to "lantern", so the two spellings
        // share one key.
        val lemmas = LemmaLookup { surface, _ -> if (surface == "lanterns") "lantern" else null }
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, vocabulary = vocabulary, lemmas = lemmas)) }

        openBook()
        tapFirstWord()
        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vocabulary.all().isNotEmpty() }

        // The word panel is open over the page bar; close it before turning the page.
        onNodeWithTag(LOOKUP_CLOSE_TAG).performClick()
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        tapFirstWord()
        // The same word (page two starts with "On.", so its key is "on"), so its
        // entry updates in place rather than adding a second one.
        onNodeWithText("Familiar").assertIsDisplayed()
        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).assertIsSelected()
        assertEquals(1, vocabulary.all().size)
    }

    @Test
    fun theVocabularyListShowsSavedWordsWithTheirDetails() = runComposeUiTest {
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
        setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        tapSection(ShellSection.VOCABULARY)

        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("lanterne").assertIsDisplayed()
        onNodeWithText("The lantern burned all night.").assertIsDisplayed()
        onNodeWithTag(vocabularyMasteryTag(WordKey("en", "lantern"))).assertTextEquals("Learned")
    }

    @Test
    fun theVocabularyListDerivesItsTranslationFromTheInstalledPack() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(
            VocabularyEntry(
                key = WordKey("en", "blorple"),
                surface = "blorpled",
                translation = "lanterne",
                mastery = MasteryLevel.LEARNED,
            ),
        )
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    dictionary = installedDictionary(),
                    vocabulary = vocabulary,
                    languageSettings = frenchTranslations(),
                ),
            )
        }

        tapSection(ShellSection.VOCABULARY)
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("blorper").fetchSemanticsNodes().isNotEmpty()
        }

        // The pack's gloss for the lemma, not the frozen translation the entry kept.
        onNodeWithText("blorper").assertIsDisplayed()
        onNodeWithText("lanterne").assertDoesNotExist()
    }

    @Test
    fun theVocabularyListFallsBackToTheStoredTranslationWhenThePackDoesNotKnowTheWord() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", translation = "lanterne"))
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    dictionary = installedDictionary(),
                    vocabulary = vocabulary,
                    languageSettings = frenchTranslations(),
                ),
            )
        }

        tapSection(ShellSection.VOCABULARY)
        waitForIdle()

        onNodeWithText("lanterne").assertIsDisplayed()
    }

    @Test
    fun theVocabularyListFallsBackToTheStoredTranslationWithNoPackInstalled() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "blorple"), "blorpled", translation = "lanterne"))
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    dictionary = dictionaryServices(),
                    vocabulary = vocabulary,
                    languageSettings = frenchTranslations(),
                ),
            )
        }

        tapSection(ShellSection.VOCABULARY)
        waitForIdle()

        onNodeWithText("lanterne").assertIsDisplayed()
    }

    @Test
    fun theVocabularyListFallsBackToTheStoredTranslationWhenNoTranslationLanguageIsChosen() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "blorple"), "blorpled", translation = "lanterne"))
        setContent {
            App(environment(InMemoryLibrary(), dictionary = installedDictionary(), vocabulary = vocabulary))
        }

        tapSection(ShellSection.VOCABULARY)
        waitForIdle()

        onNodeWithText("lanterne").assertIsDisplayed()
    }

    @Test
    fun theVocabularySearchesThePackDerivedTranslation() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(
            VocabularyEntry(WordKey("en", "blorple"), "blorpled", translation = "lanterne"),
        )
        vocabulary.save(VocabularyEntry(WordKey("en", "harbour"), "harbour", translation = "port"))
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    dictionary = installedDictionary(),
                    vocabulary = vocabulary,
                    languageSettings = frenchTranslations(),
                ),
            )
        }

        tapSection(ShellSection.VOCABULARY)
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("blorper").fetchSemanticsNodes().isNotEmpty()
        }

        // The row searches what it shows: the derived gloss, not the frozen one.
        onNodeWithTag(VOCABULARY_SEARCH_TAG).performTextInput("blorper")

        onNodeWithText("blorpled").assertIsDisplayed()
        onNodeWithText("harbour").assertDoesNotExist()
    }

    @Test
    fun theVocabularyCanBeSearched() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.LEARNED))
        vocabulary.save(VocabularyEntry(WordKey("en", "harbour"), "harbour", mastery = MasteryLevel.RECOGNIZED))
        setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        tapSection(ShellSection.VOCABULARY)
        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("harbour").assertIsDisplayed()

        onNodeWithTag(VOCABULARY_SEARCH_TAG).performTextInput("har")

        onNodeWithText("harbour").assertIsDisplayed()
        onNodeWithText("lantern").assertDoesNotExist()
    }

    @Test
    fun theVocabularyCanBeFilteredByMastery() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.LEARNED))
        vocabulary.save(VocabularyEntry(WordKey("en", "harbour"), "harbour", mastery = MasteryLevel.RECOGNIZED))
        setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        tapSection(ShellSection.VOCABULARY)
        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("harbour").assertIsDisplayed()

        onNodeWithTag(vocabularyFilterTag(MasteryLevel.LEARNED)).performClick()

        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("harbour").assertDoesNotExist()
    }

    @Test
    fun anEmptyVocabularyShowsTheInstructionalEmptyState() = runComposeUiTest {
        setContent { App(environment(InMemoryLibrary(), vocabulary = inMemoryVocabulary())) }

        tapSection(ShellSection.VOCABULARY)

        onNodeWithText("No words saved yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun deletingAWordDropsItFromTheReader() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, vocabulary = vocabulary)) }

        // Save the first word at a level, then find it in the list.
        openBook()
        tapFirstWord()
        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vocabulary.all().isNotEmpty() }
        val saved = vocabulary.all().single()

        leaveReader()
        tapSection(ShellSection.VOCABULARY)
        // The delete is a row gesture behind a confirmation now (issue #212): the
        // trash appears on hover, and only the dialog's own action removes the word.
        onNodeWithTag(vocabularyRowTag(saved.key)).performMouseInput { moveTo(center) }
        onNodeWithTag(vocabularyDeleteTag(saved.key)).performClick()
        onNodeWithTag(CONFIRM_DIALOG_CONFIRM_TAG).performClick()
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vocabulary.all().isEmpty() }
        // The list recomposes after the write; wait for the empty state before asserting.
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("No words saved yet", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("No words saved yet", substring = true).assertIsDisplayed()

        // Reopening the book, the word is unsaved again — the reader's colour
        // dropped with the entry, so list and text agree.
        tapSection(ShellSection.LIBRARY)
        openBook()
        tapFirstWord()

        onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        assertTrue(
            onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty(),
            "the level is the save; no Save word survives",
        )
    }

    @Test
    fun theIndexReachesAttributionAndAboutInOneTap() = runComposeUiTest {
        var opened: String? = null
        setContent { App(environment(InMemoryLibrary(), openUrl = { url -> opened = url })) }

        tapSection(ShellSection.SETTINGS)
        onNodeWithTag(ATTRIBUTION_LINK_TAG).performScrollTo().performClick()
        onNodeWithText("Dictionary data is derived from Wiktionary contributors", substring = true)
            .performScrollTo().assertIsDisplayed()
        onNodeWithTag(ATTRIBUTION_BACK_TAG).performClick()

        // The About row names the version, and its source opens where the platform
        // sends a link (issue #214).
        onNodeWithTag(ABOUT_LINK_TAG).performScrollTo().performClick()
        onNodeWithTag(ABOUT_VERSION_TAG).assertTextEquals("Version 0.1.0")
        onNodeWithTag(ABOUT_SOURCE_TAG).performScrollTo().performClick()

        assertEquals(LEKTO_SOURCE_URL, opened)
    }

    @Test
    fun reachesTheLanguagesSurfaceFromSettings() = runComposeUiTest {
        setContent { App(environment(InMemoryLibrary(), languages = languageCatalog())) }

        openLanguages()

        onNodeWithText("English").assertIsDisplayed()
    }

    @Test
    fun theAppLanguageOffersTheLanguagesTheAppShips() = runComposeUiTest {
        setContent { App(environment(InMemoryLibrary(), languageSettings = InMemoryLanguageSettingsStore())) }

        openAppLanguage()

        onNodeWithTag(appLanguageOptionTag("en")).assertIsDisplayed()
        onNodeWithTag(appLanguageOptionTag("fr")).assertIsDisplayed()
        // A language the app does not ship is never offered.
        onNodeWithTag(appLanguageOptionTag("es")).assertDoesNotExist()
    }

    @Test
    fun choosingAnAppLanguageInSettingsStoresIt() = runComposeUiTest {
        val settings = InMemoryLanguageSettingsStore()
        setContent { App(environment(InMemoryLibrary(), languageSettings = settings)) }

        openAppLanguage()
        onNodeWithTag(appLanguageOptionTag("fr")).performClick()

        assertEquals("fr", settings.load().appLanguage)
        // The whole interface follows the choice without a relaunch (ADR-0034), and
        // the index names it under **App language** once back on the list.
        onNodeWithTag(APP_LANGUAGE_BACK_TAG).performClick()
        onNodeWithTag(SETTINGS_TITLE_TAG).assertIsDisplayed()
        onNodeWithText("français").assertIsDisplayed()
    }

    @Test
    fun theFirstLaunchDefaultsTheAppLanguageToTheDeviceLanguage() = runComposeUiTest {
        val settings = InMemoryLanguageSettingsStore()
        setContent {
            App(environment(InMemoryLibrary(), languageSettings = settings, systemLanguage = "fr"))
        }

        // The default lands with no question and no surface opened.
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { settings.load().appLanguage != null }
        assertEquals("fr", settings.load().appLanguage)
    }

    @Test
    fun theFirstLaunchDefaultsTheAppLanguageToEnglishWhenTheAppDoesNotShipTheDeviceLanguage() = runComposeUiTest {
        val settings = InMemoryLanguageSettingsStore()
        setContent {
            App(environment(InMemoryLibrary(), languageSettings = settings, systemLanguage = "de"))
        }

        waitForIdle()

        assertEquals("en", settings.load().appLanguage)
    }

    @Test
    fun theLanguagesSurfaceOffersOnlyThePublishedTranslationLanguages() = runComposeUiTest {
        setContent { App(environment(InMemoryLibrary(), languages = translationCatalog())) }

        openLanguages()
        onNodeWithTag(TRANSLATION_LANGUAGE_PICKER_TAG).performClick()

        onNodeWithTag(translationLanguageOptionTag("fr")).assertIsDisplayed()
        onNodeWithTag(translationLanguageOptionTag("es")).assertIsDisplayed()
        // English is a Reading language, not one the packs translate into.
        onNodeWithTag(translationLanguageOptionTag("en")).assertDoesNotExist()
    }

    @Test
    fun choosingATranslationLanguageOnTheLanguagesSurfaceStoresIt() = runComposeUiTest {
        val settings = InMemoryLanguageSettingsStore()
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = translationCatalog(),
                    languageSettings = settings,
                ),
            )
        }

        openLanguages()
        onNodeWithTag(TRANSLATION_LANGUAGE_PICKER_TAG).performClick()
        onNodeWithTag(translationLanguageOptionTag("es")).performClick()

        assertEquals("es", settings.load().translationLanguage)
        // The surface shows the choice without a relaunch.
        onNodeWithText("Spanish").assertIsDisplayed()
    }

    @Test
    fun theFirstLaunchDefaultsTheTranslationLanguageToTheDeviceLanguage() = runComposeUiTest {
        val settings = InMemoryLanguageSettingsStore()
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = translationCatalog(),
                    languageSettings = settings,
                    systemLanguage = "fr",
                ),
            )
        }

        openLanguages()

        // The default lands once the manifest is read; no question is asked.
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { settings.load().translationLanguage != null }
        assertEquals("fr", settings.load().translationLanguage)
    }

    @Test
    fun anEnglishDeviceKeepsEnglishForTheInterfaceButTheReaderChoosesTheTranslationLanguage() = runComposeUiTest {
        // The bug this fixes: an English interface must not force English translations.
        val settings = InMemoryLanguageSettingsStore()
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = translationCatalog(),
                    languageSettings = settings,
                    systemLanguage = "en",
                ),
            )
        }

        openLanguages()
        onNodeWithTag(TRANSLATION_LANGUAGE_PICKER_TAG).performClick()
        onNodeWithTag(translationLanguageOptionTag("fr")).performClick()

        assertEquals("en", settings.load().appLanguage)
        assertEquals("fr", settings.load().translationLanguage)
    }

    @Suppress("SleepInsteadOfDelay") // The fetch must block its worker: that is the in-flight window under test.
    @Test
    fun reloadingTheLanguageListRefetchesItWhenNoneIsCached() = runComposeUiTest {
        // The first open finds no cache and the fetch fails: the honest empty state.
        // The fetch is held in flight for a moment, the way a loaded machine holds
        // it, because that is where CI lost this test: the surface renders its empty
        // state at once, so a tap on **Reload list** asked for before the first
        // fetch lands is a dropped tap — the action is disabled while a fetch runs —
        // and the failure is never retried. Waiting for the failure first is the
        // fix, and the held fetch makes that wait load-bearing here too.
        val catalog = InMemoryLanguageCatalog().apply { failure = IOException("offline") }
        catalog.whileInFlight = { if (catalog.reloads == 1) Thread.sleep(FIRST_FETCH_MILLIS) }
        setContent { App(environment(InMemoryLibrary(), languages = catalog)) }

        openLanguages()
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("offline").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("hasn't loaded the list of languages", substring = true).assertIsDisplayed()

        catalog.failure = null
        catalog.onReload = LanguageCatalogState.Available(listOf(testOfferedLanguage("en")))
        onNodeWithTag(LANGUAGES_RELOAD_TAG).performClick()

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("English").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("English").assertIsDisplayed()
    }

    @Test
    fun settingAReaderLevelOnTheLanguagesSurfaceStoresIt() = runComposeUiTest {
        val levels = InMemoryReaderLevels()
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = languageCatalog(),
                    vocabulary = inMemoryVocabulary(),
                    readerLevels = levels,
                ),
            )
        }

        openLanguages()
        onNodeWithTag(languageLevelPickerTag("en")).performClick()
        onNodeWithTag(languageLevelOptionTag("en", CefrLevel.B1)).performClick()

        assertEquals(CefrLevel.B1, levels.levelFor("en"))
    }

    @Test
    fun clearingAReaderLevelOnTheLanguagesSurfaceDeletesIt() = runComposeUiTest {
        val levels = InMemoryReaderLevels(mapOf("en" to CefrLevel.B2))
        setContent {
            App(
                environment(
                    InMemoryLibrary(),
                    languages = languageCatalog(),
                    vocabulary = inMemoryVocabulary(),
                    readerLevels = levels,
                ),
            )
        }

        openLanguages()
        onNodeWithText("B2").assertIsDisplayed()
        onNodeWithTag(languageLevelPickerTag("en")).performClick()
        onNodeWithTag(languageLevelClearTag("en")).performClick()

        assertNull(levels.levelFor("en"))
    }

    @Test
    fun downloadingDifficultyOnTheLanguagesSurfaceMarksItInstalled() = runComposeUiTest {
        setContent {
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

        openLanguages()
        // The manifest publishes a table for English, so the surface offers its download.
        onNodeWithTag(languageDifficultyDownloadTag("en")).performClick()

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("Download again").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("is installed", substring = true).assertIsDisplayed()
    }

    /** Taps one of the shell's three sections: the bar on a phone, the column on desktop (issue #185). */
    private fun ComposeUiTest.tapSection(section: ShellSection) {
        onNodeWithTag(shellSectionTag(section)).performClick()
    }

    /** Leaves the reader for the shelf through its own back control, an icon in its band (issue #185). */
    private fun ComposeUiTest.leaveReader() {
        onNodeWithTag(READER_BACK_TAG).performClick()
    }

    /**
     * Reaches the **Languages** surface: an entry inside Settings now that the
     * shell owns the sections, so the way in is Settings and its doorway
     * (ADR-0034, issue #185).
     */
    private fun ComposeUiTest.openLanguages() {
        tapSection(ShellSection.SETTINGS)
        onNodeWithTag(LANGUAGES_LINK_TAG).performScrollTo().performClick()
    }

    /** Reaches the **App language** screen from Settings and its row (issue #214). */
    private fun ComposeUiTest.openAppLanguage() {
        tapSection(ShellSection.SETTINGS)
        onNodeWithTag(APP_LANGUAGE_LINK_TAG).performScrollTo().performClick()
    }

    /** Reaches the **LLM provider** screen from Settings and its doorway (issue #213). */
    private fun ComposeUiTest.openProvider() {
        tapSection(ShellSection.SETTINGS)
        onNodeWithTag(PROVIDER_LINK_TAG).performScrollTo().performClick()
    }

    /** Reaches the **Vault and sync** screen from Settings and its doorway (issue #213). */
    private fun ComposeUiTest.openVault() {
        tapSection(ShellSection.SETTINGS)
        onNodeWithTag(VAULT_LINK_TAG).performScrollTo().performClick()
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
 * Sync services over [vault] and [target], turned on with a stored password, so
 * composing the app runs a sync; the test seeds [target] with what another device
 * pushed.
 */
private fun enabledSyncServices(vault: InMemoryVaultStore, target: InMemorySyncTarget): SyncServices = SyncServices(
    settings = InMemorySyncSettingsStore(
        SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = true),
    ),
    target = { _, _ -> target },
    engine = { chosen ->
        SyncEngine(
            vault,
            InMemoryTombstoneStore(),
            InMemoryRemoteIndexStore(),
            chosen,
            TestClock(),
        )
    },
    outcomes = InMemorySyncOutcomeStore(),
)

/** Waits until the reader's page bar reports page [page], then asserts it is there. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.awaitPage(page: Int) {
    waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
        onAllNodesWithText("Page $page of", substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Page $page of", substring = true).assertIsDisplayed()
}

/**
 * Opens the one book every test imports and waits for its reader.
 *
 * Opening runs on the injected dispatcher, so on a loaded machine the reader is
 * not on screen when the click returns; a test that drove it at once would fail
 * on timing rather than behaviour (issue #194).
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.openBook() {
    onNodeWithText("The Lantern Keeper").performClick()
    waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
        onAllNodesWithTag(READER_PAGE_TAG).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Types [query] into the Library's search field, which the screen owns as view state. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.typeSearch(query: String) {
    onNodeWithTag(LIBRARY_SEARCH_TAG).performTextInput(query)
}

/** Taps the first word link on the page: word links carry no text, unlike the chrome buttons. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.tapFirstWord() {
    // The word layer arrives a frame after the reader opens (the page is
    // paginated and tokenised asynchronously), so wait for it before tapping.
    waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
        onAllNodes(WORD_LINK)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    onAllNodes(WORD_LINK)
        .onFirst()
        .performClick()
}

/** Long-presses the first word link and drags across the line, selecting a phrase. */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // The long-press delay and the drag distance are the test's parameters.
private fun ComposeUiTest.selectFirstPhrase() {
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

/** Enough paragraphs to span several pages at any test viewport. */
private const val PARAGRAPHS = 8

/**
 * The budget for a wait that follows work on the app's injected dispatcher.
 * That work crosses a real thread, so the wait has to outlast a busy machine
 * rather than the one-second Compose default (issue #194).
 */
private const val AWAIT_DISPATCHED_WORK_MILLIS: Long = 10_000

/**
 * How long the Languages surface's first fetch is held in flight before it
 * reports its failure. Long enough that the surface renders while the fetch
 * runs — the window CI lost the reload test in — and short enough to be free.
 */
private const val FIRST_FETCH_MILLIS: Long = 300

/**
 * A [BookLibrary] held in memory: import, open and reading progress without a
 * vault. Shared with `AppWindowInsetsSemanticsTest`, which needs a book to open
 * without standing a vault up.
 */
internal class InMemoryLibrary(private val chapters: Boolean = false) : BookLibrary {

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

    /** Enough paragraphs to span several pages at any test viewport. */
    private fun longText(book: Book): StructuredText = StructuredText(
        title = book.title,
        language = book.language,
        chapters = if (chapters) {
            listOf(Chapter("First", startBlock = 0), Chapter("Second", startBlock = PARAGRAPHS / 2))
        } else {
            emptyList()
        },
        blocks = List(PARAGRAPHS) {
            TextBlock(
                BlockKind.PARAGRAPH,
                listOf(
                    TextRun(
                        "On the quiet evening when the harbour lights came on, the keeper lit the lantern " +
                            "and waited for the boats, and the sea beyond the headland was larger than any map.",
                    ),
                ),
            )
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
