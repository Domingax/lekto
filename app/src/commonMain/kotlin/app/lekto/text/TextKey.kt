package app.lekto.text

/**
 * The identity of one of the app's own visible strings (ADR-0034): the key a
 * per-language table resolves to the reader's **app language**. A screen, a
 * controller or a platform entry point names a [TextKey]; never a literal, so the
 * one place a string is written is its language table, and adding a language is
 * adding a table rather than editing every screen.
 *
 * A key whose text carries a value — a title, a count — holds `{0}`, `{1}`
 * placeholders [AppTexts.message] fills.
 */
enum class TextKey {
    // Navigation and controls shared across screens.
    LIBRARY,
    SETTINGS,
    VOCABULARY,
    LANGUAGES,
    ATTRIBUTION,
    BACK,
    OPEN,
    DISMISS,
    DELETE,
    CANCEL,
    CLOSE,
    SHOW,
    HIDE,

    /** The label a search field carries, on the Library and on Vocabulary. */
    SEARCH,

    /** The action that empties a search field. */
    CLEAR_SEARCH,

    // Platform file dialogs (the native chrome a reader sees on desktop). The
    // system dialog draws its own filter and buttons, so only a title is ours.
    PICK_BOOK_TITLE,
    EXPORT_VAULT_TITLE,
    IMPORT_VAULT_TITLE,

    // Library.

    /** The empty shelf's heading, in the screen title's serif. */
    LIBRARY_EMPTY_TITLE,

    /** The sentence under the empty heading: what to do about it. */
    LIBRARY_EMPTY,

    /** The filled action in the empty state; the header's [IMPORT] is a word, this one carries the action. */
    IMPORT_BOOK,

    /** What the skeleton rows say to a screen reader while the shelf is being read. */
    LIBRARY_LOADING,

    CONTINUE_READING,

    /** The heading over the books that did not lead: what is left to read. */
    OTHER_BOOKS,

    IMPORT,
    IMPORTING,
    IMPORT_PROGRESS,
    DELETE_BOOK_TITLE,
    DELETE_BOOK_MESSAGE,
    ERROR_DETAIL,
    NO_BOOKS_MATCH_QUERY,

    // The row's Reading progress line (issue #196).
    LIBRARY_ROW_PROGRESS,
    LIBRARY_ROW_NOT_STARTED,
    LIBRARY_ROW_MEASURING,
    LIBRARY_ROW_MEASURING_DESCRIPTION,

    // Vocabulary.
    VOCABULARY_EMPTY_TITLE,
    VOCABULARY_EMPTY,

    /** The saved-word count beside the screen's title, in the singular and the plural. */
    VOCABULARY_COUNT_ONE,
    VOCABULARY_COUNT_MANY,
    ALL_LEVELS,
    MASTERY_UNKNOWN,
    MASTERY_RECOGNIZED,
    MASTERY_FAMILIAR,
    MASTERY_LEARNED,
    MASTERY_KNOWN,
    NO_WORDS_TO_SHOW,
    NO_WORDS_MATCH_QUERY,
    NO_WORDS_MATCH_QUERY_AT_LEVEL,
    NO_WORDS_AT_LEVEL,

    /** What a search that found nothing covers, under the no-match heading. */
    NO_WORDS_MATCH_DETAIL,
    DELETE_WORD_TITLE,
    DELETE_WORD_MESSAGE,

    // Reader.
    PREVIOUS_PAGE,
    NEXT_PAGE,
    PAGE_OF,

    /** The position of a book with chapters: `Chapter 4 · Page 99 of 357` (ADR-0037). */
    CHAPTER_POSITION,

    /** The chapter sheet's heading. */
    CHAPTERS,
    READING_PREFERENCES,
    READING_TEXT,
    READING_FACE,
    READING_FACE_SERIF,
    READING_FACE_SANS_SERIF,
    READING_SIZE,
    READING_SMALLER,
    READING_LARGER,
    READING_MARGINS,
    READING_MARGIN_NARROW,
    READING_MARGIN_COMFORTABLE,
    READING_MARGIN_WIDE,

    // Word lookup panel.
    LISTEN,
    MASTERY,
    NOT_IN_DICTIONARY,
    NO_VOICE_FOR_LANGUAGE,
    NO_VOICE_FOR_UNKNOWN_LANGUAGE,
    TRANSLATE_ONLINE,

    // Phrase lookup panel.
    QUOTED_PHRASE,
    CONNECT_PROVIDER_PROMPT,
    SET_UP_PROVIDER,
    TRANSLATING,
    PROVIDER_RETURNED_NO_TRANSLATION,
    HIDE_WHAT_IS_SENT,
    WHAT_IS_SENT,
    SENDS_SELECTION_TO,
    THE_PROVIDER,
    NO_TRANSLATION_SERVICE,

    // Settings: sections and their controls.
    SECTION_APPEARANCE,
    SECTION_APP_LANGUAGE,
    SECTION_LANGUAGES,
    SECTION_SYNC,
    SECTION_LLM_PROVIDER,

    /** The **Vault and sync** screen's title (docs/design.md §7.6; issue #213). */
    VAULT_AND_SYNC,
    LANGUAGES_SECTION_DESCRIPTION,
    VAULT_SECTION_DESCRIPTION,
    VAULT_UNAVAILABLE,
    EXPORT_VAULT,
    IMPORT_VAULT,
    PROVIDER_SECTION_DESCRIPTION,
    PROVIDER_UNAVAILABLE,
    PROVIDER,
    BASE_URL,
    API_KEY,
    MODEL,
    CHOOSE_A_PROVIDER,
    KEY_STORED,
    REMOVE_KEY,
    CONNECTION,
    APP_LANGUAGE_DESCRIPTION,

