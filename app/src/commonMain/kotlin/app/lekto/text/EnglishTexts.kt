package app.lekto.text

/**
 * English, the base language the app is written in (ADR-0034): every other table
 * defines itself against these words, and a test pins them so a later language
 * cannot quietly move the baseline. Its language names are [readingLanguageName]'s
 * fixed table, so English is not spelled out twice.
 */
internal val EnglishTexts: AppTexts by lazy { AppTexts(EnglishTable) }

internal val EnglishTable: Map<TextKey, String> = mapOf(
    // Navigation and controls.
    TextKey.LIBRARY to "Library",
    TextKey.SETTINGS to "Settings",
    TextKey.VOCABULARY to "Vocabulary",
    TextKey.LANGUAGES to "Languages",
    TextKey.ATTRIBUTION to "Attribution",
    TextKey.BACK to "Back",
    TextKey.OPEN to "Open",
    TextKey.DISMISS to "Dismiss",
    TextKey.DELETE to "Delete",
    TextKey.CANCEL to "Cancel",
    TextKey.CLOSE to "Close",
    TextKey.SHOW to "Show",
    TextKey.HIDE to "Hide",
    TextKey.SEARCH to "Search",
    TextKey.CLEAR_SEARCH to "Clear search",

    // Platform file dialogs.
    TextKey.PICK_BOOK_TITLE to "Import a book",
    TextKey.EXPORT_VAULT_TITLE to "Export vault",
    TextKey.IMPORT_VAULT_TITLE to "Import a vault",

    // Library.
    TextKey.LIBRARY_EMPTY_TITLE to "No books yet",
    TextKey.LIBRARY_EMPTY to "An EPUB or a text file, and the first word is a minute away.",
    TextKey.IMPORT_BOOK to "Import a book",
    TextKey.LIBRARY_LOADING to "Loading your books",
    TextKey.CONTINUE_READING to "Continue reading",
    TextKey.OTHER_BOOKS to "Other books",
    TextKey.IMPORT to "Import",
    TextKey.IMPORTING to "Importing…",
    TextKey.IMPORT_PROGRESS to "Import progress",
    TextKey.DELETE_BOOK_TITLE to "Delete « {0} »?",
    TextKey.DELETE_BOOK_MESSAGE to "Lekto will remove « {0} ». This cannot be undone.",
    TextKey.ERROR_DETAIL to "Error: {0}",
    TextKey.NO_BOOKS_MATCH_QUERY to "No books match \"{0}\".",
    TextKey.LIBRARY_ROW_PROGRESS to "{0} · {1} %",
    TextKey.LIBRARY_ROW_NOT_STARTED to "{0} · Not started",
    TextKey.LIBRARY_ROW_MEASURING to "{0} · …",
    TextKey.LIBRARY_ROW_MEASURING_DESCRIPTION to "Reading progress is being measured.",

    // Vocabulary.
    TextKey.VOCABULARY_EMPTY_TITLE to "No words saved yet",
    TextKey.VOCABULARY_EMPTY to "Tap a word while reading, and it lands here.",
    TextKey.VOCABULARY_COUNT_ONE to "1 word",
    TextKey.VOCABULARY_COUNT_MANY to "{0} words",
    TextKey.ALL_LEVELS to "All levels",
    TextKey.MASTERY_UNKNOWN to "Unknown",
    TextKey.MASTERY_RECOGNIZED to "Recognized",
    TextKey.MASTERY_FAMILIAR to "Familiar",
    TextKey.MASTERY_LEARNED to "Learned",
    TextKey.MASTERY_KNOWN to "Known",
    TextKey.NO_WORDS_TO_SHOW to "No words to show.",
    TextKey.NO_WORDS_MATCH_QUERY to "No words match \"{0}\".",
    TextKey.NO_WORDS_MATCH_QUERY_AT_LEVEL to "No words match \"{0}\" at the {1} level.",
    TextKey.NO_WORDS_AT_LEVEL to "No words at the {0} level.",
    TextKey.NO_WORDS_MATCH_DETAIL to "The search covers the word, its translation and its sentence.",
    TextKey.DELETE_WORD_TITLE to "Delete « {0} »?",
    TextKey.DELETE_WORD_MESSAGE to
        "This removes the entry from your vocabulary. The word can be saved again by tapping it in the book.",

    // Reader.
    TextKey.PREVIOUS_PAGE to "Previous page",
    TextKey.NEXT_PAGE to "Next page",
    TextKey.PAGE_OF to "Page {0} of {1}",
    TextKey.READING_PREFERENCES to "Aa",
    TextKey.READING_TEXT to "Text",
    TextKey.READING_FACE to "Text face",
    TextKey.READING_FACE_SERIF to "Serif",
    TextKey.READING_FACE_SANS_SERIF to "Sans-serif",
    TextKey.READING_SIZE to "Text size",
    TextKey.READING_SMALLER to "Smaller",
    TextKey.READING_LARGER to "Larger",
    TextKey.READING_MARGINS to "Margins",
    TextKey.READING_MARGIN_NARROW to "Narrow",
    TextKey.READING_MARGIN_COMFORTABLE to "Comfortable",
    TextKey.READING_MARGIN_WIDE to "Wide",

    // Word lookup panel.
    TextKey.LISTEN to "Listen",
    TextKey.MASTERY to "Mastery",
    TextKey.NOT_IN_DICTIONARY to "\"{0}\" isn't in the offline dictionary.",
    TextKey.NO_VOICE_FOR_LANGUAGE to "No voice is installed for \"{0}\".",
    TextKey.NO_VOICE_FOR_UNKNOWN_LANGUAGE to "No voice is installed for this language.",
    TextKey.TRANSLATE_ONLINE to "Translate online",

    // Phrase lookup panel.
    TextKey.QUOTED_PHRASE to "\u201C{0}\u201D",
    TextKey.CONNECT_PROVIDER_PROMPT to "A phrase needs an AI to be translated.",
    TextKey.SET_UP_PROVIDER to "Set up a provider",
    TextKey.TRANSLATING to "Translating…",
    TextKey.PROVIDER_RETURNED_NO_TRANSLATION to "The provider returned no translation.",
    TextKey.HIDE_WHAT_IS_SENT to "Hide what's sent",
    TextKey.WHAT_IS_SENT to "What's sent?",
    TextKey.SENDS_SELECTION_TO to "Sends your selection and its sentence to {0}.",
    TextKey.THE_PROVIDER to "the provider",
    TextKey.NO_TRANSLATION_SERVICE to "No translation service is available for this phrase.",

    // Settings.
    TextKey.SECTION_APPEARANCE to "Appearance",
    TextKey.SECTION_APP_LANGUAGE to "App language",
    TextKey.SECTION_LANGUAGES to "Languages",
    TextKey.SECTION_SYNC to "Sync",
    TextKey.SECTION_LLM_PROVIDER to "LLM provider",
    TextKey.VAULT_AND_SYNC to "Vault and sync",
    TextKey.LANGUAGES_SECTION_DESCRIPTION to
        "See the languages Lekto offers for reading, ordered by the ones you have most books in. " +
        "Set your level and download the word-difficulty data and the offline dictionary for each there.",
    TextKey.VAULT_SECTION_DESCRIPTION to
        "The vault lives in the app's private storage. It holds your books, your vocabulary and " +
        "your positions.",
    TextKey.VAULT_UNAVAILABLE to "Vault export and import aren't available in this build.",
    TextKey.EXPORT_VAULT to "Export vault",
    TextKey.IMPORT_VAULT to "Import vault",
    TextKey.PROVIDER_SECTION_DESCRIPTION to
        "A selected phrase can be translated by your provider. The key stays in this device's " +
        "keychain; your books never leave the vault.",
    TextKey.PROVIDER_UNAVAILABLE to "Connecting an LLM provider isn't available in this build.",
    TextKey.PROVIDER to "Provider",
    TextKey.BASE_URL to "Base URL",
    TextKey.API_KEY to "API key",
    TextKey.MODEL to "Model",
    TextKey.CHOOSE_A_PROVIDER to "Choose a provider",
    TextKey.KEY_STORED to "A key is stored.",
    TextKey.REMOVE_KEY to "Remove key",
    TextKey.CONNECTION to "Connection",
    TextKey.APP_LANGUAGE_DESCRIPTION to "The language Lekto uses for its own texts.",
    TextKey.APPEARANCE_DESCRIPTION to
        "Choose how Lekto looks on this device. The theme recolours the whole app — never a book's " +
        "text, which keeps its face, size and margins. Your choice stays on this device and never " +
        "travels with your vault.",
    TextKey.THEME_SYSTEM to "System",
    TextKey.THEME_SYSTEM_DESCRIPTION to "Follows the device setting",
    TextKey.THEME_LIGHT to "Light",
    TextKey.THEME_LIGHT_DESCRIPTION to "Warm paper, black ink",
    TextKey.THEME_DARK to "Dark",
    TextKey.THEME_DARK_DESCRIPTION to "Warm night, light ink",
    TextKey.THEME_SEPIA to "Sepia",
    TextKey.THEME_SEPIA_DESCRIPTION to "Rag paper",
    TextKey.CURRENT_THEME to "Current",

    // Sync.
    TextKey.SYNC_FOLDER_WARNING to
        "Don't point a folder-sync tool at your vault. Syncthing, Dropbox, iCloud Drive and the like " +
        "would put a second writer on the same files and can corrupt them. Use Lekto's own sync instead.",
    TextKey.SYNC_UNAVAILABLE to "Sync isn't available in this build.",
    TextKey.SERVER_ADDRESS to "Server address",
    TextKey.USERNAME to "Username",
    TextKey.APPLICATION_PASSWORD to "Application password",
    TextKey.SYNC_NOW to "Sync now",
    TextKey.DISCONNECT to "Disconnect",
    TextKey.SYNC_LAST_JUST_NOW to "Synced just now.",
    TextKey.SYNC_LAST_MINUTE to "Synced 1 minute ago.",
    TextKey.SYNC_LAST_MINUTES to "Synced {0} minutes ago.",
    TextKey.SYNC_LAST_HOUR to "Synced 1 hour ago.",
    TextKey.SYNC_LAST_HOURS to "Synced {0} hours ago.",
    TextKey.SYNC_LAST_DAY to "Synced 1 day ago.",
    TextKey.SYNC_LAST_DAYS to "Synced {0} days ago.",
    TextKey.SYNC_STATUS_ON to "Sync is on.",
    TextKey.SYNC_STATUS_OFF to "Sync is off.",
    TextKey.SYNC_STATUS_NOT_SET_UP to "Sync isn't set up yet.",

    // Languages surface.
    TextKey.RELOAD_LIST to "Reload list",
    TextKey.TRANSLATION_LANGUAGE to "Translation language",
    TextKey.NOT_SET to "Not set",
    TextKey.LANGUAGES_EMPTY_AVAILABLE to "Lekto doesn't offer any languages for reading yet.",
    TextKey.LANGUAGES_EMPTY_UNAVAILABLE to
        "Lekto hasn't loaded the list of languages it offers yet. Reload the list, or check your connection.",
    TextKey.LOADING_LANGUAGES to "Loading the language list…",
    TextKey.DIFFICULTY_NOT_PUBLISHED to "Lekto doesn't publish word-difficulty data for this language yet.",
    TextKey.DOWNLOADING_DIFFICULTY to "Downloading word-difficulty data…",
    TextKey.DOWNLOAD_AGAIN to "Download again",
    TextKey.DOWNLOAD_DIFFICULTY to "Download word-difficulty data",
    TextKey.DICTIONARY_NOT_PUBLISHED to "Lekto doesn't publish an offline dictionary for this language yet.",
    TextKey.DOWNLOADING_DICTIONARY to "Downloading the offline dictionary…",
    TextKey.DOWNLOAD_DICTIONARY to "Download offline dictionary",
    TextKey.DICTIONARY_STATUS_NOT_INSTALLED to
        "The offline dictionary isn't installed yet. Download it to look words up with no network.",
    TextKey.DICTIONARY_STATUS_READY to "The offline dictionary is installed. Words are looked up with no network.",
    TextKey.DICTIONARY_STATUS_INCOMPATIBLE to
        "The installed dictionary is format {0}, but this app reads format {1}. Download it again.",
    TextKey.DICTIONARY_STATUS_CORRUPT to
        "The installed dictionary is damaged and couldn't be read. Download it again.",
    TextKey.DIFFICULTY_STATUS_NOT_INSTALLED to
        "Word-difficulty data isn't installed yet. Download it to colour words by how hard they are.",
    TextKey.DIFFICULTY_STATUS_READY to
        "Word-difficulty data is installed. Words are coloured by how hard they are for your level.",
    TextKey.DIFFICULTY_STATUS_INCOMPATIBLE to
        "The installed word-difficulty data is format {0}, but this app reads format {1}. Download it again.",
    TextKey.DIFFICULTY_STATUS_CORRUPT to
        "The installed word-difficulty data is damaged and couldn't be read. Download it again.",

    // Attribution.
    TextKey.ATTRIBUTION_DICTIONARY_GRANT to
        "Dictionary data is derived from Wiktionary contributors, licensed under CC BY-SA 4.0 " +
        "(and GFDL at your choice). Lekto is not affiliated with, nor endorsed by, the Wikimedia " +
        "Foundation or Wiktionary.",
    TextKey.ATTRIBUTION_DICTIONARY_NOT_INSTALLED to
        "The offline dictionary isn't installed yet. Its full attribution appears here once it is.",
    TextKey.ATTRIBUTION_DIFFICULTY_GRANT to
        "Word-difficulty data is derived from wordfreq by Robyn Speer, licensed under CC BY-SA 4.0. " +
        "Lekto is not affiliated with, nor endorsed by, wordfreq.",
    TextKey.ATTRIBUTION_DIFFICULTY_NOT_INSTALLED to
        "The word-difficulty data isn't installed yet. Its full attribution appears here once it is.",
    TextKey.ATTRIBUTION_ICONS_GRANT to
        "The interface icons come from Lucide (ISC), six of them derived from Feather (MIT): " +
        "Copyright (c) 2026 Lucide Icons and Contributors, Copyright (c) 2013-present Cole Bemis. " +
        "The launcher mark's letter is the outline of a Noto Serif glyph (SIL OFL 1.1): " +
        "Copyright 2022 The Noto Project Authors; the font itself is not bundled with Lekto. " +
        "The full notices are in the project's THIRD-PARTY-NOTICES.md.",
    TextKey.FIELD_SOURCE to "Source",
    TextKey.FIELD_DATA to "Data",
    TextKey.FIELD_LICENCE to "Licence",
    TextKey.FIELD_MODIFICATIONS to "Modifications",

    // Messages.
    TextKey.MESSAGE_IMPORT_FAILED to "The import failed.",
    TextKey.MESSAGE_BOOK_DELETE_FAILED to "The book could not be deleted.",
    TextKey.MESSAGE_SAVED to "Saved.",
    TextKey.MESSAGE_VAULT_EXPORTED to "Vault exported. Keep that file somewhere safe.",
    TextKey.MESSAGE_VAULT_IMPORTED to "Vault imported. Your books and vocabulary are restored.",
    TextKey.MESSAGE_VAULT_EXPORT_FAILED to "The vault could not be exported.",
    TextKey.MESSAGE_NOT_A_VAULT to "That file isn't a Lekto vault export.",
    TextKey.MESSAGE_VAULT_UNREADABLE to "That vault file cannot be read by this version of the app.",
    TextKey.MESSAGE_VAULT_IMPORT_FAILED to "The vault could not be imported.",
    TextKey.MESSAGE_PROVIDER_CONNECTED to "The provider answered. Your model is ready.",
    TextKey.MESSAGE_KEY_REMOVED to "Key removed from secure storage.",
    TextKey.MESSAGE_SYNC_CONNECTED to "The server answered. Sync is ready.",
    TextKey.MESSAGE_SYNC_DISCONNECTED to "Disconnected. Your vault stays on this device.",
    TextKey.MESSAGE_SYNC_NOT_CONFIGURED to "Add a server address and a username first.",
    TextKey.MESSAGE_SYNC_NO_PASSWORD to "Add the application password first.",
    TextKey.MESSAGE_SYNC_PROBE_FAILED to "Couldn't reach the server: {0}",
    TextKey.MESSAGE_SYNC_PROBE_FAILED_UNKNOWN to "Couldn't reach the server: an unknown error",
    TextKey.MESSAGE_SYNC_RUN_FAILED to "The last sync didn't finish: {0}",
    TextKey.MESSAGE_SYNC_RUN_FAILED_UNKNOWN to "The last sync didn't finish: an unknown error",
    TextKey.MESSAGE_DICTIONARY_DOWNLOAD_FAILED to "The dictionary download failed.",
    TextKey.MESSAGE_DIFFICULTY_DOWNLOAD_FAILED to "The download failed.",
    TextKey.MESSAGE_LANGUAGES_LOAD_FAILED to "The language list couldn't be loaded.",

    // The last sync's outcome.
    TextKey.REPORT_SYNCED to "Synced: {0}.",
    TextKey.REPORT_NOTHING to "Nothing was synced.",
    TextKey.REPORT_UP_TO_DATE to "Everything was already up to date.",
    TextKey.REPORT_DOWNLOADED to "downloaded {0}",
    TextKey.REPORT_UPLOADED to "uploaded {0}",
    TextKey.REPORT_REMOVED to "removed {0}",
    TextKey.REPORT_CARRIED_ONE to "carried {0} book original",
    TextKey.REPORT_CARRIED_MANY to "carried {0} book originals",
    TextKey.REPORT_RESYNCED to "This device had been away, so it restarted from the server.",
    TextKey.REPORT_UNSETTLED to "Some changes couldn't be settled; they'll retry next time.",

    // Domain outcomes.
    TextKey.CORE_SECRET_UNAVAILABLE to "Secure storage isn't available on this device.",
    TextKey.CORE_LLM_INCOMPLETE to "Choose a provider and a model before testing the connection.",
    TextKey.CORE_LLM_UNREACHABLE to "The provider could not be reached. Check your connection and the base URL.",
    TextKey.CORE_LLM_REJECTED_KEY to "The provider rejected the API key.",
    TextKey.CORE_LLM_NOT_FOUND to "The provider has no endpoint there. Check the base URL.",
    TextKey.CORE_LLM_RATE_LIMITED to "The provider is rate-limiting. Try again shortly.",
    TextKey.CORE_LLM_BAD_REQUEST to "The provider rejected the request. Check the model name.",
    TextKey.CORE_LLM_UNKNOWN_MODEL to "The provider doesn't offer that model. Check the model name.",
    TextKey.CORE_LLM_MISSING_SESSION to
        "The provider needs the app to identify its session. Update the app or pick another provider.",
    TextKey.CORE_LLM_NO_FUNDS to "The provider account can't pay for the request. Check your billing.",
    TextKey.CORE_LLM_SERVER_ERROR to "The provider answered with an error. Try again shortly.",
    TextKey.CORE_MODELS_INCOMPLETE to "Choose a provider before loading its models.",
    TextKey.CORE_MODELS_MISSING_KEY to "Enter your API key to load this provider's models.",
    TextKey.CORE_MODELS_UNSUPPORTED to "This provider doesn't list its models. Type the model name instead.",
    TextKey.CORE_MODELS_EMPTY to "This provider listed no models. Type the model name instead.",
    TextKey.CORE_DICTIONARY_NOT_INSTALLED to "The offline dictionary isn't installed yet.",
    TextKey.CORE_DICTIONARY_UNKNOWN_LANGUAGE to
        "This book's language isn't known, so the offline dictionary can't be searched.",
    TextKey.CORE_SPEECH_UNKNOWN_LANGUAGE to "This book's language isn't known, so there's no voice to use.",
    TextKey.CORE_SPEECH_NO_ENGINE to "No speech engine is available on this device.",
    TextKey.CORE_SPEECH_FAILED to "The speech engine couldn't pronounce that word.",
    TextKey.CORE_SPEECH_STARTING to "The speech engine is still starting up. Try again in a moment.",
)
