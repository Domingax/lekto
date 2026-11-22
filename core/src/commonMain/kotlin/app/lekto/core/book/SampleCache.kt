package app.lekto.core.book

import app.lekto.core.sample.Sample
import app.lekto.core.text.StructuredText
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.InstantIso8601Serializer
import app.lekto.core.vault.VaultCodec
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The device-local document a materialised **Sample book** is stored as (issue
 * #278; ADR-0005): everything the reader opens it with — the catalogue entry,
 * the text built from its paragraphs, and where the reader left off — under the
 * stable identity `sample:<language>`.
 *
 * The identity is not a legal file path ([app.lekto.core.vault.VaultFileSystem]
 * rejects `:`), so the file encodes it as `samples/<language>.json` while the
 * book the shelf shows still carries the identity itself.
 *
 * [formatVersion] is the shape's handshake (ADR-0037): a document this build
 * does not understand reads as absent, so the shelf shows no row rather than one
 * pointing at bytes it cannot read.
 */
@Serializable
internal data class MaterialisedSample(
    val formatVersion: Int = FORMAT_VERSION,
    val sample: Sample,
    val text: StructuredText,
    /** The reader's **Reading position** in [text], or `null` when they have not turned a page. */
    val position: Int? = null,
    /** When the reader declared the sample **Finished**, or `null` when they have not. */
    @Serializable(with = InstantIso8601Serializer::class)
    val finishedAt: Instant? = null,
) {

    companion object {
        /** The shape of [MaterialisedSample] this build writes and accepts. Version 1 is the first. */
        const val FORMAT_VERSION: Int = 1
    }
}

/**
 * The derived store's home of the one materialised **Sample book** (issue #278):
 * the file it lives in, the versioned envelope it is written in, and the one
 * language this device holds.
 *
 * It is a separate cache beside [BookTextCache], not a vault record: a sample is
 * a **Derived asset**, so it never enters an export and never reaches a **Sync
 * target**, and a wiped derived store loses nothing the reader authored. The one
 * sample at a time is structural — [storedLanguage] names the single file, and
 * [put] refuses to write a second — so two rows cannot exist whatever the reader
 * adds in Settings.
 */
internal class SampleCache(private val derived: DerivedAssetStore) {

    /** The language whose sample this device holds, or `null` when none was written. */
    fun storedLanguage(): String? = derived.paths().firstNotNullOfOrNull(::languageOf)

    /** The stored sample for [language], or `null` when there is none or it is unreadable. */
    fun document(language: String): MaterialisedSample? = derived.get(pathOf(language))?.let { bytes ->
        runCatching {
            val document = VaultCodec.json.decodeFromJsonElement(
                MaterialisedSample.serializer(),
                VaultCodec.json.parseToJsonElement(bytes.decodeToString()),
            )
            document.takeIf { stored -> stored.formatVersion == MaterialisedSample.FORMAT_VERSION }
        }.getOrNull()
    }

    /**
     * Writes [sample] with [text] as this device's one sample, replacing whatever
     * entry it already holds. An explicit replay's answer is the reader's deliberate
     * redo, so the shelf follows it; there is still exactly one sample at any
     * moment, because the previous entry's file is removed first.
     */
    fun put(sample: Sample, text: StructuredText) {
        storedLanguage()?.let(::remove)
        write(MaterialisedSample(sample = sample, text = text))
    }

    /** Rewrites [document], which carries the language of the sample it belongs to. */
    fun update(document: MaterialisedSample) {
        write(document)
    }

    /** Removes [language]'s sample; the dismissal that hides it is written by the caller first. */
    fun remove(language: String) {
        derived.remove(pathOf(language))
    }

    private fun write(document: MaterialisedSample) {
        derived.put(
            pathOf(document.sample.language),
            VaultCodec.json.encodeToString(MaterialisedSample.serializer(), document).encodeToByteArray(),
        )
    }

    /** The encoded path of [language]'s sample: `samples/<language>.json`. */
    private fun pathOf(language: String): String = "$DIRECTORY$language$SUFFIX"

    /** The language of a path in the sample directory, or `null` for any other derived path. */
    private fun languageOf(path: String): String? {
        if (!path.startsWith(DIRECTORY) || !path.endsWith(SUFFIX)) return null
        val language = path.removePrefix(DIRECTORY).removeSuffix(SUFFIX)
        return language.takeIf { name -> name.isNotEmpty() && '/' !in name }
    }

    private companion object {
        /** Where the one sample lives, beside the parsed book text. */
        const val DIRECTORY: String = "samples/"

        /** A sample's document suffix; the language is the name. */
        const val SUFFIX: String = ".json"
    }
}
