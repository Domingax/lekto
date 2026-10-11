package app.lekto.core.sync

import app.lekto.testkit.InMemoryRemoteIndexStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.RemoteIndexStoreContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [RemoteIndexStoreContract] run against both implementations that
 * live in `commonMain`: the in-memory fake and the JSON document store over an
 * in-memory filesystem (ADR-0027; docs/testing.md, "Test levels"). The document
 * store runs the same cases over a real directory in `jvmTest`, so the
 * bytes-on-disk path is proved too.
 */
class RemoteIndexStoreContractTest :
    FunSpec({

        context("the in-memory fake") {
            RemoteIndexStoreContract { InMemoryRemoteIndexStore() }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }

        context("the JSON document store over an in-memory filesystem") {
            RemoteIndexStoreContract { JsonRemoteIndexStore(InMemoryVaultFileSystem()) }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }
    })
