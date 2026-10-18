package app.lekto.core.reading

import app.lekto.testkit.InMemoryReadingPreferencesStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.ReadingPreferencesStoreContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [ReadingPreferencesStoreContract] run against both implementations
 * that live in `commonMain`: the in-memory fake and the JSON document store over
 * an in-memory filesystem (ADR-0035; docs/testing.md, "Test levels"). The document
 * store runs the same cases over a real directory in `jvmTest`, so the
 * bytes-on-disk path is proved too.
 */
class ReadingPreferencesStoreContractTest :
    FunSpec({

        context("the in-memory fake") {
            ReadingPreferencesStoreContract { InMemoryReadingPreferencesStore() }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }

        context("the JSON document store over an in-memory filesystem") {
            ReadingPreferencesStoreContract { JsonReadingPreferencesStore(InMemoryVaultFileSystem()) }.cases()
                .forEach { case ->
                    test(case.name) { case.body() }
                }
        }
    })
