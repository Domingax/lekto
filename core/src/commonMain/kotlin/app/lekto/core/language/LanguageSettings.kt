package app.lekto.core.language

import app.lekto.core.text.baseLanguage
import app.lekto.core.vault.VaultFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The reader's language settings (ADR-0034), held as one app-private document:
 *
 * - [appLanguage] — the language of the app's own texts;
 * - [translationLanguage] — the language every translation resolves into: the
 *   reader's offline lookup, the tokeniser's lemma index, the **Vocabulary**
 *   list and the **LLM provider** phrase prompt.
 *
 * Both are device-local configuration (ADR-0023's pattern), never **Vault**
 * records (ADR-0005), so they never appear in an export and do not move between
 * devices. A `null` field means "not chosen yet", which the app defaults from the
 * device's own language; a store that cannot be read reports [NONE] rather than
 * throwing, and no secret is involved either way.
 */
data class LanguageSettings(val appLanguage: String? = null, val translationLanguage: String? = null) {

    /** Whether the reader has chosen each setting. */
    val appLanguageChosen: Boolean get() = appLanguage != null
    val translationLanguageChosen: Boolean get() = translationLanguage != null

    companion object {
        /** What an app that has chosen nothing holds. */
        val NONE: LanguageSettings = LanguageSettings()
    }
}

/**
 * The app-private home of the [LanguageSettings] (ADR-0034; the seam ADR-0023
 * established). It is deliberately **not** the **Vault** (ADR-0005): the language
 * a device is used in and translates into is that device's own configuration, so
 * it never appears in an export and does not move between devices.
 */
interface LanguageSettingsStore {

    /** The stored settings, or [LanguageSettings.NONE] when none are stored (or unreadable). */
    fun load(): LanguageSettings

    /** Replaces the stored settings. */
    fun save(settings: LanguageSettings)
}

/** The stored shape, kept private so the document is this file's business alone. */
@Serializable
private data class LanguageSettingsDocument(val appLanguage: String? = null, val translationLanguage: String? = null)

/**
 * The platform [LanguageSettingsStore] (ADR-0034): the two choices as one JSON
 * document over any [VaultFileSystem], the same write-temp-rename byte seam as the
 * vault and the derived assets, rooted elsewhere (ADR-0003, ADR-0010). It is
 * shared by both clients because `java.nio.file` backs it identically; only the
 * root differs. A language is collapsed to its base tag, as the pack and the
 * **Language manifest** already collapse it (ADR-0018).
 */
class JsonLanguageSettingsStore(private val files: VaultFileSystem, private val json: Json = Json) :
    LanguageSettingsStore {

    override fun load(): LanguageSettings {
        val bytes = files.read(PATH) ?: return LanguageSettings.NONE
        val document = runCatching { json.decodeFromString<LanguageSettingsDocument>(bytes.decodeToString()) }
            .getOrDefault(LanguageSettingsDocument())
        return LanguageSettings(document.appLanguage.toTag(), document.translationLanguage.toTag())
    }

    override fun save(settings: LanguageSettings) {
        val document = LanguageSettingsDocument(
            appLanguage = settings.appLanguage?.let(::baseLanguage),
            translationLanguage = settings.translationLanguage?.let(::baseLanguage),
        )
        files.writeAtomically(PATH, json.encodeToString(document).encodeToByteArray())
    }

    companion object {
        /** The single document the store owns, relative to its root. */
        const val PATH: String = "language-settings.json"
    }
}

/** [tag]'s base tag, or `null` when it is absent or blank. */
private fun String?.toTag(): String? = this?.let(::baseLanguage)?.takeIf { language -> language.isNotBlank() }