    // Appearance screen (ADR-0036).
    APPEARANCE_DESCRIPTION,
    THEME_SYSTEM,
    THEME_SYSTEM_DESCRIPTION,
    THEME_LIGHT,
    THEME_LIGHT_DESCRIPTION,
    THEME_DARK,
    THEME_DARK_DESCRIPTION,
    THEME_SEPIA,
    THEME_SEPIA_DESCRIPTION,
    CURRENT_THEME,

    // Sync section.
    SYNC_FOLDER_WARNING,
    SYNC_FOLDER_WARNING_TITLE,
    SYNC_UNAVAILABLE,
    SERVER_ADDRESS,
    USERNAME,
    APPLICATION_PASSWORD,
    SYNC_NOW,
    DISCONNECT,
    SYNC_LAST_JUST_NOW,
    SYNC_LAST_MINUTE,
    SYNC_LAST_MINUTES,
    SYNC_LAST_HOUR,
    SYNC_LAST_HOURS,
    SYNC_LAST_DAY,
    SYNC_LAST_DAYS,
    SYNC_STATUS_ON,
    SYNC_STATUS_OFF,
    SYNC_STATUS_NOT_SET_UP,

    // Languages surface.
    RELOAD_LIST,
    TRANSLATION_LANGUAGE,
    NOT_SET,
    LANGUAGES_EMPTY_AVAILABLE,
    LANGUAGES_EMPTY_UNAVAILABLE,
    LOADING_LANGUAGES,
    DIFFICULTY_NOT_PUBLISHED,
    DOWNLOADING_DIFFICULTY,
    DOWNLOAD_AGAIN,
    DOWNLOAD_DIFFICULTY,
    DICTIONARY_NOT_PUBLISHED,
    DOWNLOADING_DICTIONARY,
    DOWNLOAD_DICTIONARY,
    DICTIONARY_STATUS_NOT_INSTALLED,
    DICTIONARY_STATUS_READY,
    DICTIONARY_STATUS_INCOMPATIBLE,
    DICTIONARY_STATUS_CORRUPT,
    DIFFICULTY_STATUS_NOT_INSTALLED,
    DIFFICULTY_STATUS_READY,
    DIFFICULTY_STATUS_INCOMPATIBLE,
    DIFFICULTY_STATUS_CORRUPT,

    // Attribution screen.
    ATTRIBUTION_DICTIONARY_GRANT,
    ATTRIBUTION_DICTIONARY_NOT_INSTALLED,
    ATTRIBUTION_DIFFICULTY_GRANT,
    ATTRIBUTION_DIFFICULTY_NOT_INSTALLED,
    ATTRIBUTION_ICONS_GRANT,
    FIELD_SOURCE,
    FIELD_DATA,
    FIELD_LICENCE,
    FIELD_MODIFICATIONS,

    // Messages the app's own controllers produce.
    MESSAGE_IMPORT_FAILED,
    MESSAGE_BOOK_DELETE_FAILED,
    MESSAGE_SAVED,
    MESSAGE_VAULT_EXPORTED,
    MESSAGE_VAULT_IMPORTED,
    MESSAGE_VAULT_EXPORT_FAILED,
    MESSAGE_NOT_A_VAULT,
    MESSAGE_VAULT_UNREADABLE,
    MESSAGE_VAULT_IMPORT_FAILED,
    MESSAGE_PROVIDER_CONNECTED,
    MESSAGE_KEY_REMOVED,
    MESSAGE_SYNC_CONNECTED,
    MESSAGE_SYNC_DISCONNECTED,
    MESSAGE_SYNC_NOT_CONFIGURED,
    MESSAGE_SYNC_NO_PASSWORD,
    MESSAGE_SYNC_PROBE_FAILED,
    MESSAGE_SYNC_PROBE_FAILED_UNKNOWN,
    MESSAGE_SYNC_RUN_FAILED,
    MESSAGE_SYNC_RUN_FAILED_UNKNOWN,
    MESSAGE_DICTIONARY_DOWNLOAD_FAILED,
    MESSAGE_DIFFICULTY_DOWNLOAD_FAILED,
    MESSAGE_LANGUAGES_LOAD_FAILED,

    // The last sync's outcome, in plain language.
    REPORT_SYNCED,
    REPORT_NOTHING,
    REPORT_UP_TO_DATE,
    REPORT_DOWNLOADED,
    REPORT_UPLOADED,
    REPORT_REMOVED,
    REPORT_CARRIED_ONE,
    REPORT_CARRIED_MANY,
    REPORT_RESYNCED,
    REPORT_UNSETTLED,

    // Domain outcomes whose text the app renders (ADR-0034): the keys a core
    // constant is mapped to, so an honest failure reads in the reader's language.
    CORE_SECRET_UNAVAILABLE,
    CORE_LLM_INCOMPLETE,
    CORE_LLM_UNREACHABLE,
    CORE_LLM_REJECTED_KEY,
    CORE_LLM_NOT_FOUND,
    CORE_LLM_RATE_LIMITED,
    CORE_LLM_BAD_REQUEST,
    CORE_LLM_UNKNOWN_MODEL,
    CORE_LLM_MISSING_SESSION,
    CORE_LLM_NO_FUNDS,
    CORE_LLM_SERVER_ERROR,
    CORE_MODELS_INCOMPLETE,
    CORE_MODELS_MISSING_KEY,
    CORE_MODELS_UNSUPPORTED,
    CORE_MODELS_EMPTY,
    CORE_DICTIONARY_NOT_INSTALLED,
    CORE_DICTIONARY_UNKNOWN_LANGUAGE,
    CORE_SPEECH_UNKNOWN_LANGUAGE,
    CORE_SPEECH_NO_ENGINE,
    CORE_SPEECH_FAILED,
    CORE_SPEECH_STARTING,
}
