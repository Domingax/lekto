package app.lekto

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.ImportProgress
import app.lekto.core.book.ReadingPosition
import app.lekto.core.book.ReadingSession
import app.lekto.core.dictionary.DictionaryPackInstaller
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
import app.lekto.settings.VaultTransfer
import app.lekto.testkit.FakeDictionaryPackFiles
import app.lekto.testkit.FakePronouncer
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.WhitespaceTextSegmenter
import app.lekto.testkit.deterministicSeams
import app.lekto.translation.TRANSLATION_SHORTCUT_TAG
import app.lekto.vocabulary.VOCABULARY_SEARCH_TAG
import app.lekto.vocabulary.vocabularyDeleteTag
import app.lekto.vocabulary.vocabularyFilterTag
import app.lekto.vocabulary.vocabularyMasteryTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The application's reading-loop flow through semantics (issues #15 and #16): it
 * opens on the library, an import lands a book, tapping that book starts a
 * reading session over its parsed text, and leaving and reopening the book
 * resumes at the page the reader left.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One flow, one test per step; splitting the class hides the whole path.
class AppSemanticsTest {

    @Test
    fun opensOnTheLibraryAndImportsABook() = runComposeUiTest {
        val library = InMemoryLibrary()
        setContent { App(environment(library, pickFile = { PickedFile("lantern.epub", byteArrayOf(1)) })) }

        onNodeWithText("No books yet", substring = true).assertIsDisplayed()
        onNodeWithText("Import").performClick()

        onNodeWithText("The Lantern Keeper").assertIsDisplayed()
    }

    @Test
    fun openingABookStartsAReadingSession() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        onNodeWithText("The Lantern Keeper").performClick()

        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun reopeningABookResumesWhereTheReaderLeftOff() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        onNodeWithText("The Lantern Keeper").performClick()
        onNodeWithText("Next page").performClick()
        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()
        // Persisting runs off the UI thread; wait for it before leaving the book.
        waitUntil { library.savedOffset() != null }

        onNodeWithText("Library").performClick()
        onNodeWithText("The Lantern Keeper").performClick()

        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingAWordOpensTheLookupPanelWithTheShortcuts() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        onNodeWithText("The Lantern Keeper").performClick()
        tapFirstWord()

