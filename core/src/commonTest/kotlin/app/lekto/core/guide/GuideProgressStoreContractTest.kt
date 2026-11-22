package app.lekto.core.guide

import app.lekto.testkit.GuideProgressStoreContract
import app.lekto.testkit.InMemoryGuideProgressStore
import app.lekto.testkit.InMemoryVaultFileSystem
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [GuideProgressStoreContract] run against both implementations that
 * live in `commonMain`: the in-memory fake and the JSON document store over an
 * in-memory filesystem (ADR-0023's pattern; docs/testing.md, "Test levels"). The
 * document store runs the same cases over a real directory in `jvmTest`, so the
 * bytes-on-disk path is proved too.
 */
class GuideProgressStoreContractTest :
    FunSpec({

        context("the in-memory fake") {
            GuideProgressStoreContract { InMemoryGuideProgressStore() }.cases().forEach { case ->
                test(case.name) { case.body() }
            }
        }

        context("the JSON document store over an in-memory filesystem") {
            GuideProgressStoreContract { JsonGuideProgressStore(InMemoryVaultFileSystem()) }.cases()
                .forEach { case ->
                    test(case.name) { case.body() }
                }
        }
    })
