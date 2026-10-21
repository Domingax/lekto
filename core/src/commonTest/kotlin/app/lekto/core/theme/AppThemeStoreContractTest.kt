package app.lekto.core.theme

import app.lekto.testkit.AppThemeStoreContract
import app.lekto.testkit.InMemoryAppThemeStore
import app.lekto.testkit.InMemoryVaultFileSystem
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [AppThemeStoreContract] run against both implementations that live in
 * `commonMain`: the in-memory fake and the JSON document store over an in-memory
 * filesystem (ADR-0036; docs/testing.md, "Test levels"). The document store runs
 * the same cases over a real directory in `jvmTest`, so the bytes-on-disk path is
 * proved too.
 */
class AppThemeStoreContractTest :
    FunSpec({

        context("the in-memory fake") {
            AppThemeStoreContract { InMemoryAppThemeStore() }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }

        context("the JSON document store over an in-memory filesystem") {
            AppThemeStoreContract { JsonAppThemeStore(InMemoryVaultFileSystem()) }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }
    })
