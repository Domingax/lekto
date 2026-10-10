package app.lekto

import android.content.Context
import app.lekto.core.Seams
import app.lekto.core.sync.JsonSyncSettingsStore
import app.lekto.core.sync.JsonTombstoneStore
import app.lekto.core.sync.SyncEngine
import app.lekto.core.sync.SyncSettings
import app.lekto.core.sync.SyncTarget
import app.lekto.core.vault.DeviceIdFile
import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.integrations.webdav.WebDavSyncTarget
import app.lekto.settings.SyncServices
import java.io.File

/**
 * The Android wiring of sync (issue #116; ADR-0009, ADR-0026): the WebDAV driver
 * is now reachable from the first-class client, so this entry point is where the
 * Android target is built. Everything is app-private under `Context.filesDir`
 * (ADR-0010): the non-secret endpoint and the enable flag live in a document
 * under `sync/`, the tombstones live beside them (never in the vault, ADR-0005),
 * and the engine reads the vault the library writes — the same records the
 * reader and the vocabulary use.
 *
 * It mirrors `desktopSync`; only the root differs. The **application password**
 * is not here: it lives in the `Secret store` (ADR-0021) the composition root
 * supplies.
 */
fun androidSync(context: Context): SyncServices {
    val root = androidVaultRoot(context)
    val state = File(context.filesDir, "sync")
    return SyncServices(
        settings = JsonSyncSettingsStore(JvmVaultFileSystem(state)),
        target = { settings, password -> androidWebDavTarget(settings, password) },
        engine = { target -> androidSyncEngine(root, state, target) },
    )
}

private fun androidWebDavTarget(settings: SyncSettings, password: String): SyncTarget =
    WebDavSyncTarget(settings.serverUrl, settings.username, password)

private fun androidSyncEngine(root: File, state: File, target: SyncTarget): SyncEngine = SyncEngine(
    local = JsonVaultStore(JvmVaultFileSystem(root)),
    tombstones = JsonTombstoneStore(JvmVaultFileSystem(state)),
    target = target,
    clock = Seams.system().clock,
    deviceId = DeviceIdFile(File(root.parentFile, "device-id")).get(),
)
