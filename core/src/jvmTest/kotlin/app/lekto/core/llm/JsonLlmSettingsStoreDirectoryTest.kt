package app.lekto.core.llm

import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.LlmSettingsStoreContract
import io.kotest.core.spec.style.FunSpec
import java.io.File
import java.nio.file.Files

/**
 * The [LlmSettingsStoreContract] run against the JSON document store over a real
 * app-private directory (issue #88; ADR-0023): the in-memory filesystem proves
 * the format, and this proves the bytes reach a disk through
 * [JvmVaultFileSystem] exactly as the desktop and Android clients write them.
 */
class JsonLlmSettingsStoreDirectoryTest :
    FunSpec({

        val roots = mutableListOf<File>()

        afterSpec { roots.forEach(File::deleteRecursively) }

        LlmSettingsStoreContract {
            val root = Files.createTempDirectory("lekto-llm-settings").toFile().also(roots::add)
            JsonLlmSettingsStore(JvmVaultFileSystem(root))
        }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
