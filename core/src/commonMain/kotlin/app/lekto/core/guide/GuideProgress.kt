package app.lekto.core.guide

import app.lekto.core.vault.VaultFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What this device knows about the first-launch guide (issue #277): whether the
 * reader has completed it. It records completion **only** — there is no stage
 * field — so nothing is written until the last screen is reached or `Skip` is
 * taken, and a mid-guide relaunch has nothing to resume from: it starts on the
 * first screen every time.
 *
 * It is deliberately a document of its own rather than a field of the **Sample
 * book**'s dismissal (#278): a corrupt tutorial must never resurrect a sample the
 * reader removed, and removing the sample must not replay the guide. Like the
 * app's other device-local settings it is app configuration on ADR-0023's
 * pattern, never a **Vault** record, so it enters no export and never reaches a
 * **Sync target** (ADR-0005).
 *
 * A corrupt document reads as [NOT_SEEN]: showing the guide once more is a
 * nuisance, while hiding it from a reader who never saw it loses the explanation
 * the product promises.
 */
@Serializable
data class GuideProgress(val seen: Boolean = false) {

    companion object {
        /** A fresh install, which has not completed the guide. */
        val NOT_SEEN: GuideProgress = GuideProgress(seen = false)

        /** The reader has completed the guide (or skipped it); it opens no more unless replayed. */
        val SEEN: GuideProgress = GuideProgress(seen = true)
    }
}

/**
 * The app-private home of the [GuideProgress] record (ADR-0023's pattern; issue
 * #277). An unreadable document reads as [GuideProgress.NOT_SEEN], so a corrupt
 * file never brings the app down and never silently hides the guide.
 */
interface GuideProgressStore {

    /** The stored progress, or [GuideProgress.NOT_SEEN] when there is none. */
    fun load(): GuideProgress

    /** Replaces the stored progress. */
    fun save(progress: GuideProgress)
}

/**
 * The platform [GuideProgressStore] (issue #277): the record as one JSON document
 * over any [VaultFileSystem], the same write-temp-rename byte seam as the vault
 * and the derived assets, rooted elsewhere (ADR-0003, ADR-0010). It is shared by
 * both clients because the same file seam backs them identically; only the root
 * differs.
 *
 * A document this version cannot read — corrupt, or written by a future one whose
 * shape it does not know — reads as not seen, exactly as the reading preferences
 * and the theme degrade (ADR-0023).
 */
class JsonGuideProgressStore(private val files: VaultFileSystem, private val json: Json = Json) :
    GuideProgressStore {

    override fun load(): GuideProgress {
        val bytes = files.read(PATH) ?: return GuideProgress.NOT_SEEN
        return runCatching { json.decodeFromString<GuideProgress>(bytes.decodeToString()) }
            .getOrDefault(GuideProgress.NOT_SEEN)
    }

    override fun save(progress: GuideProgress) {
        files.writeAtomically(PATH, json.encodeToString(progress).encodeToByteArray())
    }

    companion object {
        /** The single document the store owns, relative to its root. */
        const val PATH: String = "guide.json"
    }
}
