package app.lekto.core.reading

import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.ReadingPreferencesStoreContract
import io.kotest.core.spec.style.FunSpec
import java.io.File
import java.nio.file.Files

/**
 * The [ReadingPreferencesStoreContract] run against the JSON document store over a
 * real app-private directory (ADR-0035; ADR-0023's pattern): the in-memory
 * filesystem proves the format, and this proves the bytes reach a disk through
 * [JvmVaultFileSystem] exactly as the desktop and Android clients write them.
 */
class JsonReadingPreferencesStoreDirectoryTest :
    FunSpec({

        val roots = mutableListOf<File>()

        afterSpec { roots.forEach(File::deleteRecursively) }

        ReadingPreferencesStoreContract {
            val root = Files.createTempDirectory("lekto-reading-preferences").toFile().also(roots::add)
            JsonReadingPreferencesStore(JvmVaultFileSystem(root))
        }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
