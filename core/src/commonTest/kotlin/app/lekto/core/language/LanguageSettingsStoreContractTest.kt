package app.lekto.core.language

import app.lekto.testkit.InMemoryLanguageSettingsStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.LanguageSettingsStoreContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [LanguageSettingsStoreContract] run against both implementations that
 * live in `commonMain`: the in-memory fake and the JSON document store over an
 * in-memory filesystem (ADR-0034; docs/testing.md, "Test levels"). The document
 * store runs the same cases over a real directory in `jvmTest`, so the
 * bytes-on-disk path is proved too.
 */
class LanguageSettingsStoreContractTest :
    FunSpec({

        context("the in-memory fake") {
            LanguageSettingsStoreContract { InMemoryLanguageSettingsStore() }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }

        context("the JSON document store over an in-memory filesystem") {
            LanguageSettingsStoreContract { JsonLanguageSettingsStore(InMemoryVaultFileSystem()) }.cases()
                .forEach { case ->
                    test(case.name) { case.body() }
                }
        }
    })
