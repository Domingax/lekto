package app.lekto

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import app.lekto.core.dictionary.firstGloss
import app.lekto.core.dictionary.translationShortcut
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.language.AppLanguages
import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.LanguageCatalogState
import app.lekto.core.language.LanguageSettingsStore
import app.lekto.core.llm.LlmClient
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmSettingsStore
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.reading.ReadingPreferencesStore
import app.lekto.core.readinglevel.ReaderLevels
import app.lekto.core.secret.SecretStore
import app.lekto.core.speech.Pronouncer
import app.lekto.core.speech.SpeechResult
import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.TextSegmenter
import app.lekto.core.text.WordKey
import app.lekto.core.text.baseLanguage
import app.lekto.core.vocabulary.Vocabulary
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.dictionary.DictionaryController
import app.lekto.dictionary.DictionaryServices
import app.lekto.dictionary.DictionaryUiState
import app.lekto.dictionary.Pronunciation
import app.lekto.dictionary.VocabularyPanel
import app.lekto.dictionary.WordLookupActions
import app.lekto.dictionary.WordLookupPanel
import app.lekto.difficulty.DifficultyController
import app.lekto.difficulty.DifficultyServices
import app.lekto.difficulty.DifficultyUiState
import app.lekto.language.LanguageController
import app.lekto.language.LanguageSettingsController
import app.lekto.language.LanguagesDestination
import app.lekto.library.LibraryActions
import app.lekto.library.LibraryController
import app.lekto.library.LibraryScreen
import app.lekto.library.LibraryUiState
import app.lekto.reader.MasteryController
import app.lekto.reader.PhraseSelection
import app.lekto.reader.ReaderActions
import app.lekto.reader.ReaderChapter
import app.lekto.reader.ReaderDocument
import app.lekto.reader.ReaderRenderer
import app.lekto.reader.ReaderScreen
import app.lekto.reader.ReadingPreferencesController
import app.lekto.reader.WordTap
import app.lekto.settings.AttributionScreen
import app.lekto.settings.ProviderController
import app.lekto.settings.ProviderUiState
import app.lekto.settings.SettingsActions
import app.lekto.settings.SettingsScreen
import app.lekto.settings.SettingsUiState
import app.lekto.settings.SyncController
import app.lekto.settings.SyncServices
import app.lekto.settings.SyncUiState
import app.lekto.settings.VaultTransfer
import app.lekto.settings.VaultTransferController
import app.lekto.settings.VaultUiState
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import app.lekto.translation.PhraseLookupActions
import app.lekto.translation.PhraseLookupPanel
import app.lekto.translation.PhraseTranslationUiState
import app.lekto.translation.rememberTranslation
import app.lekto.vocabulary.VocabularyActions
import app.lekto.vocabulary.VocabularyController
import app.lekto.vocabulary.VocabularyScreen
import app.lekto.vocabulary.VocabularyUiState
import app.lekto.vocabulary.filterByMastery
import app.lekto.vocabulary.packTranslations
import app.lekto.vocabulary.searchVocabulary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The pieces a platform entry point supplies to the [App]: the [segmenter], the
 * vault-backed [library], the wording palette's [mastery], the file [pickFile],
 * the dictionary [dictionary] services (issue #18), the [pronouncer] the lookup
 * panel speaks through (issue #21), the [openUrl] the lookup panel's reference
 * shortcuts open in the platform browser (issue #19), the [vaultTransfer] that
 * exports and imports the vault (issue #20), the saved [vocabulary] the reader
 * colours by and the lookup panel saves into (issue #22), the [readerLevels] the
 * reader declares per language and the [difficulty] table its words are banded by
 * (issue #64), the [secrets] store an
 * API key is kept in and never in the vault (issue #24; ADR-0021), the [lemmas]
 * that give each word its identity, the [sync] services that reconcile the vault
 * with a WebDAV server (issue #28), and the [dispatcher] blocking work runs on.
 * Bundled so the root composable's signature stays small and grows in one named
 * place.
 */
data class AppEnvironment(
    val segmenter: TextSegmenter,
    val library: BookLibrary,
    val mastery: MasteryLookup = MasteryLookup.AllKnown,
    val lemmas: LemmaLookup = LemmaLookup.None,
    val vocabulary: Vocabulary? = null,
    val readerLevels: ReaderLevels? = null,
    /**
     * The app-private language settings store (ADR-0034): the device-local
     * **app language** and **Translation language**, neither a **Vault** record.
     * `null` when the platform wires none, so the app behaves as "no pack" and
     * stays honest.
     */
    val languageSettings: LanguageSettingsStore? = null,
    /**
     * The app-private reading preference store (ADR-0035): the device-local
     * face, size and margin a book's text renders with, never a **Vault** record.
     * `null` when the platform wires none, so the reader renders the defaults.
     */
    val readingPreferences: ReadingPreferencesStore? = null,
    /**
     * The language the platform reports for this device (ADR-0034): the first-launch
     * suggestion for the **app language**. `null` when the platform reports none —
     * a case that defaults to English, like any language the app does not ship.
     */
    val systemLanguage: String? = null,
    val pickFile: (suspend () -> PickedFile?)? = null,
    val dictionary: DictionaryServices? = null,
    val difficulty: DifficultyServices? = null,
    /**
     * The published **Language manifest** the **Languages** surface lists the
     * offered **Reading language**s from, fetched and cached as a **Derived
     * asset** (issue #146). `null` when the platform wires none, so the surface
     * stays reachable and honest.
     */
    val languages: LanguageCatalog? = null,
    val pronouncer: Pronouncer? = null,
    val openUrl: (String) -> Unit = {},
    val vaultTransfer: VaultTransfer? = null,
    val secrets: SecretStore? = null,
    val llm: LlmServices? = null,
    val sync: SyncServices? = null,
    /**
     * A signal the app returned to the foreground (issue #121): each emission asks
     * sync to run if it is on. Desktop raises it on window focus; a platform with
     * no lifecycle leaves it empty, and the bounded periodic run still applies.
     */
    val onResume: Flow<Unit> = emptyFlow(),
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
    data object Vocabulary : Destination

    /**
     * The attribution screen, reachable from the **Languages** surface (issue
     * #149). It carries where to return to, so leaving lands back on the surface
     * that offered the data.
     */
    data class Attribution(val returnTo: Destination) : Destination

    /**
     * The **Languages** surface (issue #146). It is reached from the library and
     * from Settings, so it carries where to return to rather than hard-coding
     * one — the navigation rework will replace this with a real shell.
     */
    data class Languages(val returnTo: Destination) : Destination
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
 *
 * [windowInsets] is the space the window's system UI occupies — the status and
 * navigation bars and any display cutout on Android — and it is a parameter
 * rather than a direct read of [WindowInsets.safeDrawing] for the same reason:
 * it is a deterministic seam a test drives with a known inset, which on Android
 * a host test cannot otherwise supply. It defaults to [WindowInsets.safeDrawing]
 * so a platform entry point passes nothing.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // The composition root wires each independent seam once.
@Composable
fun App(
    environment: AppEnvironment,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    // The one signal the local writers raise and sync subscribes to (issue #121):
    // a book imported, a word saved or deleted, a reading position moved.
    val changes = remember { LocalChanges() }
    val controller = remember(environment.library, environment.dispatcher, changes) {
        LibraryController(environment.library, changes, environment.dispatcher)
    }
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val progress = remember(environment.library, environment.dispatcher, scope, changes) {
        ReadingProgressWriter(scope, environment.dispatcher, changes) { position ->
            environment.library.savePosition(position)
        }
    }
    val dictionary = remember(environment.dictionary, environment.dispatcher) {
        environment.dictionary?.let { services -> DictionaryController(services, environment.dispatcher) }
    }
    val dictionaryStates = dictionary?.states?.collectAsState()?.value.orEmpty()
    val vaultTransfer = remember(environment.vaultTransfer, environment.dispatcher, scope) {
        environment.vaultTransfer?.let { transfer -> VaultTransferController(transfer, environment.dispatcher, scope) }
    }
    val vaultState = vaultTransfer?.state?.collectAsState()?.value ?: VaultUiState()
    val vocabulary = remember(environment.vocabulary, changes) {
        environment.vocabulary?.let { store -> VocabularyController(store, changes) }
    }
    // The reader's single mastery composition (ADR-0030): saved vocabulary, the
    // reader level and the difficulty table, with one revision the reader keys
    // its word layer on. The difficulty accessor is a function because the table
    // is installed at runtime.
    val mastery = remember(vocabulary, environment.readerLevels, environment.difficulty, changes) {
        vocabulary?.let { store ->
            MasteryController(
                vocabulary = store,
                levels = environment.readerLevels ?: ReaderLevels.None,
                difficulty = { language -> environment.difficulty?.table(language) },
                changes = changes,
            )
        }
    }
    val difficulty = remember(environment.difficulty, environment.dispatcher) {
        environment.difficulty?.let { services -> DifficultyController(services, environment.dispatcher) }
    }
    val difficultyStates = difficulty?.states?.collectAsState()?.value.orEmpty()
    // Inspect the cached manifest's languages at once — without fetching — so a
    // table or a pack already installed on this device is reported without the
    // reader having to open the Languages surface first (issues #148, #149).
    LaunchedEffect(difficulty, environment.languages) {
        val cached = (environment.languages?.status() as? LanguageCatalogState.Available)?.languages
        difficulty?.refresh(cached.orEmpty().map { offered -> offered.language })
    }
    LaunchedEffect(dictionary, environment.languages) {
        val cached = (environment.languages?.status() as? LanguageCatalogState.Available)?.languages
        dictionary?.refresh(cached.orEmpty())
    }
    // The **Languages** surface's catalogue (issue #146): the Language manifest
    // cached as a derived asset, its offered languages ordered by the books the
    // library currently holds.
    val language = remember(environment.languages, environment.dispatcher) {
        environment.languages?.let { catalog -> LanguageController(catalog, { state.books }, environment.dispatcher) }
    }
    // The reader's language settings (ADR-0034): the app language, the language of
    // the app's texts, and the translation language every lookup, lemma index and
    // phrase prompt resolves into. Both are device-local; the first launch defaults
    // them, with no question.
    val languageSettings = remember(environment.languageSettings) {
        environment.languageSettings?.let { store -> LanguageSettingsController(store) }
    }
    LaunchedEffect(languageSettings) { languageSettings?.applyAppLanguageDefault(environment.systemLanguage) }
    // The reader's reading preference (ADR-0035): the device-local face, size and
    // margin the reader's own **Aa** panel changes, read once and observed as state.
    val readingPreferences = remember(environment.readingPreferences) {
        environment.readingPreferences?.let { store -> ReadingPreferencesController(store) }
    }
    // The translation-language default needs the manifest, so it is filled from the
    // cached catalogue at launch and again when the Languages surface reads it; a
    // device whose language a pack serves needs no choice.
    LaunchedEffect(languageSettings, environment.languages) {
        val cached = (environment.languages?.status() as? LanguageCatalogState.Available)?.languages
        languageSettings?.applyTranslationLanguageDefault(environment.systemLanguage, cached.orEmpty())
    }
    // The lemma index resolves against the chosen **Translation language** — the
    // language a pack translates into — so a word's identity follows the pack that
    // translates it (ADR-0006); memoised on the choice so the reader's word layer is
    // rebuilt only when it moves. The unwired `environment.lemmas` is the fallback
    // for a build or a test that wires no dictionary.
    val lemmas = remember(environment.dictionary, environment.lemmas, languageSettings?.translationLanguage) {
        environment.dictionary?.lemmas(languageSettings?.translationLanguage) ?: environment.lemmas
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
    val sync = rememberSyncController(environment, scope, changes, onSynced = {
        // Reload what the run moved, so a book, a word, a level or a mastery
        // change from another device shows without relaunching the app
        // (issues #116, #132).
        controller.refresh()
        scope.launch {
            withContext(environment.dispatcher) {
                mastery?.refresh() ?: vocabulary?.refresh()
            }
        }
    })
    val syncState = sync?.state?.collectAsState()?.value ?: SyncUiState()
    // Automatic sync (issue #121; ADR-0028): arm it once the app is composed —
    // an app-start run plus the periodic timer — and ask for a run whenever the
    // platform reports the app has resumed.
    LaunchedEffect(sync, environment.onResume) {
        sync?.start()
        environment.onResume.collect { sync?.onResumed() }
    }
    val settings = Settings(
        dictionary = dictionary,
        mastery = mastery,
        vaultTransfer = vaultTransfer,
        provider = provider,
        sync = sync,
        languageSettings = languageSettings,
        state = SettingsUiState(
            vault = vaultState,
            provider = providerState,
            sync = syncState,
            appLanguage = languageSettings?.appLanguage ?: AppLanguages.ENGLISH,
        ),
    )

    // The window's system UI — the status and navigation bars and any display
    // cutout — is kept out of every screen. An app targeting Android 15+ draws
    // edge to edge whether it asks to or not, so without this the chrome sits
    // under the status bar and the system, which owns that strip, eats its taps.
    Box(Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
        CompositionLocalProvider(LocalAppTexts provides appTextsFor(languageSettings?.appLanguage)) {
            AppScreens(
                environment,
                controller,
                state,
                scope,
                progress,
                settings,
                vocabulary,
                mastery,
                language,
                languageSettings,
                readingPreferences,
                lemmas,
                difficulty,
                difficultyStates,
                dictionaryStates,
            )
        }
    }
}

/** The app's destinations, so [App] stays a wiring function and each screen is small. */
@Suppress("LongParameterList", "LongMethod") // The destinations' inputs, and the `when` that maps each to its screen.
@Composable
private fun AppScreens(
    environment: AppEnvironment,
    controller: LibraryController,
    libraryState: LibraryUiState,
    scope: CoroutineScope,
    progress: ReadingProgressWriter,
    settings: Settings,
    vocabulary: VocabularyController?,
    mastery: MasteryController?,
    language: LanguageController?,
    languageSettings: LanguageSettingsController?,
    readingPreferences: ReadingPreferencesController?,
    lemmas: LemmaLookup,
    difficulty: DifficultyController?,
    difficultyStates: Map<String, DifficultyUiState>,
    dictionaryStates: Map<String, DictionaryUiState>,
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
                mastery,
                languageSettings,
                readingPreferences,
                lemmas,
                onBack = { destination = Destination.Library },
                onOpenSettings = { destination = Destination.Settings },
            )

            is Destination.Attribution -> AttributionScreen(
                metadata = dictionaryStates.values
                    .firstNotNullOfOrNull { state -> (state.status as? DictionaryPackState.Ready)?.metadata },
                difficulty = difficultyStates.toSortedMap().values.firstNotNullOfOrNull { state ->
                    (state.status as? DifficultyState.Ready)?.metadata
                },
                onBack = { destination = current.returnTo },
            )

            Destination.Settings -> SettingsDestination(
                settings,
                onVaultImported = controller::refresh,
                onOpenLanguages = { destination = Destination.Languages(Destination.Settings) },
                onBack = { destination = Destination.Library },
            )

            is Destination.Languages -> LanguagesDestination(
                language = language,
                difficulty = difficulty,
                dictionary = settings.dictionary,
                mastery = mastery,
                languageSettings = languageSettings,
                systemLanguage = environment.systemLanguage,
                onOpenAttribution = { destination = Destination.Attribution(current) },
                onBack = { destination = current.returnTo },
            )

            Destination.Vocabulary -> VocabularyDestination(vocabulary, environment, languageSettings, scope) {
                destination = Destination.Library
            }

            Destination.Library -> LibraryDestination(
                environment,
                controller,
                scope,
                libraryState,
                onOpenVocabulary = { destination = Destination.Vocabulary },
                onOpenSettings = { destination = Destination.Settings },
                onOpenLanguages = { destination = Destination.Languages(Destination.Library) },
                onOpen = { session -> destination = Destination.Reading(session) },
            )
        }
    }
}

/** The word whose lookup panel is open: the tapped word and its offline result. */
private data class WordSelection(val tap: WordTap, val result: WordLookup)

/** The reader destination over an opened session, with the lookup panel it owns. */
@Suppress("LongParameterList", "LongMethod") // The reader wires its independent pieces; splitting scatters them.
@Composable
private fun ReaderDestination(
    session: ReadingSession,
    environment: AppEnvironment,
    dictionary: DictionaryController?,
    scope: CoroutineScope,
    progress: ReadingProgressWriter,
    mastery: MasteryController?,
    languageSettings: LanguageSettingsController?,
    readingPreferences: ReadingPreferencesController?,
    lemmas: LemmaLookup,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var selection by remember { mutableStateOf<WordSelection?>(null) }
    var phrase by remember { mutableStateOf<PhraseSelection?>(null) }
    var speech by remember { mutableStateOf<SpeechResult?>(null) }
    val speak = speakHandler(environment.pronouncer, scope, environment.dispatcher) { result -> speech = result }
    val wordMastery = mastery?.mastery ?: environment.mastery
    val translation = rememberTranslation(environment, scope, languageSettings)
    val chosenLanguage = languageSettings?.translationLanguage
    val lookup = Lookup(
        selection = selection,
        phrase = phrase,
        pronunciation = pronunciationFor(selection, speech, speak),
        vocabulary = VocabularyPanel(
            entry = selection?.let { selected -> mastery?.entryFor(selected.tap.token.key) },
            onSave = { level -> selection?.let { chosen -> saveWord(mastery, scope, environment, chosen, level) } },
            defaultLevel = selection?.let { selected -> mastery?.freshSaveLevel(selected.tap.token.key) }
                ?: MasteryLevel.RECOGNIZED,
        ),
        translation = translation.state,
        translationLanguage = chosenLanguage,
        onOpenSettings = onOpenSettings,
        onWordTap = wordTapHandler(dictionary, scope, chosenLanguage) { tap, result ->
            phrase = null
            translation.dismiss()
            selection = WordSelection(tap, result)
            speech = null
        },
        onPhraseSelected = { chosen ->
            selection = null
            phrase = chosen
            translation.start(chosen)
        },
        onDismiss = {
            selection = null
            phrase = null
            translation.dismiss()
        },
    )
    ReaderSession(
        session = session,
        environment = environment,
        mastery = wordMastery,
        masteryRevision = mastery?.revision ?: 0,
        lemmas = lemmas,
        lookup = lookup,
        readingPreferences = readingPreferences,
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
    mastery: MasteryController?,
    scope: CoroutineScope,
    environment: AppEnvironment,
    selection: WordSelection,
    level: MasteryLevel,
) {
    mastery ?: return
    val entry = VocabularyEntry(
        key = selection.tap.token.key,
        surface = selection.tap.token.surface,
        translation = selection.result.firstGloss(),
        contextSentence = selection.tap.contextSentence,
        mastery = level,
    )
    scope.launch { withContext(environment.dispatcher) { mastery.save(entry) } }
}

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
    val translation: PhraseTranslationUiState,
    val translationLanguage: String?,
    val onOpenSettings: () -> Unit,
    val onWordTap: (WordTap) -> Unit,
    val onPhraseSelected: (PhraseSelection) -> Unit,
    val onDismiss: () -> Unit,
) {
    /** Whether a word or phrase panel is open above the reader, for the page's dismiss scrim. */
    val hasPanel: Boolean get() = selection != null || phrase != null
}

/**
 * The settings screen's live pieces, bundled so [AppScreens] stays within the
 * parameter bound: the two controllers it drives and the state it renders.
 */
@Suppress("LongParameterList") // The settings controllers; bundling a bundle adds a type, not clarity.
private class Settings(
    val dictionary: DictionaryController?,
    val mastery: MasteryController?,
    val vaultTransfer: VaultTransferController?,
    val provider: ProviderController?,
    val sync: SyncController?,
    val languageSettings: LanguageSettingsController?,
    val state: SettingsUiState,
)

/** The settings destination wrapped so its action bundle stays out of [AppScreens]. */
@Suppress("LongParameterList") // The settings controllers and the ways it navigates, each independent.
@Composable
private fun SettingsDestination(
    settings: Settings,
    onVaultImported: () -> Unit,
    onOpenLanguages: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScreen(
        state = settings.state,
        actions = SettingsActions(
            onBack = onBack,
            onOpenLanguages = onOpenLanguages,
            onSelectAppLanguage = { language -> settings.languageSettings?.chooseAppLanguage(language) },
            onExportVault = { settings.vaultTransfer?.export() },
            onImportVault = { settings.vaultTransfer?.import(onVaultImported) },
            onDismissVaultMessage = { settings.vaultTransfer?.dismiss() },
            onSelectProvider = { provider -> settings.provider?.selectProvider(provider) },
            onModelChange = { model -> settings.provider?.setModel(model) },
            onBaseUrlChange = { baseUrl -> settings.provider?.setBaseUrl(baseUrl) },
            onLoadModels = { apiKey -> settings.provider?.loadModels(apiKey) },
            onSaveProvider = { apiKey -> settings.provider?.save(apiKey) },
            onTestProvider = { apiKey -> settings.provider?.test(apiKey) },
            onRemoveProviderKey = { settings.provider?.removeKey() },
            onDismissProviderResult = { settings.provider?.dismissResult() },
            onSyncHostChange = { host -> settings.sync?.setServerUrl(host) },
            onSyncUsernameChange = { username -> settings.sync?.setUsername(username) },
            onSaveSync = { password -> settings.sync?.save(password) },
            onTestSync = { password -> settings.sync?.test(password) },
            onEnableSync = { settings.sync?.enable() },
            onDisableSync = { settings.sync?.disable() },
            onSyncNow = { settings.sync?.syncNow() },
            onDisconnectSync = { settings.sync?.disconnect() },
            onDismissSyncResult = { settings.sync?.dismissResult() },
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
    onOpenLanguages: () -> Unit,
    onOpen: (ReadingSession) -> Unit,
) {
    LibraryScreen(
        state = state,
        actions = libraryActions(
            environment,
            controller,
            scope,
            LibraryNavigation(onOpenVocabulary, onOpenSettings, onOpenLanguages, onOpen),
        ),
    )
}

/**
 * The vocabulary list destination (issue #23): the reader's live vocabulary, the
 * search query the user has typed, and the delete that removes an entry from the
 * vault. The query is local to the screen; the delete is a blocking vault write,
 * so it runs on the environment's dispatcher, and the reader recolours through
 * the controller's bumped revision.
 *
 * The translation each row shows is derived from the installed **Dictionary
 * packs** (issue #67; ADR-0032), not the one the entry froze at save time: the
 * whole list is queried once per entry set and **Translation language**, off the
 * UI thread because the pack read blocks, and an entry no pack answers falls back
 * to its stored translation. Nothing derived is written back to the vault, and the
 * search matches the resolved translation, because the list searches what it
 * shows.
 */
@Suppress("LongParameterList") // The composition's inputs; a bundle would only hide that.
@Composable
private fun VocabularyDestination(
    vocabulary: VocabularyController?,
    environment: AppEnvironment,
    languageSettings: LanguageSettingsController?,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var masteryLevel by remember { mutableStateOf<MasteryLevel?>(null) }
    val translationLanguage = languageSettings?.translationLanguage
    val all = vocabulary?.all().orEmpty()
    var derived by remember { mutableStateOf(emptyMap<WordKey, String>()) }
    LaunchedEffect(all, translationLanguage, environment.dictionary) {
        // Each Reading language's pack is resolved here, on the composition thread
        // where the reader resolves its own, so the services' per-pair installer
        // cache is only ever written from one thread; only the blocking query moves
        // off it (docs/testing.md, "Deterministic seams").
        val dictionary = environment.dictionary
        val lookups = all.map { entry -> entry.key.language }.distinct()
            .associateWith { reading -> dictionary?.lookup(reading, translationLanguage) }
        derived = withContext(environment.dispatcher) {
            packTranslations(all) { reading, surface ->
                lookups[reading]?.lookup(surface, reading)
                    ?: WordLookup.Unavailable(DictionaryLookup.NOT_INSTALLED)
            }
        }
    }
    val shown = all.map { entry -> entry.copy(translation = derived[entry.key] ?: entry.translation) }
    val state = if (all.isEmpty()) {
        VocabularyUiState.Empty
    } else {
        VocabularyUiState.Results(filterByMastery(searchVocabulary(shown, query), masteryLevel), query, masteryLevel)
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
    translationLanguage: String?,
    onLookup: (WordTap, WordLookup) -> Unit,
): (WordTap) -> Unit = { tap ->
    if (dictionary == null) {
        onLookup(tap, WordLookup.Unavailable(DictionaryLookup.NOT_INSTALLED))
    } else {
        scope.launch {
            onLookup(tap, dictionary.lookUp(tap.token.surface, tap.token.key.language, translationLanguage))
        }
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
    onDelete = { book -> controller.delete(book.id) },
    onDismissError = controller::dismissError,
    onOpenSettings = navigation.onOpenSettings,
    onOpenVocabulary = navigation.onOpenVocabulary,
    onOpenLanguages = navigation.onOpenLanguages,
)

/** Where the library's taps lead, bundled so [libraryActions] stays within the parameter bound. */
private class LibraryNavigation(
    val onOpenVocabulary: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenLanguages: () -> Unit,
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
    lemmas: LemmaLookup,
    lookup: Lookup,
    readingPreferences: ReadingPreferencesController?,
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
                    lemmas = lemmas,
                ),
                initialOffset = session.position?.offset ?: 0,
                selectedRange = lookup.selection?.let { selected ->
                    selected.tap.token.start..selected.tap.token.end
                } ?: lookup.phrase?.range,
                masteryRevision = masteryRevision,
                preferences = readingPreferences?.preferences ?: ReadingPreferences.DEFAULT,
            ),
            actions = ReaderActions(
                onWordTap = lookup.onWordTap,
                onPhraseSelected = lookup.onPhraseSelected,
                onBack = onBack,
                onPositionChange = onPositionChange,
                onReadingPreferencesChange = { next -> readingPreferences?.update(next) },
                // A lookup panel is open above the reader: a tap on the page outside
                // it closes it, rather than looking a word up or turning the page.
                onDismissOverlay = if (lookup.hasPanel) lookup.onDismiss else null,
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
            translationLanguage = lookup.translationLanguage,
            openUrl = openUrl,
            onDismiss = lookup.onDismiss,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    lookup.phrase?.let { phrase ->
        PhrasePanel(
            phrase = phrase,
            language = language,
            translationLanguage = lookup.translationLanguage,
            openUrl = openUrl,
            translation = lookup.translation,
            onOpenSettings = lookup.onOpenSettings,
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
    translationLanguage: String?,
    openUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val token = selected.tap.token
    val language = token.key.language?.let { code -> baseLanguage(code) }
    val shortcuts = remember(token, language, translationLanguage) {
        dictionaryShortcuts(token.surface, language, translationLanguage)
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
    translationLanguage: String?,
    openUrl: (String) -> Unit,
    translation: PhraseTranslationUiState,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shortcut = remember(phrase, language, translationLanguage) {
        translationShortcut(
            phrase.text,
            language?.let { code -> baseLanguage(code) },
            translationLanguage,
        )
    }
    PhraseLookupPanel(
        phrase = phrase.text,
        shortcut = shortcut,
        actions = PhraseLookupActions(
            onOpenShortcut = { chosen -> openUrl(chosen.url) },
            onDismiss = onDismiss,
        ),
        translation = translation,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

/** A platform-picked file: its name and bytes, read before it reaches the domain. */
class PickedFile(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PickedFile && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}
