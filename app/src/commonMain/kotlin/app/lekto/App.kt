package app.lekto

import androidx.compose.foundation.layout.Box
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
import app.lekto.core.MasteryLookup
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.ReadingPosition
import app.lekto.core.book.ReadingSession
import app.lekto.core.dictionary.DictionaryLookup
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.dictionary.dictionaryShortcuts
import app.lekto.core.speech.Pronouncer
import app.lekto.core.speech.SpeechResult
import app.lekto.core.text.TextSegmenter
import app.lekto.core.text.WordToken
import app.lekto.core.text.baseLanguage
import app.lekto.dictionary.DictionaryController
import app.lekto.dictionary.DictionaryRelease
import app.lekto.dictionary.DictionaryServices
import app.lekto.dictionary.DictionaryUiState
import app.lekto.dictionary.Pronunciation
import app.lekto.dictionary.WordLookupPanel
import app.lekto.library.LibraryActions
import app.lekto.library.LibraryController
import app.lekto.library.LibraryScreen
import app.lekto.library.LibraryUiState
import app.lekto.reader.ReaderActions
import app.lekto.reader.ReaderChapter
import app.lekto.reader.ReaderDocument
import app.lekto.reader.ReaderRenderer
import app.lekto.reader.ReaderScreen
import app.lekto.settings.AttributionScreen
import app.lekto.settings.SettingsActions
import app.lekto.settings.SettingsScreen
import app.lekto.settings.SettingsUiState
import app.lekto.settings.VaultTransfer
import app.lekto.settings.VaultTransferController
import app.lekto.settings.VaultUiState
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
 * exports and imports the vault (issue #20) and the [dispatcher] blocking work
 * runs on. Bundled so the root composable's signature stays small and grows in
 * one named place.
 */
data class AppEnvironment(
    val segmenter: TextSegmenter,
    val library: BookLibrary,
    val mastery: MasteryLookup,
    val pickFile: (suspend () -> PickedFile?)? = null,
    val dictionary: DictionaryServices? = null,
    val pronouncer: Pronouncer? = null,
    val openUrl: (String) -> Unit = {},
    val vaultTransfer: VaultTransfer? = null,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
)

/** Where the app currently is, so one state replaces the reader/settings/attribution flags. */
private sealed interface Destination {
    data object Library : Destination
    data object Settings : Destination
    data object Attribution : Destination
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

    AppScreens(environment, controller, state, scope, progress, dictionary, dictionaryState, vaultTransfer, vaultState)
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
    dictionary: DictionaryController?,
    dictionaryState: DictionaryUiState,
    vaultTransfer: VaultTransferController?,
    vaultState: VaultUiState,
) {
    var destination by remember { mutableStateOf<Destination>(Destination.Library) }

    MaterialTheme {
        when (val current = destination) {
            is Destination.Reading -> ReaderDestination(
                current.session,
                environment,
                dictionary,
                scope,
                progress,
                onBack = { destination = Destination.Library },
            )

            Destination.Attribution -> AttributionScreen(
                metadata = (dictionaryState.status as? DictionaryPackState.Ready)?.metadata,
                onBack = { destination = Destination.Settings },
            )

            Destination.Settings -> SettingsDestination(
                dictionaryState,
                dictionary,
                vaultState,
                vaultTransfer,
                onVaultImported = controller::refresh,
                onOpenAttribution = { destination = Destination.Attribution },
                onBack = { destination = Destination.Library },
            )

            Destination.Library -> LibraryDestination(
                environment,
                controller,
                scope,
                libraryState,
                onOpenSettings = { destination = Destination.Settings },
                onOpen = { session -> destination = Destination.Reading(session) },
            )
        }
    }
}

/** The word whose lookup panel is open: the tapped token and its offline result. */
private data class WordSelection(val token: WordToken, val result: WordLookup)

/** The reader destination over an opened session, with the lookup panel it owns. */
@Suppress("LongParameterList") // The reader's inputs are independent; a bundle would only hide that.
@Composable
private fun ReaderDestination(
    session: ReadingSession,
    environment: AppEnvironment,
    dictionary: DictionaryController?,
    scope: CoroutineScope,
    progress: ReadingProgressWriter,
    onBack: () -> Unit,
) {
    var selection by remember { mutableStateOf<WordSelection?>(null) }
    var speech by remember { mutableStateOf<SpeechResult?>(null) }
    val speak = speakHandler(environment.pronouncer, scope, environment.dispatcher) { result -> speech = result }
    val lookup = Lookup(
        selection = selection,
        pronunciation = Pronunciation(
            onSpeak = { selection?.let { selected -> speak(selected.token.surface, selected.token.key.language) } },
            result = speech,
        ),
        onWordTap = wordTapHandler(dictionary, scope) { token, result ->
            selection = WordSelection(token, result)
            speech = null
        },
        onDismiss = { selection = null },
    )
    ReaderSession(
        session = session,
        environment = environment,
        lookup = lookup,
        onBack = onBack,
        onPositionChange = { offset -> progress.record(ReadingPosition(session.book.id, offset)) },
    )
}

/**
 * The lookup panel's live state and events, bundled so the reader's signature
 * stays small: the word whose panel is open, its pronunciation control, and the
 * taps that open and close it (issues #19 and #21).
 */
private class Lookup(
    val selection: WordSelection?,
    val pronunciation: Pronunciation,
    val onWordTap: (WordToken) -> Unit,
    val onDismiss: () -> Unit,
)

/** The settings destination wrapped so its action bundle stays out of [AppScreens]. */
@Suppress("LongParameterList") // The settings sections' inputs are independent; the action bundle is [SettingsActions].
@Composable
private fun SettingsDestination(
    dictionaryState: DictionaryUiState,
    dictionary: DictionaryController?,
    vaultState: VaultUiState,
    vaultTransfer: VaultTransferController?,
    onVaultImported: () -> Unit,
    onOpenAttribution: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScreen(
        state = SettingsUiState(dictionaryState, vaultState),
        actions = SettingsActions(
            onDownload = { dictionary?.install() },
            onOpenAttribution = onOpenAttribution,
            onDismissError = { dictionary?.dismissError() },
            onBack = onBack,
            onExportVault = { vaultTransfer?.export() },
            onImportVault = { vaultTransfer?.import(onVaultImported) },
            onDismissVaultMessage = { vaultTransfer?.dismiss() },
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
    onOpenSettings: () -> Unit,
    onOpen: (ReadingSession) -> Unit,
) {
    LibraryScreen(
        state = state,
        actions = libraryActions(environment, controller, scope, LibraryNavigation(onOpenSettings, onOpen)),
    )
}

/**
 * The tap handler for a word: resolve it with the dictionary on a background
 * dispatcher, or report the offline dictionary unavailable when none is wired.
 * The token is carried back so the panel knows the word it is showing and the
 * reader can keep it highlighted behind the panel (issue #19).
 */
internal fun wordTapHandler(
    dictionary: DictionaryController?,
    scope: CoroutineScope,
    onLookup: (WordToken, WordLookup) -> Unit,
): (WordToken) -> Unit = { token ->
    if (dictionary == null) {
        onLookup(token, WordLookup.Unavailable(DictionaryLookup.NOT_INSTALLED))
    } else {
        scope.launch { onLookup(token, dictionary.lookUp(token.surface, token.key.language)) }
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
)

/** Where the library's taps lead, bundled so [libraryActions] stays within the parameter bound. */
private class LibraryNavigation(val onOpenSettings: () -> Unit, val onOpen: (ReadingSession) -> Unit)

/** The reader over an opened [session], with a way back to the library and the lookup panel. */
@Suppress("LongParameterList") // The reader's inputs are independent; a bundle would only hide that.
@Composable
private fun ReaderSession(
    session: ReadingSession,
    environment: AppEnvironment,
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
                renderer = ReaderRenderer(segmenter = environment.segmenter, mastery = environment.mastery),
                initialOffset = session.position?.offset ?: 0,
                selectedRange = lookup.selection?.let { selected -> selected.token.start..selected.token.end },
            ),
            actions = ReaderActions(
                onWordTap = lookup.onWordTap,
                onBack = onBack,
                onPositionChange = onPositionChange,
            ),
        )
        lookup.selection?.let { selected ->
            LookupPanel(
                selected = selected,
                pronunciation = lookup.pronunciation,
                openUrl = environment.openUrl,
                onDismiss = lookup.onDismiss,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** The lookup panel for [selected], with the book's reference shortcuts built for its language. */
@Suppress("LongParameterList") // The panel's inputs are independent; a bundle would only hide that.
@Composable
private fun LookupPanel(
    selected: WordSelection,
    pronunciation: Pronunciation,
    openUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = selected.token.key.language?.let { code -> baseLanguage(code) }
    val shortcuts = remember(selected.token, language) {
        dictionaryShortcuts(selected.token.surface, language, DictionaryRelease.TARGET_LANGUAGE)
    }
    WordLookupPanel(
        result = selected.result,
        term = selected.token.surface,
        shortcuts = shortcuts,
        onOpenShortcut = { shortcut -> openUrl(shortcut.url) },
        onDismiss = onDismiss,
        pronunciation = pronunciation,
        modifier = modifier,
    )
}

/** A platform-picked file: its name and bytes, read before it reaches the domain. */
class PickedFile(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PickedFile && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}
