package app.lekto.core.llm

import app.lekto.testkit.InMemoryLlmSettingsStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.LlmSettingsStoreContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [LlmSettingsStoreContract] run against both implementations that
 * live in `commonMain`: the in-memory fake and the JSON document store over an
 * in-memory filesystem (issue #88; ADR-0023; docs/testing.md, "Test levels"). The
 * document store runs the same cases over a real directory in `jvmTest`, so the
 * bytes-on-disk path is proved too.
 */
class LlmSettingsStoreContractTest :
    FunSpec({

        context("the in-memory fake") {
            LlmSettingsStoreContract { InMemoryLlmSettingsStore() }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }

        context("the JSON document store over an in-memory filesystem") {
            LlmSettingsStoreContract { JsonLlmSettingsStore(InMemoryVaultFileSystem()) }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }
    })
