package app.lekto.core.sync

import app.lekto.testkit.InMemorySyncTarget
import app.lekto.testkit.SyncTargetContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [SyncTargetContract] run against the in-memory driver across every
 * capability combination (ticket #26; ADR-0009). Both gated branches — a change
 * cursor and a compare-and-swap — are exercised, and the no-capability target
 * proves the degraded path, so the contract documents what each capability adds
 * (docs/testing.md, "Test levels").
 */
class SyncTargetContractTest :
    FunSpec({

        listOf(
            "with a change cursor and conditional writes" to
                { InMemorySyncTarget(conditionalWrites = true, changeCursor = true) },
            "with conditional writes but no change cursor" to
                { InMemorySyncTarget(conditionalWrites = true, changeCursor = false) },
            "with a change cursor but no conditional writes" to
                { InMemorySyncTarget(conditionalWrites = false, changeCursor = true) },
            "with neither capability" to
                { InMemorySyncTarget(conditionalWrites = false, changeCursor = false) },
        ).forEach { (description, factory) ->
            context(description) {
                SyncTargetContract(factory).cases().forEach { case ->
                    test(case.name) { case.body() }
                }
            }
        }
    })
