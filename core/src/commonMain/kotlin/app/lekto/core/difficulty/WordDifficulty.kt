package app.lekto.core.difficulty

import app.lekto.core.CefrLevel
import app.lekto.core.text.WordKey

/**
 * The attribution and provenance the **Difficulty table** carries, which the
 * mandatory attribution screen renders (ADR-0011, ADR-0029). [formatVersion] is
 * the artifact's handshake: a reader refuses a table it does not understand.
 *
 * The credit must travel *inside* the artifact — wordfreq's licence requires its
 * attribution to ship with the data — so [attribution], [source] and [notice]
 * name wordfreq / Robyn Speer, Google Books Ngram Viewer, OpenSubtitles and the
 * SUBTLEX authors (docs/research/word-difficulty-data-licences.md).
 */
data class DifficultyMetadata(
    val formatVersion: Int,
    val attribution: String?,
    val license: String?,
    val licenseUrl: String?,
    val source: String?,
    val sourceData: String?,
    val modifications: String?,
    val notice: String?,
) {
    companion object {
        /** Reads the metadata a difficulty table's `difficulty_metadata` rows carry. */
        fun from(formatVersion: Int, values: Map<String, String>): DifficultyMetadata = DifficultyMetadata(
            formatVersion = formatVersion,
            attribution = values["attribution"],
            license = values["license"],
            licenseUrl = values["license_url"],
            source = values["source"],
            sourceData = values["source_data"],
            modifications = values["modifications"],
            notice = values["notice"],
        )
    }
}

/**
 * The per-language **Difficulty table** (CONTEXT.md; ADR-0029): the seam the
 * default-mastery rule reads a word's **Word difficulty** from. It maps each
 * [WordKey] to the frequency band it falls in, or `null` when the table knows
 * none — a proper noun, a word beyond the calibrated top, a form the pipeline
 * could not place.
 *
 * It is a **Derived asset**, device-local and never synced: the concrete
 * implementation opens the SQLite file by its app-private platform path through
 * the same `PackDatabase` seam as the dictionary pack (ADR-0018), loads it into
 * memory once (the reader asks for a band per **Word token** while paginating),
 * and never touches the vault.
 */
interface WordDifficulty : AutoCloseable {

    /** The artifact's format version, attribution and provenance, read once at open. */
    val metadata: DifficultyMetadata

    /** The difficulty band of [key], or `null` when the table has none. */
    fun levelOf(key: WordKey): CefrLevel?

    companion object {
        /**
         * The table format this reader understands, duplicated from the producer's
         * `DifficultyFormat.VERSION` because `tools/dictionaries` depends on no
         * Lekto module (ADR-0013). A synchronisation test in `architecture` keeps
         * the two in step.
         */
        const val FORMAT_VERSION: Int = 1
    }
}
