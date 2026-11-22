package app.lekto.core.book

import app.lekto.core.vault.VaultFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The reader's removal of the **Sample book** (CONTEXT.md, "Sample book";
 * issue #278): the **Reading language** whose sample they dismissed, or `null`
 * when no sample has been removed.
 *
 * It is deliberately a document of its own rather than a field of the
 * first-launch guide's progress (#277): a corrupt tutorial must never resurrect
 * a sample the reader removed. Like the guide's progress and the app's other
 * device-local settings it is app configuration on ADR-0023's pattern, never a
 * **Vault** record, so it enters no export and never reaches a **Sync target**
 * (ADR-0005).
 *
 * A dismissal keeps the sample's identity `sample:<language>` reserved: a later
 * build that ships the sample again reads this document and materialises
 * nothing.
 */
@Serializable
data class SampleDismissed(val language: String? = null) {

    /** Whether a sample has been removed, whatever language it was. */
    val isDismissed: Boolean get() = language != null

    companion object {
        /** No sample has been removed, which is every fresh install. */
        val NONE: SampleDismissed = SampleDismissed()
    }
}

/**
 * The app-private home of the [SampleDismissed] record (issue #278; ADR-0023's
 * pattern). An unreadable document reads as [SampleDismissed.NONE], so a corrupt
 * file never brings the shelf down and never invents a removal.
 */
interface SampleDismissedStore {

    /** The stored dismissal, or [SampleDismissed.NONE] when there is none. */
    fun load(): SampleDismissed

    /** Replaces the stored dismissal. */
    fun save(dismissed: SampleDismissed)
}

/**
 * The platform [SampleDismissedStore] (issue #278): the record as one JSON
 * document over any [VaultFileSystem], the same write-temp-rename byte seam as
 * the vault and the derived assets, rooted elsewhere (ADR-0003, ADR-0010). It is
 * shared by both clients because the same file seam backs them identically; only
 * the root differs.
 *
 * A document this version cannot read — corrupt, or written by a future one
 * whose shape it does not know — reads as not dismissed, exactly as the reading
 * preferences and the theme degrade (ADR-0023). A sample the reader did not
 * remove stays, which is the recoverable direction: a disappeared demo is
 * worse than a repeated one only if removal were clearable, and it never is.
 */
class JsonSampleDismissedStore(private val files: VaultFileSystem, private val json: Json = Json) :
    SampleDismissedStore {

    override fun load(): SampleDismissed {
        val bytes = files.read(PATH) ?: return SampleDismissed.NONE
        return runCatching { json.decodeFromString<SampleDismissed>(bytes.decodeToString()) }
            .getOrDefault(SampleDismissed.NONE)
    }

    override fun save(dismissed: SampleDismissed) {
        files.writeAtomically(PATH, json.encodeToString(dismissed).encodeToByteArray())
    }

    companion object {
        /** The single document the store owns, relative to its root. */
        const val PATH: String = "sample-dismissed.json"
    }
}
