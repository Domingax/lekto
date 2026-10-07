package app.lekto

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
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
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.ImportProgress
import app.lekto.core.book.ReadingPosition
import app.lekto.core.book.ReadingSession
import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.secret.SecretStore
import app.lekto.core.speech.Pronouncer
import app.lekto.core.speech.SpeechResult
import app.lekto.core.text.BlockKind
import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.text.WordKey
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.core.vocabulary.VaultVocabulary
import app.lekto.core.vocabulary.Vocabulary
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.dictionary.DictionaryServices
import app.lekto.dictionary.masteryTag
import app.lekto.reader.READER_PAGE_TAG
import app.lekto.settings.API_KEY_FIELD_TAG
import app.lekto.settings.MODEL_FIELD_TAG
import app.lekto.settings.PROVIDER_PICKER_TAG
import app.lekto.settings.ProviderController
import app.lekto.settings.TEST_CONNECTION_TAG
import app.lekto.settings.VaultTransfer
import app.lekto.testkit.FakeDictionaryPackFiles
import app.lekto.testkit.FakeLlmClient
import app.lekto.testkit.FakePronouncer
import app.lekto.testkit.InMemoryLlmSettingsStore
import app.lekto.testkit.InMemorySecretStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.WhitespaceTextSegmenter
import app.lekto.testkit.deterministicSeams
import app.lekto.translation.TRANSLATION_RESULT_TAG
import app.lekto.translation.TRANSLATION_SETTINGS_TAG
import app.lekto.translation.TRANSLATION_SHORTCUT_TAG
import app.lekto.vocabulary.VOCABULARY_SEARCH_TAG
import app.lekto.vocabulary.vocabularyDeleteTag
import app.lekto.vocabulary.vocabularyFilterTag
import app.lekto.vocabulary.vocabularyMasteryTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
@Suppress("TooManyFunctions") // One flow, one test per step; splitting the class hides the whole path.
class AppSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun opensOnTheLibraryAndImportsABook() {
        val library = InMemoryLibrary()
        compose.setContent { App(environment(library, pickFile = { PickedFile("lantern.epub", byteArrayOf(1)) })) }

        compose.onNodeWithText("No books yet", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Import").performClick()

        compose.waitUntil { compose.onAllNodesWithText("The Lantern Keeper").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("The Lantern Keeper").assertIsDisplayed()
    }

    @Test
    fun openingABookStartsAReadingSession() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()

        compose.onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun reopeningABookResumesWhereTheReaderLeftOff() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.onNodeWithText("Next page").performClick()
        compose.awaitPage(2)
        // Persisting runs off the UI thread; wait for it before leaving the book.
        compose.waitUntil { library.savedOffset() != null }

        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("The Lantern Keeper").performClick()

        // The reopened reader resumes past page one: the page number is enough,
        // and Previous page enabled proves it is not page one.
        compose.awaitPage(2)
        compose.onNodeWithText("Previous page").assertIsEnabled()
    }

