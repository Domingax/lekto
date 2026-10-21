package app.lekto.core.theme

import app.lekto.core.vault.VaultFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How Lekto paints itself on this device (CONTEXT.md, "App theme"; ADR-0036): one
 * of three papers — **Light**, **Dark** or **Sepia** — or [SYSTEM], which follows
 * the platform's own light-or-dark setting.
 *
 * It is app-private device configuration on ADR-0023's pattern, never a **Vault**
 * record: it enters no export and no sync, and a second device starts at
 * [SYSTEM]. The choice is not a **Reading preference** (ADR-0035): it recolours
 * the whole app rather than a book's text.
 */
@Serializable
enum class AppTheme {
    /** Follow the platform: Light on a light platform, Dark on a dark one. */
    SYSTEM,

    /** The daylight paper. */
    LIGHT,

    /** Warm night. */
    DARK,

    /** Rag paper, chosen by hand — no operating system has a sepia state. */
    SEPIA,
    ;

    /**
     * The paper this choice means when the platform reports [systemInDark]. A
     * chosen paper is itself; only [SYSTEM] consults the platform, and it resolves
     * to Light or Dark and never to Sepia (ADR-0036).
     */
    fun resolve(systemInDark: Boolean): AppTheme = when (this) {
        SYSTEM -> if (systemInDark) DARK else LIGHT
        else -> this
    }

    companion object {
        /** What a reader who has changed nothing sees: the platform's own setting. */
        val DEFAULT: AppTheme = SYSTEM
    }
}

/**
 * The app-private home of the [AppTheme] (ADR-0036; the seam ADR-0023
 * established). It is deliberately **not** the **Vault** (ADR-0005): how a device
 * looks is that device's own configuration, so it never appears in an export and
 * does not move between devices. A store that cannot be read reports
 * [AppTheme.SYSTEM] rather than throwing.
 */
interface AppThemeStore {

    /** The stored theme, or [AppTheme.SYSTEM] when none is stored. */
    fun load(): AppTheme

    /** Replaces the stored theme. */
    fun save(theme: AppTheme)
}

/**
 * The platform [AppThemeStore] (ADR-0036): the choice as one JSON document over
 * any [VaultFileSystem], the same write-temp-rename byte seam as the vault and the
 * derived assets, rooted elsewhere (ADR-0003, ADR-0010). It is shared by both
 * clients because the same file seam backs them identically; only the root
 * differs.
 *
 * A document this version cannot read — corrupt, or written by a future one whose
 * paper it does not know — is treated as [AppTheme.SYSTEM] rather than a crash,
 * exactly as the provider, language and reading settings degrade (ADR-0023).
 * The document is an object rather than a bare string so a later field can join
 * the choice without changing the shape.
 */
class JsonAppThemeStore(private val files: VaultFileSystem, private val json: Json = Json) : AppThemeStore {

    override fun load(): AppTheme {
        val bytes = files.read(PATH) ?: return AppTheme.SYSTEM
        return runCatching { json.decodeFromString<AppThemeDocument>(bytes.decodeToString()).theme }
            .getOrDefault(AppTheme.SYSTEM)
    }

    override fun save(theme: AppTheme) {
        files.writeAtomically(PATH, json.encodeToString(AppThemeDocument(theme)).encodeToByteArray())
    }

    companion object {
        /** The single document the store owns, relative to its root. */
        const val PATH: String = "app-theme.json"
    }
}

/** The stored shape of an [AppTheme], one document under [JsonAppThemeStore.PATH]. */
@Serializable
internal data class AppThemeDocument(val theme: AppTheme = AppTheme.SYSTEM)
