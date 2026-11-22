package app.lekto.core.guide

import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.GuideProgressStoreContract
import io.kotest.core.spec.style.FunSpec
import java.io.File
import java.nio.file.Files

/**
 * The [GuideProgressStoreContract] run against the JSON document store over a
 * real app-private directory (ADR-0023's pattern; issue #277): the in-memory
 * filesystem proves the format, and this proves the bytes reach a disk through
 * [JvmVaultFileSystem] exactly as the desktop and Android clients write them.
 */
class JsonGuideProgressStoreDirectoryTest :
    FunSpec({

        val roots = mutableListOf<File>()

        afterSpec { roots.forEach(File::deleteRecursively) }

        GuideProgressStoreContract {
            val root = Files.createTempDirectory("lekto-guide-progress").toFile().also(roots::add)
            JsonGuideProgressStore(JvmVaultFileSystem(root))
        }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
