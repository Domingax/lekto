package app.lekto

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.ReadingPosition
import app.lekto.core.book.ReadingSession
import app.lekto.core.dictionary.DictionaryLookup
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.dictionary.dictionaryShortcuts
import app.lekto.core.dictionary.translationShortcut
import app.lekto.core.llm.LlmClient
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmSettingsStore
import app.lekto.core.secret.SecretStore
import app.lekto.core.speech.Pronouncer
import app.lekto.core.speech.SpeechResult
import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.TextSegmenter
import app.lekto.core.text.baseLanguage
import app.lekto.core.vocabulary.Vocabulary
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.dictionary.DictionaryController
import app.lekto.dictionary.DictionaryRelease
import app.lekto.dictionary.DictionaryServices
import app.lekto.dictionary.DictionaryUiState
import app.lekto.dictionary.Pronunciation
import app.lekto.dictionary.VocabularyPanel
import app.lekto.dictionary.WordLookupActions
import app.lekto.dictionary.WordLookupPanel
import app.lekto.library.LibraryActions
import app.lekto.library.LibraryController
import app.lekto.library.LibraryScreen
import app.lekto.library.LibraryUiState
import app.lekto.reader.PhraseSelection
import app.lekto.reader.ReaderActions
import app.lekto.reader.ReaderChapter
import app.lekto.reader.ReaderDocument
import app.lekto.reader.ReaderRenderer
import app.lekto.reader.ReaderScreen
import app.lekto.reader.WordTap
import app.lekto.settings.AttributionScreen
import app.lekto.settings.ProviderController
import app.lekto.settings.ProviderUiState
import app.lekto.settings.SettingsActions
import app.lekto.settings.SettingsScreen
import app.lekto.settings.SettingsUiState
import app.lekto.settings.VaultTransfer
import app.lekto.settings.VaultTransferController
import app.lekto.settings.VaultUiState
import app.lekto.translation.PhraseLookupActions
import app.lekto.translation.PhraseLookupPanel
import app.lekto.vocabulary.VocabularyActions
import app.lekto.vocabulary.VocabularyController
import app.lekto.vocabulary.VocabularyScreen
import app.lekto.vocabulary.VocabularyUiState
import app.lekto.vocabulary.filterByMastery
import app.lekto.vocabulary.searchVocabulary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The pieces a platform entry point supplies to the [App]: the [segmenter], the
 * vault-backed [library], the wording palette's [mastery], the file [pickFile],
 * the dictionary [dictionary] services (issue #18), the [pronouncer] the lookup
 * panel speaks through (issue #21), the [openUrl] the lookup panel's reference
 * shortcuts open in the platform browser (issue #19), the [vaultTransfer] that
 * exports and imports the vault (issue #20), the saved [vocabulary] the reader
 * colours by and the lookup panel saves into (issue #22), the [secrets] store an
 * API key is kept in and never in the vault (issue #24; ADR-0021), the [lemmas]
 * that give each word its identity, and the [dispatcher] blocking work runs on.
 * Bundled so the root composable's signature stays small and grows in one named
 * place.
 */
data class AppEnvironment(
    val segmenter: TextSegmenter,
    val library: BookLibrary,
    val mastery: MasteryLookup = MasteryLookup.AllKnown,
    val lemmas: LemmaLookup = LemmaLookup.None,
    val vocabulary: Vocabulary? = null,
    val pickFile: (suspend () -> PickedFile?)? = null,
    val dictionary: DictionaryServices? = null,
    val pronouncer: Pronouncer? = null,
    val openUrl: (String) -> Unit = {},
    val vaultTransfer: VaultTransfer? = null,
    val secrets: SecretStore? = null,
    val llm: LlmServices? = null,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
)

/**
 * The LLM provider pieces a platform entry point supplies (issue #88;
 * ADR-0022): the app-private [settings] store for the non-secret provider
 * configuration — preset, model, base URL — and the [client] the connection
 * test reaches the provider with. The **API key** is deliberately not here; it
 * lives in [AppEnvironment.secrets].
 */
data class LlmServices(
    val settings: LlmSettingsStore,
    val client: LlmClient,
    /**
     * The presets this platform can reach. Android omits `Ollama`, a local server
     * reachable only from a desktop (ADR-0022), so the picker never offers an
     * endpoint the client cannot call.
     */
    val providers: List<LlmProvider> = LlmProvider.entries,
)

/** Where the app currently is, so one state replaces the reader/settings/attribution flags. */
private sealed interface Destination {
    data object Library : Destination
    data object Settings : Destination
    data object Attribution : Destination
    data object Vocabulary : Destination
    data class Reading(val session: ReadingSession) : Destination
}

/**
 * The application root and the composition root for the reading loop: it shows
 * the library, drives an import through the environment's library, and opens the
 * chosen book in the reader (issue #15). Since issue #18 it also reaches settings,
 * the attribution screen, and an offline word lookup, and since issue #20 the
 * settings screen exports and imports the whole vault.
 *
 * The host supplies the file picker as [AppEnvironment.pickFile], because
 * choosing a file is a platform action. When it is absent the Import button does
 * nothing, so the app still builds and stays usable where no picker is wired.
 *
 * Parsing, opening and downloading are blocking, so they run on
 * [AppEnvironment.dispatcher] rather than the composition thread; it is injected
 * because a hard-coded dispatcher cannot be driven by virtual time
 * (docs/testing.md, "Deterministic seams").
 */
@Composable
fun App(environment: AppEnvironment) {
    val controller = remember(environment.library, environment.dispatcher) {
        LibraryController(environment.library, environment.dispatcher)
    }
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val progress = remember(environment.library, environment.dispatcher, scope) {
        ReadingProgressWriter(scope, environment.dispatcher) { position -> environment.library.savePosition(position) }
    }
    val dictionary = remember(environment.dictionary, environment.dispatcher) {
        environment.dictionary?.let { services -> DictionaryController(services, environment.dispatcher) }
    }
    val dictionaryState = dictionary?.state?.collectAsState()?.value ?: DictionaryUiState()
    val vaultTransfer = remember(environment.vaultTransfer, environment.dispatcher, scope) {
        environment.vaultTransfer?.let { transfer -> VaultTransferController(transfer, environment.dispatcher, scope) }
    }
    val vaultState = vaultTransfer?.state?.collectAsState()?.value ?: VaultUiState()
    val vocabulary = remember(environment.vocabulary) {
        environment.vocabulary?.let { store -> VocabularyController(store) }
    }
    val provider = remember(environment.llm, environment.secrets, environment.dispatcher, scope) {
        val llm = environment.llm
        val secrets = environment.secrets
        if (llm != null && secrets != null) {
            ProviderController(llm.settings, secrets, llm.client, llm.providers, environment.dispatcher, scope)
        } else {
            null
        }
    }
    val providerState = provider?.state?.collectAsState()?.value ?: ProviderUiState()
    val settings = Settings(
        dictionary = dictionary,
        vaultTransfer = vaultTransfer,
        provider = provider,
        state = SettingsUiState(dictionaryState, vaultState, providerState),
    )

    AppScreens(environment, controller, state, scope, progress, settings, vocabulary)
}

/** The app's destinations, so [App] stays a wiring function and each screen is small. */
@Suppress("LongParameterList") // These are the composition's inputs; bundling them adds a type, not clarity.
@Composable
private fun AppScreens(
    environment: AppEnvironment,
    controller: LibraryController,
    libraryState: LibraryUiState,
    scope: CoroutineScope,
    progress: ReadingProgressWriter,
    settings: Settings,
    vocabulary: VocabularyController?,
) {
    var destination by remember { mutableStateOf<Destination>(Destination.Library) }

    MaterialTheme {
        when (val current = destination) {
            is Destination.Reading -> ReaderDestination(
                current.session,
                environment,
                settings.dictionary,
                scope,
                progress,
                vocabulary,
                onBack = { destination = Destination.Library },
            )

            Destination.Attribution -> AttributionScreen(
                metadata = (settings.state.dictionary.status as? DictionaryPackState.Ready)?.metadata,
                onBack = { destination = Destination.Settings },
            )

            Destination.Settings -> SettingsDestination(
                settings,
                onVaultImported = controller::refresh,
                onOpenAttribution = { destination = Destination.Attribution },
                onBack = { destination = Destination.Library },
            )

            Destination.Vocabulary -> VocabularyDestination(vocabulary, environment, scope) {
                destination = Destination.Library
            }

            Destination.Library -> LibraryDestination(
                environment,
                controller,
                scope,
                libraryState,
                onOpenVocabulary = { destination = Destination.Vocabulary },
                onOpenSettings = { destination = Destination.Settings },
                onOpen = { session -> destination = Destination.Reading(session) },
            )
        }
    }
}

/** The word whose lookup panel is open: the tapped word and its offline result. */
private data class WordSelection(val tap: WordTap, val result: WordLookup)

/** The reader destination over an opened session, with the lookup panel it owns. */
@Suppress("LongParameterList") // The reader's inputs are independent; a bundle would only hide that.
@Composable
private fun ReaderDestination(
    session: ReadingSession,
    environment: AppEnvironment,
    dictionary: DictionaryController?,
    scope: CoroutineScope,
    progress: ReadingProgressWriter,
    vocabulary: VocabularyController?,
    onBack: () -> Unit,
) {
    var selection by remember { mutableStateOf<WordSelection?>(null) }
    var phrase by remember { mutableStateOf<PhraseSelection?>(null) }
    var speech by remember { mutableStateOf<SpeechResult?>(null) }
    val speak = speakHandler(environment.pronouncer, scope, environment.dispatcher) { result -> speech = result }
    val mastery = vocabulary?.mastery ?: environment.mastery
    val lookup = Lookup(
        selection = selection,
        phrase = phrase,
        pronunciation = pronunciationFor(selection, speech, speak),
        vocabulary = VocabularyPanel(
            entry = selection?.let { selected -> vocabulary?.entryFor(selected.tap.token.key) },
            onSave = { level -> selection?.let { chosen -> saveWord(vocabulary, scope, environment, chosen, level) } },
        ),
        onWordTap = wordTapHandler(dictionary, scope) { tap, result ->
            phrase = null
            selection = WordSelection(tap, result)
            speech = null
        },
        onPhraseSelected = { chosen ->
            selection = null
            phrase = chosen
        },
        onDismiss = {
            selection = null
            phrase = null
        },
    )
    ReaderSession(
        session = session,
        environment = environment,
        mastery = mastery,
        masteryRevision = vocabulary?.revision ?: 0,
        lookup = lookup,
        onBack = onBack,
        onPositionChange = { offset -> progress.record(ReadingPosition(session.book.id, offset)) },
    )
}

/** The pronunciation control for the selected word, or a no-op when nothing is selected. */
private fun pronunciationFor(
    selection: WordSelection?,
    speech: SpeechResult?,
    speak: (String, String?) -> Unit,
): Pronunciation = Pronunciation(
    onSpeak = { selection?.tap?.token?.let { word -> speak(word.surface, word.key.language) } },
    result = speech,
)

/**
 * Saves the word behind [selection] at [level] (issue #22): the entry carries the
 * token's identity — the lemma when the dictionary knew one — its translation
 * and context sentence, so an inflected form updates its lemma's entry. The vault
 * write is blocking, so it runs on the environment's dispatcher.
 */
@Suppress("LongParameterList") // The save's inputs are the reader's own pieces; a bundle would only hide that.
private fun saveWord(
    vocabulary: VocabularyController?,
    scope: CoroutineScope,
    environment: AppEnvironment,
    selection: WordSelection,
    level: MasteryLevel,
) {
    vocabulary ?: return
    val entry = VocabularyEntry(
        key = selection.tap.token.key,
        surface = selection.tap.token.surface,
        translation = translationOf(selection.result),
        contextSentence = selection.tap.contextSentence,
        mastery = level,
    )
    scope.launch { withContext(environment.dispatcher) { vocabulary.save(entry) } }
}

/** The translation an offline lookup found, or `null` when it found none. */
internal fun translationOf(result: WordLookup): String? = (result as? WordLookup.Found)
    ?.entries
    ?.firstNotNullOfOrNull { entry -> entry.translations.firstOrNull() }
    ?.takeIf { translation -> translation.isNotBlank() }

/**
 * The lookup panel's live state and events, bundled so the reader's signature
 * stays small: the word or phrase whose panel is open, its pronunciation and
 * vocabulary controls, and the taps that open and close it (issues #19, #21, #22
 * and #87).
 */
@Suppress("LongParameterList") // The reader's own pieces, bundled as one value; a second bundle only hides them.
private class Lookup(
    val selection: WordSelection?,
    val phrase: PhraseSelection?,
    val pronunciation: Pronunciation,
    val vocabulary: VocabularyPanel,
    val onWordTap: (WordTap) -> Unit,
    val onPhraseSelected: (PhraseSelection) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The settings screen's live pieces, bundled so [AppScreens] stays within the
 * parameter bound: the two controllers it drives and the state it renders.
 */
private class Settings(
    val dictionary: DictionaryController?,
    val vaultTransfer: VaultTransferController?,
    val provider: ProviderController?,
    val state: SettingsUiState,
)

/** The settings destination wrapped so its action bundle stays out of [AppScreens]. */
@Composable
private fun SettingsDestination(
    settings: Settings,
    onVaultImported: () -> Unit,
    onOpenAttribution: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScreen(
        state = settings.state,
        actions = SettingsActions(
            onDownload = { settings.dictionary?.install() },
            onOpenAttribution = onOpenAttribution,
            onDismissError = { settings.dictionary?.dismissError() },
            onBack = onBack,
            onExportVault = { settings.vaultTransfer?.export() },
            onImportVault = { settings.vaultTransfer?.import(onVaultImported) },
            onDismissVaultMessage = { settings.vaultTransfer?.dismiss() },
            onSelectProvider = { provider -> settings.provider?.selectProvider(provider) },
            onModelChange = { model -> settings.provider?.setModel(model) },
            onBaseUrlChange = { baseUrl -> settings.provider?.setBaseUrl(baseUrl) },
            onSaveProvider = { apiKey -> settings.provider?.save(apiKey) },
            onTestProvider = { apiKey -> settings.provider?.test(apiKey) },
            onRemoveProviderKey = { settings.provider?.removeKey() },
            onDismissProviderResult = { settings.provider?.dismissResult() },
        ),
    )
}

/** The library destination wrapped so its navigation bundle stays out of [AppScreens]. */
@Suppress("LongParameterList") // The composition's inputs; a bundle is [LibraryNavigation] already.
@Composable
private fun LibraryDestination(
    environment: AppEnvironment,
    controller: LibraryController,
    scope: CoroutineScope,
    state: LibraryUiState,
    onOpenVocabulary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpen: (ReadingSession) -> Unit,
) {
    LibraryScreen(
        state = state,
        actions = libraryActions(
            environment,
            controller,
            scope,
            LibraryNavigation(onOpenVocabulary, onOpenSettings, onOpen),
        ),
    )
}

/**
 * The vocabulary list destination (issue #23): the reader's live vocabulary, the
 * search query the user has typed, and the delete that removes an entry from the
 * vault. The query is local to the screen; the delete is a blocking vault write,
 * so it runs on the environment's dispatcher, and the reader recolours through
 * the controller's bumped revision.
 */
@Suppress("LongParameterList") // The composition's inputs; a bundle would only hide that.
@Composable
private fun VocabularyDestination(
    vocabulary: VocabularyController?,
    environment: AppEnvironment,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var masteryLevel by remember { mutableStateOf<MasteryLevel?>(null) }
    val all = vocabulary?.all().orEmpty()
    val state = if (all.isEmpty()) {
        VocabularyUiState.Empty
    } else {
        VocabularyUiState.Results(filterByMastery(searchVocabulary(all, query), masteryLevel), query, masteryLevel)
    }
    VocabularyScreen(
        state = state,
        actions = VocabularyActions(
            onSearch = { text -> query = text },
            onFilter = { chosen -> masteryLevel = chosen },
            onDelete = { entry ->
                scope.launch { withContext(environment.dispatcher) { vocabulary?.delete(entry.key) } }
            },
            onBack = onBack,
        ),
    )
}

/**
 * The tap handler for a word: resolve it with the dictionary on a background
 * dispatcher, or report the offline dictionary unavailable when none is wired.
 * The tapped word is carried back so the panel knows the word it is showing, its
 * context sentence and the reader can keep it highlighted behind the panel
 * (issues #19 and #22).
 */
internal fun wordTapHandler(
    dictionary: DictionaryController?,
    scope: CoroutineScope,
    onLookup: (WordTap, WordLookup) -> Unit,
): (WordTap) -> Unit = { tap ->
    if (dictionary == null) {
        onLookup(tap, WordLookup.Unavailable(DictionaryLookup.NOT_INSTALLED))
    } else {
        scope.launch { onLookup(tap, dictionary.lookUp(tap.token.surface, tap.token.key.language)) }
    }
}

/**
 * The pronunciation handler (issue #21): speak the word off the UI thread — the
 * platform call blocks — and report the honest [SpeechResult] back, or report
 * that no engine is wired. Returned as a function so the reader's action bundle
 * stays a plain value.
 */
internal fun speakHandler(
    pronouncer: Pronouncer?,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    onResult: (SpeechResult) -> Unit,
): (String, String?) -> Unit = { text, language ->
    if (pronouncer == null) {
        onResult(SpeechResult.Unavailable(Pronouncer.NO_ENGINE))
    } else {
        scope.launch { onResult(withContext(dispatcher) { pronouncer.speak(text, language) }) }
    }
}

/** The library's actions: import through the platform picker, open a book, dismiss a failure. */
private fun libraryActions(
    environment: AppEnvironment,
    controller: LibraryController,
    scope: CoroutineScope,
    navigation: LibraryNavigation,
): LibraryActions = LibraryActions(
    onImport = {
        environment.pickFile?.let { pick ->
            scope.launch {
                val file = pick() ?: return@launch
                controller.import(file.name, file.bytes)
            }
        }
    },
    onOpen = { book ->
        scope.launch {
            withContext(environment.dispatcher) { environment.library.open(book.id) }?.let(navigation.onOpen)
        }
    },
    onDismissError = controller::dismissError,
    onOpenSettings = navigation.onOpenSettings,
    onOpenVocabulary = navigation.onOpenVocabulary,
)

/** Where the library's taps lead, bundled so [libraryActions] stays within the parameter bound. */
private class LibraryNavigation(
    val onOpenVocabulary: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpen: (ReadingSession) -> Unit,
)

/** The reader over an opened [session], with a way back to the library and the lookup panel. */
@Suppress("LongParameterList") // The reader's inputs are independent; a bundle would only hide that.
@Composable
private fun ReaderSession(
    session: ReadingSession,
    environment: AppEnvironment,
    mastery: MasteryLookup,
    masteryRevision: Int,
    lookup: Lookup,
    onBack: () -> Unit,
    onPositionChange: (Int) -> Unit,
) {
    // The reader shows one chapter at a time; this build renders a book's blocks
    // as a single chapter and the chrome carries the book's title. It opens at
    // the saved reading position, or the start for a book never opened.
    val chapter = ReaderChapter(
        title = session.book.title,
        language = session.book.language,
        blocks = session.text.blocks,
    )
    Box(Modifier.fillMaxSize()) {
        ReaderScreen(
            document = ReaderDocument(
                chapter = chapter,
                renderer = ReaderRenderer(
                    segmenter = environment.segmenter,
                    mastery = mastery,
                    lemmas = environment.lemmas,
                ),
                initialOffset = session.position?.offset ?: 0,
                selectedRange = lookup.selection?.let { selected ->
                    selected.tap.token.start..selected.tap.token.end
                } ?: lookup.phrase?.range,
                masteryRevision = masteryRevision,
            ),
            actions = ReaderActions(
                onWordTap = lookup.onWordTap,
                onPhraseSelected = lookup.onPhraseSelected,
                onBack = onBack,
                onPositionChange = onPositionChange,
            ),
        )
        LookupOverlays(lookup, session.book.language, environment.openUrl)
    }
}

/** The panel for the open word or phrase, bottom-aligned over the reader. */
@Composable
private fun BoxScope.LookupOverlays(lookup: Lookup, language: String?, openUrl: (String) -> Unit) {
    lookup.selection?.let { selected ->
        LookupPanel(
            selected = selected,
            pronunciation = lookup.pronunciation,
            vocabulary = lookup.vocabulary,
            openUrl = openUrl,
            onDismiss = lookup.onDismiss,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    lookup.phrase?.let { phrase ->
        PhrasePanel(
            phrase = phrase,
            language = language,
            openUrl = openUrl,
            onDismiss = lookup.onDismiss,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** The lookup panel for [selected], with the book's reference shortcuts built for its language. */
@Suppress("LongParameterList") // The panel's inputs are independent; a bundle would only hide that.
@Composable
private fun LookupPanel(
    selected: WordSelection,
    pronunciation: Pronunciation,
    vocabulary: VocabularyPanel,
    openUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val token = selected.tap.token
    val language = token.key.language?.let { code -> baseLanguage(code) }
    val shortcuts = remember(token, language) {
        dictionaryShortcuts(token.surface, language, DictionaryRelease.TARGET_LANGUAGE)
    }
    WordLookupPanel(
        result = selected.result,
        term = token.surface,
        shortcuts = shortcuts,
        actions = WordLookupActions(
            onOpenShortcut = { shortcut -> openUrl(shortcut.url) },
            onDismiss = onDismiss,
        ),
        pronunciation = pronunciation,
        vocabulary = vocabulary,
        modifier = modifier,
    )
}

/**
 * The phrase panel for [phrase] (issue #87): the book's zero-configuration
 * **Translation shortcut**, built from the book's language and the pack's target
 * language. It needs no provider and no key, so the panel always offers it.
 */
@Suppress("LongParameterList") // The panel's inputs are independent; a bundle would only hide that.
@Composable
private fun PhrasePanel(
    phrase: PhraseSelection,
    language: String?,
    openUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shortcut = remember(phrase, language) {
        translationShortcut(
            phrase.text,
            language?.let { code -> baseLanguage(code) },
            DictionaryRelease.TARGET_LANGUAGE,
        )
    }
    PhraseLookupPanel(
        phrase = phrase.text,
        shortcut = shortcut,
        actions = PhraseLookupActions(
            onOpenShortcut = { chosen -> openUrl(chosen.url) },
            onDismiss = onDismiss,
        ),
        modifier = modifier,
    )
}

/** A platform-picked file: its name and bytes, read before it reaches the domain. */
class PickedFile(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PickedFile && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}
