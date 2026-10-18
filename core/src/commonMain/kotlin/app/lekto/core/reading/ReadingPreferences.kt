package app.lekto.core.reading

import app.lekto.core.vault.VaultFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How Lekto renders a book's text for the reader on this device (CONTEXT.md,
 * "Reading preference"; ADR-0035): the [face], the [size] and the [margin]. The
 * leading is not a field — it is derived from the [size] by [leading], so the
 * reader has one size control rather than two that fight each other.
 *
 * It is app-private device configuration on ADR-0023's pattern, never a
 * **Vault** record: it enters no export and no sync, and a second device starts
 * at the defaults. One choice per device and not per **Reading language**, because
 * a preferred face and size do not change with a book's language.
 *
 * The [size] is a plain number of scale-independent pixels (sp), so this value
 * stays pure Kotlin in `commonMain`; the application maps it to a Compose
 * `TextUnit`. The platform's font scale still multiplies it (ADR-0035).
 */
@Serializable
data class ReadingPreferences(
    val face: ReadingFace = ReadingFace.SERIF,
    val size: Float = DEFAULT_SIZE,
    val margin: ReadingMargin = ReadingMargin.COMFORTABLE,
) {

    /** The body's line height, derived from the [size] by [LEADING_RATIO] (ADR-0035). */
    val leading: Float get() = size * LEADING_RATIO

    /** The horizontal page margin, in density-independent pixels. */
    val horizontalMargin: Float get() = margin.horizontalMargin

    /** The same preference, one [SIZE_STEP] smaller, never below [MIN_SIZE]. */
    fun smaller(): ReadingPreferences = copy(size = (size - SIZE_STEP).coerceAtLeast(MIN_SIZE))

    /** The same preference, one [SIZE_STEP] larger, never above [MAX_SIZE]. */
    fun larger(): ReadingPreferences = copy(size = (size + SIZE_STEP).coerceAtMost(MAX_SIZE))

    companion object {
        /** The smallest size the control offers (about 14.sp). */
        const val MIN_SIZE: Float = 14f

        /** The largest size the control offers (about 28.sp). */
        const val MAX_SIZE: Float = 28f

        /** How far one turn of the size control moves, in sp. */
        const val SIZE_STEP: Float = 2f

        /** Today's reading size, the value [ReaderStyles] held when it was fixed (ADR-0035). */
        const val DEFAULT_SIZE: Float = 18f

        /** Today's body leading as a ratio of the size: the 1.7 the decision names (ADR-0035). */
        const val LEADING_RATIO: Float = 1.7f

        /** What a reader who has changed nothing sees: today's fixed reading style. */
        val DEFAULT: ReadingPreferences = ReadingPreferences()
    }
}

/** One of the platform's own text faces (ADR-0035); Lekto bundles no font file. */
@Serializable
enum class ReadingFace {
    /** The serif the reader defaults to, the face a printed book reads in. */
    SERIF,

    /** The platform's sans-serif, for a reader who prefers it. */
    SANS_SERIF,
}

/** The horizontal margin, in three steps, the middle one today's 24.dp (ADR-0035). */
@Suppress("MagicNumber") // The three steps are the preference's data; the middle one is today's 24.dp.
@Serializable
enum class ReadingMargin(val horizontalMargin: Float) {
    NARROW(16f),
    COMFORTABLE(24f),
    WIDE(32f),
}

/**
 * The app-private home of the [ReadingPreferences] (ADR-0035; the seam ADR-0023
 * established). It is deliberately **not** the **Vault** (ADR-0005): how a device
 * renders a book is that device's own configuration, so it never appears in an
 * export and does not move between devices. A store that cannot be read reports
 * [ReadingPreferences.DEFAULT] rather than throwing.
 */
interface ReadingPreferencesStore {

    /** The stored preferences, or [ReadingPreferences.DEFAULT] when none are stored. */
    fun load(): ReadingPreferences

    /** Replaces the stored preferences. */
    fun save(preferences: ReadingPreferences)
}

/**
 * The platform [ReadingPreferencesStore] (ADR-0035): the preference as one JSON
 * document over any [VaultFileSystem], the same write-temp-rename byte seam as the
 * vault and the derived assets, rooted elsewhere (ADR-0003, ADR-0010). It is
 * shared by both clients because the same file seam backs them identically; only
 * the root differs.
 *
 * A document this version cannot read — corrupt, or written by a future one whose
 * face, margin or shape it does not know — is treated as the defaults rather than
 * a crash, exactly as the provider and language settings degrade (ADR-0023).
 */
class JsonReadingPreferencesStore(private val files: VaultFileSystem, private val json: Json = Json) :
    ReadingPreferencesStore {

    override fun load(): ReadingPreferences {
        val bytes = files.read(PATH) ?: return ReadingPreferences.DEFAULT
        return runCatching { json.decodeFromString<ReadingPreferences>(bytes.decodeToString()) }
            .getOrDefault(ReadingPreferences.DEFAULT)
    }

    override fun save(preferences: ReadingPreferences) {
        files.writeAtomically(PATH, json.encodeToString(preferences).encodeToByteArray())
    }

    companion object {
        /** The single document the store owns, relative to its root. */
        const val PATH: String = "reading-preferences.json"
    }
}
