package app.lekto.core.language

import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.LanguageSettingsStoreContract
import io.kotest.core.spec.style.FunSpec
import java.io.File
import java.nio.file.Files

/**
 * The [LanguageSettingsStoreContract] run against the JSON document store over a
 * real app-private directory (ADR-0034; ADR-0023's pattern): the in-memory
 * filesystem proves the format, and this proves the bytes reach a disk through
 * [JvmVaultFileSystem] exactly as the desktop and Android clients write them.
 */
class JsonLanguageSettingsStoreDirectoryTest :
    FunSpec({

        val roots = mutableListOf<File>()

        afterSpec { roots.forEach(File::deleteRecursively) }

        LanguageSettingsStoreContract {
            val root = Files.createTempDirectory("lekto-language-settings").toFile().also(roots::add)
            JsonLanguageSettingsStore(JvmVaultFileSystem(root))
        }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
