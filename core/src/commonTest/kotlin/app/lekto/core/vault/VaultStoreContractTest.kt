package app.lekto.core.vault

import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.VaultStoreContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [VaultStoreContract] run against the in-memory store: the seam's
 * specification, proved without a disk so the fast lane covers it (ticket #12;
 * docs/testing.md, "Test levels"). The JVM file store runs the same cases in
 * `jvmTest`.
 */
class VaultStoreContractTest :
    FunSpec({

        VaultStoreContract { InMemoryVaultStore() }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
