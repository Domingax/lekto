package app.lekto.core.sync

import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.TombstoneStoreContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [TombstoneStoreContract] run against both implementations (ticket
 * #26; ADR-0015): the in-memory fake and the JSON store over an in-memory
 * filesystem, so the engine's deletion memory cannot drift between them
 * (docs/testing.md, "Test levels").
 */
class TombstoneStoreContractTest :
    FunSpec({

        TombstoneStoreContract { InMemoryTombstoneStore() }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
        TombstoneStoreContract { JsonTombstoneStore(InMemoryVaultFileSystem()) }.cases().forEach { case ->
            test("json store: ${case.name}") { case.body() }
        }
    })
