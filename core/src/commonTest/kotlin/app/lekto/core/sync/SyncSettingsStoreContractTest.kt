package app.lekto.core.sync

import app.lekto.testkit.InMemorySyncSettingsStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.SyncSettingsStoreContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [SyncSettingsStoreContract] run against both implementations that
 * live in `commonMain`: the in-memory fake and the JSON document store over an
 * in-memory filesystem (issue #28; docs/testing.md, "Test levels"). The document
 * store runs the same cases over a real directory in `jvmTest`, so the
 * bytes-on-disk path is proved too.
 */
class SyncSettingsStoreContractTest :
    FunSpec({

        context("the in-memory fake") {
            SyncSettingsStoreContract { InMemorySyncSettingsStore() }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }

        context("the JSON document store over an in-memory filesystem") {
            SyncSettingsStoreContract { JsonSyncSettingsStore(InMemoryVaultFileSystem()) }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }
    })