    @Test
    fun tappingAWordOpensTheLookupPanelWithTheShortcuts() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
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
        compose.setContent { App(environment(library, openUrl = { url -> opened = url })) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
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
        compose.setContent { App(environment(library)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.onNodeWithText("Next page").performClick()
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
    fun selectingAPhraseOffersTheTranslationShortcutWithNoProviderConfigured() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        var opened: String? = null
        compose.setContent { App(environment(library, openUrl = { url -> opened = url })) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.onNodeWithText("Next page").performClick()
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
    fun selectingAPhraseStreamsATranslationFromTheConnectedProvider() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val settings = InMemoryLlmSettingsStore(LlmProviderConfig(LlmProvider.ANTHROPIC, "claude-3"))
        val secrets = InMemorySecretStore().apply {
            put(ProviderController.providerSecretKey(LlmProvider.ANTHROPIC), "sk-live-123")
        }
        val client = FakeLlmClient(translation = listOf("La lanterne", " brille"))
        compose.setContent {
            App(environment(library, secrets = secrets, llm = LlmServices(settings, client)))
        }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.onNodeWithText("Next page").performClick()
        compose.awaitPage(2)

        compose.selectFirstPhrase()

        compose.waitUntil { client.translated.isNotEmpty() }
        compose.waitUntil { compose.onAllNodesWithTag(TRANSLATION_RESULT_TAG).fetchSemanticsNodes().isNotEmpty() }
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

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.onNodeWithText("Next page").performClick()
        compose.awaitPage(2)

        compose.selectFirstPhrase()

        compose.onNodeWithText("Connect an LLM provider", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(TRANSLATION_SETTINGS_TAG).performClick()

        // The non-blocking prompt lands on settings, leaving the book behind.
        compose.onNodeWithText("Dictionary").assertIsDisplayed()
    }

    @Test
    fun listeningToAWordSpeaksItThroughThePlatform() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val pronouncer = FakePronouncer()
        compose.setContent { App(environment(library, pronouncer = pronouncer)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.tapFirstWord()
        compose.onNodeWithText("Listen").performClick()

        compose.waitUntil { pronouncer.utterances.isNotEmpty() }
        val (text, language) = pronouncer.utterances.single()
        assertTrue("the tapped word must be spoken", text.isNotBlank())
        assertEquals("en", language)
    }

    @Test
    fun aLanguageWithNoVoiceSaysSoRatherThanFailing() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val pronouncer = FakePronouncer(SpeechResult.NoVoice("en"))
        compose.setContent { App(environment(library, pronouncer = pronouncer)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.tapFirstWord()
        compose.onNodeWithText("Listen").performClick()

        compose.waitUntil { pronouncer.utterances.isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("No voice is installed", substring = true).assertIsDisplayed()
    }

    @Test
    fun speakingWithNoEngineWiredSaysSo() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.tapFirstWord()
        compose.onNodeWithText("Listen").performClick()

        compose.onNodeWithText("No speech engine is available", substring = true).assertIsDisplayed()
    }

    @Test
    fun reachesSettingsAndAttributionFromTheLibrary() {
        compose.setContent { App(environment(InMemoryLibrary(), dictionary = dictionaryServices())) }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Dictionary").assertIsDisplayed()

        compose.onNodeWithText("Attribution").performScrollTo().performClick()

        compose.onNodeWithText("CC BY-SA 4.0", substring = true).assertIsDisplayed()
    }

    @Test
    fun settingsShowAnInstalledDictionaryAndItsAttribution() {
        val installed = dictionaryServices().also { services -> services.installer.install() }
        compose.setContent { App(environment(InMemoryLibrary(), dictionary = installed)) }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("is installed", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Attribution").performScrollTo().performClick()
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

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag(PROVIDER_PICKER_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Anthropic").performClick()
        compose.onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performTextInput("claude-3")
        compose.onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        compose.onNodeWithTag(TEST_CONNECTION_TAG).performScrollTo().performClick()

        compose.waitUntil { client.tested.isNotEmpty() }
        assertEquals("sk-live-123", client.tested.single().second)
        compose.onNodeWithText("The provider answered.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savingAWordFromThePanelStoresItWithItsContextAndLevel() {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, vocabulary = vocabulary)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.tapFirstWord()
        compose.onNodeWithText("Save").performClick()

        compose.waitUntil { vocabulary.all().isNotEmpty() }
        val entry = vocabulary.all().single()
        assertEquals("on", entry.key.key)
        assertTrue(
            "the context sentence is kept: ${entry.contextSentence}",
            entry.contextSentence.orEmpty().contains("On"),
        )
        assertEquals(MasteryLevel.FAMILIAR, entry.mastery)
        // No toast, no dialog: the saved state on the panel is the confirmation.
        compose.waitUntil { compose.onAllNodesWithText("Saved").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved").assertIsDisplayed()
    }

    @Test
    fun theMasteryLevelCanBeChangedAfterSaving() {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, vocabulary = vocabulary)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.tapFirstWord()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil { vocabulary.all().isNotEmpty() }

        compose.onNodeWithTag(masteryTag(MasteryLevel.MASTERED)).performScrollTo().performClick()

        compose.waitUntil { vocabulary.all().single().mastery == MasteryLevel.MASTERED }
        assertEquals("changing the level must not add a second entry", 1, vocabulary.all().size)
    }

    @Test
    fun aWordSavedInAnEarlierSessionIsShownAsSaved() {
        val vault = InMemoryVaultStore()
        val vocabulary = VaultVocabulary(vault, deterministicSeams(), DeviceId("device-a"))
        vocabulary.save(VocabularyEntry(WordKey("en", "on"), "On", mastery = MasteryLevel.MASTERED))
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, vocabulary = vocabulary)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.tapFirstWord()

        // The entry survived the restart: the panel opens already saved.
        compose.onNodeWithText("Saved").assertIsDisplayed()
        compose.onNodeWithTag(masteryTag(MasteryLevel.MASTERED)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savingAnInflectedFormDoesNotCreateASecondEntryForTheLemma() {
        val vocabulary = inMemoryVocabulary()
        // The pack resolves the page's first word to a lemma, so the two pages'
        // spellings collapse onto one key rather than adding a second entry.
        val lemmas = LemmaLookup { surface, _ -> if (surface == "On") "lantern" else null }
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, vocabulary = vocabulary, lemmas = lemmas)) }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.tapFirstWord()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil { vocabulary.all().isNotEmpty() }
        assertEquals("the save must key on the lemma", "lantern", vocabulary.all().single().key.key)

        compose.onNodeWithText("Next page").performClick()
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
                mastery = MasteryLevel.MASTERED,
            ),
        )
        compose.setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        compose.onNodeWithText("Vocabulary").performClick()

        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("lanterne").assertIsDisplayed()
        compose.onNodeWithText("The lantern burned all night.").assertIsDisplayed()
        compose.onNodeWithTag(vocabularyMasteryTag(WordKey("en", "lantern"))).assertTextEquals("Mastered")
    }

    @Test
    fun theVocabularyCanBeSearched() {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.MASTERED))
        vocabulary.save(VocabularyEntry(WordKey("en", "harbour"), "harbour", mastery = MasteryLevel.FAMILIAR))
        compose.setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        compose.onNodeWithText("Vocabulary").performClick()
        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("harbour").assertIsDisplayed()

        compose.onNodeWithTag(VOCABULARY_SEARCH_TAG).performTextInput("har")

        compose.onNodeWithText("harbour").assertIsDisplayed()
        compose.onNodeWithText("lantern").assertDoesNotExist()
    }

    @Test
    fun theVocabularyCanBeFilteredByMastery() {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.MASTERED))
        vocabulary.save(VocabularyEntry(WordKey("en", "harbour"), "harbour", mastery = MasteryLevel.FAMILIAR))
        compose.setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        compose.onNodeWithText("Vocabulary").performClick()
        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("harbour").assertIsDisplayed()

        compose.onNodeWithTag(vocabularyFilterTag(MasteryLevel.MASTERED)).performScrollTo().performClick()

        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("harbour").assertDoesNotExist()
    }

    @Test
    fun anEmptyVocabularyShowsTheInstructionalEmptyState() {
        compose.setContent { App(environment(InMemoryLibrary(), vocabulary = inMemoryVocabulary())) }

        compose.onNodeWithText("Vocabulary").performClick()

        compose.onNodeWithText("No words saved yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun deletingAWordDropsItFromTheReader() {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent { App(environment(library, vocabulary = vocabulary)) }

        // Save the first word from the panel, then find it in the list.
        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.tapFirstWord()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil { vocabulary.all().isNotEmpty() }
        val saved = vocabulary.all().single()

        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Vocabulary").performClick()
        compose.onNodeWithTag(vocabularyDeleteTag(saved.key)).performScrollTo().performClick()
        compose.waitUntil { vocabulary.all().isEmpty() }
        compose.onNodeWithText("No words saved yet", substring = true).assertIsDisplayed()

        // Reopening the book, the word is unsaved again — the reader's colour
        // dropped with the entry, so list and text agree.
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.tapFirstWord()

        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Saved").assertDoesNotExist()
    }
}

@Suppress("LongParameterList") // The environment's inputs are independent; a bundle would only hide that.
internal fun environment(
    library: BookLibrary,
    pickFile: (suspend () -> PickedFile?)? = null,
    dictionary: DictionaryServices? = null,
    pronouncer: Pronouncer? = null,
    openUrl: (String) -> Unit = {},
    vaultTransfer: VaultTransfer? = null,
    vocabulary: Vocabulary? = null,
    lemmas: LemmaLookup = LemmaLookup.None,
    secrets: SecretStore? = null,
    llm: LlmServices? = null,
) = AppEnvironment(
    segmenter = WhitespaceTextSegmenter(),
    library = library,
    mastery = MasteryLookup.AllKnown,
    lemmas = lemmas,
    vocabulary = vocabulary,
    pickFile = pickFile,
    dictionary = dictionary,
    pronouncer = pronouncer,
    openUrl = openUrl,
    vaultTransfer = vaultTransfer,
    secrets = secrets,
    llm = llm,
)

/** A vocabulary over an in-memory vault, so a test can inspect and restart it. */
private fun inMemoryVocabulary(): Vocabulary =
    VaultVocabulary(InMemoryVaultStore(), deterministicSeams(), DeviceId("device-a"))

/** Taps the first word link on the page: word links carry no text, unlike the chrome buttons. */
private fun ComposeTestRule.tapFirstWord() {
    // The word layer arrives a frame after the reader opens (the page is
    // paginated and tokenised asynchronously), so wait for it before tapping.
    waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    onAllNodes(hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        .onFirst()
        .performSemanticsAction(SemanticsActions.OnClick)
}

/** Waits until the reader's page bar reports page [page], then asserts it is there. */
private fun ComposeTestRule.awaitPage(page: Int) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithText("Page $page of", substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Page $page of", substring = true).assertExists()
}

/** Long-presses the first word link and drags across the line, selecting a phrase. */
@Suppress("MagicNumber") // The long-press delay and the drag distance are the test's parameters.
private fun ComposeTestRule.selectFirstPhrase() {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    val page = onNodeWithTag(READER_PAGE_TAG).fetchSemanticsNode().boundsInRoot
    val word = onAllNodes(hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
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
    val files = FakeDictionaryPackFiles(derived)
    return DictionaryServices(
        DictionaryPackInstaller(derived, files, files, "https://example.test/pack.sqlite.gz"),
    )
}

/** Enough one-line paragraphs to span several pages at any test viewport. */
private const val PARAGRAPHS = 40

/** A [BookLibrary] held in memory: import, open and reading progress without a vault. */
private class InMemoryLibrary : BookLibrary {

    private var stored: List<Book> = emptyList()
    private val positions = mutableMapOf<String, Int>()

    override fun books(): List<Book> = stored

    override fun import(fileName: String, bytes: ByteArray, onProgress: (ImportProgress) -> Unit): Book {
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

    override fun open(id: String): ReadingSession? {
        val book = stored.firstOrNull { it.id == id } ?: return null
        return ReadingSession(
            book = book,
            text = longText(book),
            position = positions[book.id]?.let { offset -> ReadingPosition(book.id, offset) },
        )
    }

    override fun position(bookId: String): ReadingPosition? = positions[bookId]?.let { ReadingPosition(bookId, it) }

    override fun savePosition(position: ReadingPosition) {
        positions[position.bookId] = position.offset
    }

    /** The last offset written, so a test can wait for the asynchronous save. */
    fun savedOffset(): Int? = positions.values.firstOrNull()

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
