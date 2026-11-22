package app.lekto.core.book

import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.SampleDismissedStoreContract
import io.kotest.core.spec.style.FunSpec
import java.io.File
import java.nio.file.Files

/**
 * The [SampleDismissedStoreContract] run against the JSON document store over a
 * real app-private directory (issue #278; ADR-0023's pattern): the in-memory
 * filesystem proves the format, and this proves the bytes reach a disk through
 * [JvmVaultFileSystem] exactly as the desktop and Android clients write them.
 */
class JsonSampleDismissedStoreDirectoryTest :
    FunSpec({

        val roots = mutableListOf<File>()

        afterSpec { roots.forEach(File::deleteRecursively) }

        SampleDismissedStoreContract {
            val root = Files.createTempDirectory("lekto-sample-dismissed").toFile().also(roots::add)
            JsonSampleDismissedStore(JvmVaultFileSystem(root))
        }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
