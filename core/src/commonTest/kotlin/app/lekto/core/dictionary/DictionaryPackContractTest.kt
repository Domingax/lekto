package app.lekto.core.dictionary

import app.lekto.testkit.DictionaryPackContract
import app.lekto.testkit.InMemoryDictionaryPack
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [DictionaryPackContract] run against the in-memory pack: the seam's
 * specification, proved without a SQLite file in the fast lane. The real reader
 * runs the same cases against a built pack in `jvmTest` and `androidHostTest`
 * (issue #18; docs/testing.md, "Test levels").
 */
class DictionaryPackContractTest :
    FunSpec({

        DictionaryPackContract { InMemoryDictionaryPack() }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
