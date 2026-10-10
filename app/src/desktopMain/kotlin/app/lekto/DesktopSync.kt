package app.lekto

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
 * The desktop wiring of sync (issue #28; ADR-0009, ADR-0025): the WebDAV driver
 * is one real target, built here for the JVM client. The non-secret endpoint and
 * the enable flag live in an app-private document under `sync/`, the tombstones
 * live beside them (never in the vault, ADR-0005), and the engine reads the vault
 * the library writes — all under [root]'s parent, so the same records sync that
 * the reader and the vocabulary use. `androidSync` is the mirror for the Android
 * client (issue #116; ADR-0026).
 *
 * [root] is the test seam, as [desktopVaultStore]'s is; production never passes
 * one.
 */
fun desktopSync(root: File = desktopVaultRoot()): SyncServices {
    val state = File(root.parentFile, "sync")
    return SyncServices(
        settings = JsonSyncSettingsStore(JvmVaultFileSystem(state)),
        target = { settings, password -> webDavTarget(settings, password) },
        engine = { target -> desktopSyncEngine(root, state, target) },
    )
}

private fun webDavTarget(settings: SyncSettings, password: String): SyncTarget =
    WebDavSyncTarget(settings.serverUrl, settings.username, password)

private fun desktopSyncEngine(root: File, state: File, target: SyncTarget): SyncEngine = SyncEngine(
    local = JsonVaultStore(JvmVaultFileSystem(root)),
    tombstones = JsonTombstoneStore(JvmVaultFileSystem(state)),
    target = target,
    clock = Seams.system().clock,
    deviceId = DeviceIdFile(File(root.parentFile, "device-id")).get(),
)
