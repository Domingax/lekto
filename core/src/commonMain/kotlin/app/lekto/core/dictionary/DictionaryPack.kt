package app.lekto.core.dictionary

/**
 * One definition and the French translations attached to it, in the pack's
 * order. This is the sense half of a [DictionaryEntry].
 */
data class DictionarySense(val definition: String, val translations: List<String>)

/**
 * One dictionary entry: a lemma with its part of speech, pronunciation and
 * senses, as the offline EN→FR pack stores it (CONTEXT.md, "Dictionary pack").
 */
data class DictionaryEntry(
    val lemma: String,
    val language: String,
    val partOfSpeech: String?,
    val pronunciation: String?,
    val audioUrl: String?,
    val senses: List<DictionarySense>,
) {
    /** Every French translation of the entry, across its senses, in order. */
    val translations: List<String> get() = senses.flatMap(DictionarySense::translations)
}

/**
 * The attribution and provenance the pack carries, which the mandatory
 * attribution screen renders (ADR-0011). [formatVersion] is the pack's
 * handshake: a reader refuses a pack it does not understand.
 */
data class PackMetadata(
    val formatVersion: Int,
    val attribution: String?,
    val license: String?,
    val licenseUrl: String?,
    val licenseFullTextUrl: String?,
    val source: String?,
    val sourceData: String?,
    val modifications: String?,
    val notice: String?,
) {
    companion object {
        /** Reads the metadata a pack's `pack_metadata` table carries. */
        fun from(formatVersion: Int, values: Map<String, String>): PackMetadata = PackMetadata(
            formatVersion = formatVersion,
            attribution = values["attribution"],
            license = values["license"],
            licenseUrl = values["license_url"],
            licenseFullTextUrl = values["license_full_text_url"],
            source = values["source"],
            sourceData = values["source_data"],
            modifications = values["modifications"],
            notice = values["notice"],
        )
    }
}

/**
 * The offline dictionary pack: a read-only, randomly-accessed, device-local
 * derived asset (CONTEXT.md, "Derived asset"; ADR-0005, ADR-0017).
 *
 * It is the seam the domain queries for a word's lemma, senses and translation,
 * and the one mastery keying (ADR-0006) reads lemmas from. The concrete
 * implementation opens the pack by its app-private platform path; the contract
 * every implementation must satisfy lives in `testkit`.
 */
interface DictionaryPack : AutoCloseable {

    /** The pack's format version and attribution, read once at open. */
    val metadata: PackMetadata

    /** Every entry for [lemma] in [language], in the pack's order. */
    fun entries(language: String, lemma: String): List<DictionaryEntry>

    /** The lemma [surface] resolves to in [language], or null when unknown. */
    fun lemmaOf(surface: String, language: String): String?

    companion object {
        /**
         * The pack format this reader understands, duplicated from the producer's
         * `PackFormat.VERSION` because `tools/dictionaries` depends on no Lekto
         * module (ADR-0013). A synchronisation test in `architecture` keeps the
         * two in step.
         */
        const val FORMAT_VERSION: Int = 1
    }
}
