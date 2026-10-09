package app.lekto.core.sync

import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.SyncSettingsStoreContract
import io.kotest.core.spec.style.FunSpec
import java.io.File
import java.nio.file.Files

/**
 * The [SyncSettingsStoreContract] run against the JSON document store over a real
 * app-private directory (issue #28): the in-memory filesystem proves the format,
 * and this proves the bytes reach a disk through [JvmVaultFileSystem] exactly as
 * the desktop and Android clients write them.
 */
class JsonSyncSettingsStoreDirectoryTest :
    FunSpec({

        val roots = mutableListOf<File>()

        afterSpec { roots.forEach(File::deleteRecursively) }

        SyncSettingsStoreContract {
            val root = Files.createTempDirectory("lekto-sync-settings").toFile().also(roots::add)
            JsonSyncSettingsStore(JvmVaultFileSystem(root))
        }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