        onNodeWithText("Look it up online").assertIsDisplayed()
        // No pack is wired, so the panel degrades to the honest message and the
        // reference shortcuts (issue #19).
        onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        onNodeWithText("Reverso").assertIsDisplayed()
    }

    @Test
    fun aReferenceShortcutOpensTheCanonicalPageInTheBrowser() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        var opened: String? = null
        setContent { App(environment(library, openUrl = { url -> opened = url })) }

        onNodeWithText("The Lantern Keeper").performClick()
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
        setContent { App(environment(library)) }

        onNodeWithText("The Lantern Keeper").performClick()
        onNodeWithText("Next page").performClick()
        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()

        tapFirstWord()
        onNodeWithText("Look it up online").assertIsDisplayed()
        // The reader stays put behind the panel, at the same page.
        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()

        onNodeWithText("Close").performClick()

        onNodeWithText("Look it up online").assertDoesNotExist()
        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()
    }

    @Test
    fun selectingAPhraseOffersTheTranslationShortcutWithNoProviderConfigured() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        var opened: String? = null
        setContent { App(environment(library, openUrl = { url -> opened = url })) }

        onNodeWithText("The Lantern Keeper").performClick()
        onNodeWithText("Next page").performClick()
        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()

        // Long-press and drag across the page's words to select a phrase.
        selectFirstPhrase()

        // No provider and no key are configured: the shortcut is the whole offer.
        onNodeWithText("Translate online").assertIsDisplayed()
        // The reader stays put behind the panel, at the same page.
        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()
        onNodeWithTag(TRANSLATION_SHORTCUT_TAG).performClick()
        assertTrue(
            opened.orEmpty().startsWith("https://translate.google.com/"),
            "the shortcut must open the translation service's prefilled page: $opened",
        )

        onNodeWithText("Close").performClick()
        onNodeWithText("Translate online").assertDoesNotExist()
        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()
    }

    @Test
    fun listeningToAWordSpeaksItThroughThePlatform() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val pronouncer = FakePronouncer()
        setContent { App(environment(library, pronouncer = pronouncer)) }

        onNodeWithText("The Lantern Keeper").performClick()
        tapFirstWord()
        onNodeWithText("Listen").performClick()

        waitUntil { pronouncer.utterances.isNotEmpty() }
        val (text, language) = pronouncer.utterances.single()
        assertTrue(text.isNotBlank(), "the tapped word must be spoken")
        assertEquals("en", language)
    }

    @Test
    fun aLanguageWithNoVoiceSaysSoRatherThanFailing() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        val pronouncer = FakePronouncer(SpeechResult.NoVoice("en"))
        setContent { App(environment(library, pronouncer = pronouncer)) }

        onNodeWithText("The Lantern Keeper").performClick()
        tapFirstWord()
        onNodeWithText("Listen").performClick()

        waitUntil { pronouncer.utterances.isNotEmpty() }
        waitForIdle()
        onNodeWithText("No voice is installed", substring = true).assertIsDisplayed()
    }

    @Test
    fun speakingWithNoEngineWiredSaysSo() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        onNodeWithText("The Lantern Keeper").performClick()
        tapFirstWord()
        onNodeWithText("Listen").performClick()

        onNodeWithText("No speech engine is available", substring = true).assertIsDisplayed()
    }

    @Test
    fun reachesSettingsAndAttributionFromTheLibrary() = runComposeUiTest {
        setContent { App(environment(InMemoryLibrary(), dictionary = dictionaryServices())) }

        onNodeWithText("Settings").performClick()
        onNodeWithText("Dictionary").assertIsDisplayed()

        onNodeWithText("Attribution").performClick()

        onNodeWithText("CC BY-SA 4.0", substring = true).assertIsDisplayed()
    }

    @Test
    fun settingsShowAnInstalledDictionaryAndItsAttribution() = runComposeUiTest {
        val installed = dictionaryServices().also { services -> services.installer.install() }
        setContent { App(environment(InMemoryLibrary(), dictionary = installed)) }

        onNodeWithText("Settings").performClick()
        onNodeWithText("is installed", substring = true).assertIsDisplayed()

        onNodeWithText("Attribution").performClick()
        onNodeWithText("Wiktionary contributors").assertIsDisplayed()
    }

    @Test
    fun savingAWordFromThePanelStoresItWithItsContextAndLevel() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, vocabulary = vocabulary)) }

        onNodeWithText("The Lantern Keeper").performClick()
        tapFirstWord()
        onNodeWithText("Save").performClick()

        waitUntil { vocabulary.all().isNotEmpty() }
        val entry = vocabulary.all().single()
        assertEquals("on", entry.key.key)
        assertTrue(
            entry.contextSentence.orEmpty().contains("On"),
            "the context sentence is kept: ${entry.contextSentence}",
        )
        assertEquals(MasteryLevel.FAMILIAR, entry.mastery)
        // No toast, no dialog: the saved state on the panel is the confirmation.
        waitUntil { onAllNodesWithText("Saved").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Saved").assertIsDisplayed()
    }

    @Test
    fun theMasteryLevelCanBeChangedAfterSaving() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, vocabulary = vocabulary)) }

        onNodeWithText("The Lantern Keeper").performClick()
        tapFirstWord()
        onNodeWithText("Save").performClick()
        waitUntil { vocabulary.all().isNotEmpty() }

        onNodeWithTag(masteryTag(MasteryLevel.MASTERED)).performClick()

        waitUntil { vocabulary.all().single().mastery == MasteryLevel.MASTERED }
        assertEquals(1, vocabulary.all().size, "changing the level must not add a second entry")
    }

    @Test
    fun aWordSavedInAnEarlierSessionIsShownAsSaved() = runComposeUiTest {
        val vault = InMemoryVaultStore()
        val vocabulary = VaultVocabulary(vault, deterministicSeams(), DeviceId("device-a"))
        vocabulary.save(VocabularyEntry(WordKey("en", "on"), "On", mastery = MasteryLevel.MASTERED))
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, vocabulary = vocabulary)) }

        onNodeWithText("The Lantern Keeper").performClick()
        tapFirstWord()

        // The entry survived the restart: the panel opens already saved.
        onNodeWithText("Saved").assertIsDisplayed()
        onNodeWithTag(masteryTag(MasteryLevel.MASTERED)).assertIsDisplayed()
    }

    @Test
    fun savingAnInflectedFormDoesNotCreateASecondEntryForTheLemma() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        // The pack knows "lanterns" resolves to "lantern", so the two spellings
        // share one key.
        val lemmas = LemmaLookup { surface, _ -> if (surface == "lanterns") "lantern" else null }
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, vocabulary = vocabulary, lemmas = lemmas)) }

        onNodeWithText("The Lantern Keeper").performClick()
        tapFirstWord()
        onNodeWithText("Save").performClick()
        waitUntil { vocabulary.all().isNotEmpty() }

        onNodeWithText("Next page").performClick()
        tapFirstWord()
        // The same word (page two starts with "On.", so its key is "on"), so its
        // entry updates in place rather than adding a second one.
        onNodeWithText("Saved").assertIsDisplayed()
        onNodeWithText("Save").assertDoesNotExist()
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
                mastery = MasteryLevel.MASTERED,
            ),
        )
        setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        onNodeWithText("Vocabulary").performClick()

        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("lanterne").assertIsDisplayed()
        onNodeWithText("The lantern burned all night.").assertIsDisplayed()
        onNodeWithTag(vocabularyMasteryTag(WordKey("en", "lantern"))).assertTextEquals("Mastered")
    }

    @Test
    fun theVocabularyCanBeSearched() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.MASTERED))
        vocabulary.save(VocabularyEntry(WordKey("en", "harbour"), "harbour", mastery = MasteryLevel.FAMILIAR))
        setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        onNodeWithText("Vocabulary").performClick()
        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("harbour").assertIsDisplayed()

        onNodeWithTag(VOCABULARY_SEARCH_TAG).performTextInput("har")

        onNodeWithText("harbour").assertIsDisplayed()
        onNodeWithText("lantern").assertDoesNotExist()
    }

    @Test
    fun theVocabularyCanBeFilteredByMastery() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.MASTERED))
        vocabulary.save(VocabularyEntry(WordKey("en", "harbour"), "harbour", mastery = MasteryLevel.FAMILIAR))
        setContent { App(environment(InMemoryLibrary(), vocabulary = vocabulary)) }

        onNodeWithText("Vocabulary").performClick()
        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("harbour").assertIsDisplayed()

        onNodeWithTag(vocabularyFilterTag(MasteryLevel.MASTERED)).performScrollTo().performClick()

        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("harbour").assertDoesNotExist()
    }

    @Test
    fun anEmptyVocabularyShowsTheInstructionalEmptyState() = runComposeUiTest {
        setContent { App(environment(InMemoryLibrary(), vocabulary = inMemoryVocabulary())) }

        onNodeWithText("Vocabulary").performClick()

        onNodeWithText("No words saved yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun deletingAWordDropsItFromTheReader() = runComposeUiTest {
        val vocabulary = inMemoryVocabulary()
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library, vocabulary = vocabulary)) }

        // Save the first word from the panel, then find it in the list.
        onNodeWithText("The Lantern Keeper").performClick()
        tapFirstWord()
        onNodeWithText("Save").performClick()
        waitUntil { vocabulary.all().isNotEmpty() }
        val saved = vocabulary.all().single()

        onNodeWithText("Library").performClick()
        onNodeWithText("Vocabulary").performClick()
        onNodeWithTag(vocabularyDeleteTag(saved.key)).performClick()
        waitUntil { vocabulary.all().isEmpty() }
        onNodeWithText("No words saved yet", substring = true).assertIsDisplayed()

        // Reopening the book, the word is unsaved again — the reader's colour
        // dropped with the entry, so list and text agree.
        onNodeWithText("Library").performClick()
        onNodeWithText("The Lantern Keeper").performClick()
        tapFirstWord()

        onNodeWithText("Save").assertIsDisplayed()
        onNodeWithText("Saved").assertDoesNotExist()
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
)

/** A vocabulary over an in-memory vault, so a test can inspect and restart it. */
private fun inMemoryVocabulary(): Vocabulary =
    VaultVocabulary(InMemoryVaultStore(), deterministicSeams(), DeviceId("device-a"))

/** Taps the first word link on the page: word links carry no text, unlike the chrome buttons. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.tapFirstWord() {
    onAllNodes(hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        .onFirst()
        .performClick()
}

/** Long-presses the first word link and drags across the line, selecting a phrase. */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // The long-press delay and the drag distance are the test's parameters.
private fun ComposeUiTest.selectFirstPhrase() {
    val page = onNodeWithTag(READER_PAGE_TAG).fetchSemanticsNode().boundsInRoot
    val word = onAllNodes(hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
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
    val files = FakeDictionaryPackFiles(derived)
    return DictionaryServices(
        DictionaryPackInstaller(derived, files, files, "https://example.test/pack.sqlite.gz"),
    )
}

/** Enough paragraphs to span several pages at any test viewport. */
private const val PARAGRAPHS = 8

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

    /** Enough paragraphs to span several pages at any test viewport. */
    private fun longText(book: Book): StructuredText = StructuredText(
        title = book.title,
        language = book.language,
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
