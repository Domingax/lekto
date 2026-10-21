package app.lekto.core.theme

import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.AppThemeStoreContract
import io.kotest.core.spec.style.FunSpec
import java.io.File
import java.nio.file.Files

/**
 * The [AppThemeStoreContract] run against the JSON document store over a real
 * app-private directory (ADR-0036; ADR-0023's pattern): the in-memory filesystem
 * proves the format, and this proves the bytes reach a disk through
 * [JvmVaultFileSystem] exactly as the desktop and Android clients write them.
 */
class JsonAppThemeStoreDirectoryTest :
    FunSpec({

        val roots = mutableListOf<File>()

        afterSpec { roots.forEach(File::deleteRecursively) }

        AppThemeStoreContract {
            val root = Files.createTempDirectory("lekto-app-theme").toFile().also(roots::add)
            JsonAppThemeStore(JvmVaultFileSystem(root))
        }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
