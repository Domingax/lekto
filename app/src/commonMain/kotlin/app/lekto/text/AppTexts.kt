package app.lekto.text

import androidx.compose.runtime.staticCompositionLocalOf
import app.lekto.core.MasteryLevel
import app.lekto.core.dictionary.DictionaryLookup
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.language.AppLanguages
import app.lekto.core.language.readingLanguageName
import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.llm.LlmModelsResult
import app.lekto.core.secret.SecretStore
import app.lekto.core.speech.Pronouncer
import app.lekto.core.text.baseLanguage
import app.lekto.core.theme.AppTheme

/**
 * The app's own texts, in one language (ADR-0034): the per-language table a
 * [TextKey] resolves to. [EnglishTexts] is the base — every other table defines
 * itself against it — and [FrenchTexts] is the second language the app ships.
 *
 * A screen names a [TextKey] and renders [get]; a controller names one too, in an
 * [AppMessage], so no visible string is a literal outside a table. The reader's
 * **app language** selects the table through [LocalAppTexts], which the app's root
 * provides and re-provides whenever the setting moves, so the whole interface
 * follows it with no relaunch.
 */
@Suppress("TooManyFunctions") // One accessor per kind of text the app has; splitting hides them.
class AppTexts internal constructor(
    private val table: Map<TextKey, String>,
    private val names: Map<String, String> = emptyMap(),
    private val fallback: Map<TextKey, String> = table,
) {

    /**
     * [key]'s text: this language's table, the base language's when a table has a
     * hole, and the key's own name only when even the base is incomplete, so a
     * partial translation degrades to English rather than to an identifier.
     */
    operator fun get(key: TextKey): String = table[key] ?: fallback[key] ?: key.name

    /** Whether this language's own table defines [key], so a test can pin that it is complete. */
    internal fun defines(key: TextKey): Boolean = table.containsKey(key)

    /** The name to show for a language [code], falling back to the base build's table. */
    fun languageName(code: String): String = names[baseLanguage(code)] ?: readingLanguageName(code)

    /** The reader-facing text of [message] in this language. */
    fun message(message: AppMessage): String = when (message) {
        is AppMessage.Text -> format(this[message.key], message.args)
        is AppMessage.Core -> coreMessage(message.text)
        is AppMessage.SyncReportText -> syncReport(message)
    }

    /**
     * A domain outcome's text: a known constant reads in this language, and
     * anything else — a free-text reason a transport or the platform supplied —
     * passes through, because it is not ours to translate.
     */
    fun coreMessage(text: String): String = CORE_KEYS[text]?.let { key -> this[key] } ?: text

    /** A **Mastery level**'s readable name, so the list and filter name it, not only its colour. */
    fun masteryName(level: MasteryLevel): String = this[MASTERY_KEYS.getValue(level)]

    /** An **App theme**'s readable name, so the Appearance screen and Settings name the current one. */
    fun themeName(theme: AppTheme): String = this[THEME_KEYS.getValue(theme)]

    /** The one-line description of what an **App theme** looks like, shown beside its preview. */
    fun themeDescription(theme: AppTheme): String = this[THEME_DESCRIPTION_KEYS.getValue(theme)]

    /** An honest one-line summary of a **Dictionary pack**'s state. */
    fun dictionaryStatus(state: DictionaryPackState): String = when (state) {
        DictionaryPackState.NotInstalled -> this[TextKey.DICTIONARY_STATUS_NOT_INSTALLED]

        is DictionaryPackState.Ready -> this[TextKey.DICTIONARY_STATUS_READY]

        is DictionaryPackState.Incompatible -> format(
            this[TextKey.DICTIONARY_STATUS_INCOMPATIBLE],
            listOf(state.found.toString(), state.expected.toString()),
        )

        is DictionaryPackState.Corrupt -> this[TextKey.DICTIONARY_STATUS_CORRUPT]
    }

    /** An honest one-line summary of a **Difficulty table**'s state. */
    fun difficultyStatus(state: DifficultyState): String = when (state) {
        DifficultyState.NotInstalled -> this[TextKey.DIFFICULTY_STATUS_NOT_INSTALLED]

        is DifficultyState.Ready -> this[TextKey.DIFFICULTY_STATUS_READY]

        is DifficultyState.Incompatible -> format(
            this[TextKey.DIFFICULTY_STATUS_INCOMPATIBLE],
            listOf(state.found.toString(), state.expected.toString()),
        )

        is DifficultyState.Corrupt -> this[TextKey.DIFFICULTY_STATUS_CORRUPT]
    }

    /** The last sync's outcome in plain language: what moved, or that nothing needed to. */
    private fun syncReport(message: AppMessage.SyncReportText): String {
        val base = when {
            message.hasMovement -> format(this[TextKey.REPORT_SYNCED], listOf(movedParts(message).joinToString(", ")))
            message.unresolved > 0 -> this[TextKey.REPORT_NOTHING]
            else -> this[TextKey.REPORT_UP_TO_DATE]
        }
        return (listOf(base) + reportNotes(message)).joinToString(" ")
    }

    /** The kinds of change a report names, in the order the section reads them. */
    private fun movedParts(message: AppMessage.SyncReportText): List<String> = buildList {
        if (message.downloaded > 0) add(counted(TextKey.REPORT_DOWNLOADED, message.downloaded))
        if (message.uploaded > 0) add(counted(TextKey.REPORT_UPLOADED, message.uploaded))
        if (message.deletions > 0) add(counted(TextKey.REPORT_REMOVED, message.deletions))
        if (message.attachments > 0) add(carried(message.attachments))
    }

    /** The honest notes that follow the move count: a resync, and a write that will retry. */
    private fun reportNotes(message: AppMessage.SyncReportText): List<String> = buildList {
        if (message.resynced) add(this@AppTexts[TextKey.REPORT_RESYNCED])
        if (message.unresolved > 0) add(this@AppTexts[TextKey.REPORT_UNSETTLED])
    }

    private fun counted(key: TextKey, count: Int): String = format(this[key], listOf(count.toString()))

    private fun carried(count: Int): String = format(
        this[if (count == 1) TextKey.REPORT_CARRIED_ONE else TextKey.REPORT_CARRIED_MANY],
        listOf(count.toString()),
    )

    /** [template] with each `{n}` replaced by the matching argument. */
    private fun format(template: String, args: List<String>): String =
        args.foldIndexed(template) { index, text, arg -> text.replace("{$index}", arg) }

    private companion object {
        val MASTERY_KEYS: Map<MasteryLevel, TextKey> = mapOf(
            MasteryLevel.UNKNOWN to TextKey.MASTERY_UNKNOWN,
            MasteryLevel.RECOGNIZED to TextKey.MASTERY_RECOGNIZED,
            MasteryLevel.FAMILIAR to TextKey.MASTERY_FAMILIAR,
            MasteryLevel.LEARNED to TextKey.MASTERY_LEARNED,
            MasteryLevel.KNOWN to TextKey.MASTERY_KNOWN,
        )

        val THEME_KEYS: Map<AppTheme, TextKey> = mapOf(
            AppTheme.SYSTEM to TextKey.THEME_SYSTEM,
            AppTheme.LIGHT to TextKey.THEME_LIGHT,
            AppTheme.DARK to TextKey.THEME_DARK,
            AppTheme.SEPIA to TextKey.THEME_SEPIA,
        )

        val THEME_DESCRIPTION_KEYS: Map<AppTheme, TextKey> = mapOf(
            AppTheme.SYSTEM to TextKey.THEME_SYSTEM_DESCRIPTION,
            AppTheme.LIGHT to TextKey.THEME_LIGHT_DESCRIPTION,
            AppTheme.DARK to TextKey.THEME_DARK_DESCRIPTION,
            AppTheme.SEPIA to TextKey.THEME_SEPIA_DESCRIPTION,
        )

        val CORE_KEYS: Map<String, TextKey> = mapOf(
            SecretStore.UNAVAILABLE to TextKey.CORE_SECRET_UNAVAILABLE,
            LlmConnectionResult.INCOMPLETE to TextKey.CORE_LLM_INCOMPLETE,
            LlmConnectionResult.UNREACHABLE to TextKey.CORE_LLM_UNREACHABLE,
            LlmConnectionResult.REJECTED_KEY to TextKey.CORE_LLM_REJECTED_KEY,
            LlmConnectionResult.NOT_FOUND to TextKey.CORE_LLM_NOT_FOUND,
            LlmConnectionResult.RATE_LIMITED to TextKey.CORE_LLM_RATE_LIMITED,
            LlmConnectionResult.BAD_REQUEST to TextKey.CORE_LLM_BAD_REQUEST,
            LlmConnectionResult.UNKNOWN_MODEL to TextKey.CORE_LLM_UNKNOWN_MODEL,
            LlmConnectionResult.MISSING_SESSION to TextKey.CORE_LLM_MISSING_SESSION,
            LlmConnectionResult.NO_FUNDS to TextKey.CORE_LLM_NO_FUNDS,
            LlmConnectionResult.SERVER_ERROR to TextKey.CORE_LLM_SERVER_ERROR,
            LlmModelsResult.INCOMPLETE to TextKey.CORE_MODELS_INCOMPLETE,
            LlmModelsResult.MISSING_KEY to TextKey.CORE_MODELS_MISSING_KEY,
            LlmModelsResult.UNSUPPORTED to TextKey.CORE_MODELS_UNSUPPORTED,
            LlmModelsResult.EMPTY to TextKey.CORE_MODELS_EMPTY,
            DictionaryLookup.NOT_INSTALLED to TextKey.CORE_DICTIONARY_NOT_INSTALLED,
            DictionaryLookup.UNKNOWN_LANGUAGE to TextKey.CORE_DICTIONARY_UNKNOWN_LANGUAGE,
            Pronouncer.UNKNOWN_LANGUAGE to TextKey.CORE_SPEECH_UNKNOWN_LANGUAGE,
            Pronouncer.NO_ENGINE to TextKey.CORE_SPEECH_NO_ENGINE,
            Pronouncer.FAILED to TextKey.CORE_SPEECH_FAILED,
            Pronouncer.STARTING to TextKey.CORE_SPEECH_STARTING,
        )
    }
}

/**
 * The **app language** in effect for the composition (ADR-0034): its tables, read
 * by every screen and by the reader's panels. It defaults to English so a preview
 * or a test with no provider still renders the base language, and the app's root
 * re-provides it when the reader chooses another.
 */
val LocalAppTexts = staticCompositionLocalOf { EnglishTexts }

/**
 * The tables for an **app language** (ADR-0034): English for its base tag and for
 * anything the app does not ship, French for `fr`. Only the languages the app
 * ships — [AppLanguages.SUPPORTED] — reach here, so an unsupported tag is the base
 * language rather than a hole.
 */
fun appTextsFor(appLanguage: String?): AppTexts =
    if (baseLanguage(appLanguage.orEmpty()) == AppLanguages.FRENCH) FrenchTexts else EnglishTexts
