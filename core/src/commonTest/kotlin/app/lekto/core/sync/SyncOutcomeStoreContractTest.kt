package app.lekto.core.sync

import app.lekto.testkit.InMemorySyncOutcomeStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.SyncOutcomeStoreContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [SyncOutcomeStoreContract] run against both implementations that
 * live in `commonMain`: the in-memory fake and the JSON document store over an
 * in-memory filesystem (issue #121; ADR-0028; docs/testing.md, "Test levels").
 * The document store runs the same cases over a real directory in `jvmTest`, so
 * the bytes-on-disk path is proved too.
 */
class SyncOutcomeStoreContractTest :
    FunSpec({

        context("the in-memory fake") {
            SyncOutcomeStoreContract { InMemorySyncOutcomeStore() }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }

        context("the JSON document store over an in-memory filesystem") {
            SyncOutcomeStoreContract { JsonSyncOutcomeStore(InMemoryVaultFileSystem()) }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }
    })
