package app.lekto.core.book

import app.lekto.testkit.InMemorySampleDismissedStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.SampleDismissedStoreContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [SampleDismissedStoreContract] run against both implementations that
 * live in `commonMain`: the in-memory fake and the JSON document store over an
 * in-memory filesystem (issue #278; docs/testing.md, "Test levels"). The document
 * store runs the same cases over a real directory in `jvmTest`, so the
 * bytes-on-disk path is proved too.
 */
class SampleDismissedStoreContractTest :
    FunSpec({

        context("the in-memory fake") {
            SampleDismissedStoreContract { InMemorySampleDismissedStore() }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }

        context("the JSON document store over an in-memory filesystem") {
            SampleDismissedStoreContract { JsonSampleDismissedStore(InMemoryVaultFileSystem()) }.cases()
                .forEach { case ->
                    test(case.name) { case.body() }
                }
        }
    })
