package app.lekto.core.vault

import app.lekto.testkit.testTombstone
import app.lekto.testkit.testVaultRecord
import app.lekto.testkit.testVersionedRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The whole-vault merge in the small (ticket #14; ADR-0004): [VaultMerge] names
 * what each side must write or store a tombstone for, and a vanished live record
 * is deleted rather than copied back.
 */
class VaultMergeTest :
    FunSpec({

        test("a record only one side holds is written to the other") {
            val onlyLocal = testVaultRecord("a")
            val onlyRemote = testVaultRecord("b")

            val outcome = VaultMerge.merge(
                local = listOf(VersionedRecord.of(onlyLocal)),
                remote = listOf(VersionedRecord.of(onlyRemote)),
            )

            outcome.toWriteLocally shouldBe listOf(onlyRemote)
            outcome.toWriteRemotely shouldBe listOf(onlyLocal)
            outcome.toDeleteLocally shouldBe emptyList()
            outcome.toDeleteRemotely shouldBe emptyList()
        }

        test("a winning tombstone has the losing side store it") {
            val tombstone = testTombstone("a", updatedAtMillis = 2)
            val outcome = VaultMerge.merge(
                local = listOf(testVersionedRecord("a", updatedAtMillis = 1)),
                remote = listOf(testVersionedRecord("a", updatedAtMillis = 2, deleted = true)),
            )

            outcome.toDeleteLocally shouldBe listOf(tombstone)
            outcome.toWriteLocally shouldBe emptyList()
            outcome.toDeleteRemotely shouldBe emptyList()
            outcome.toWriteRemotely shouldBe emptyList()
        }

        test("a newer live record wins over an older tombstone") {
            val resurrected = testVaultRecord("a", updatedAtMillis = 2)
            val outcome = VaultMerge.merge(
                local = listOf(testVersionedRecord("a", updatedAtMillis = 1, deleted = true)),
                remote = listOf(VersionedRecord.of(resurrected)),
            )

            outcome.toWriteLocally shouldBe listOf(resurrected)
            outcome.toWriteRemotely shouldBe emptyList()
            outcome.toDeleteLocally shouldBe emptyList()
        }

        test("the newer of two live copies is written to the older side") {
            val newer = testVaultRecord("a", updatedAtMillis = 2, device = "device-b")
            val outcome = VaultMerge.merge(
                local = listOf(testVersionedRecord("a", updatedAtMillis = 1)),
                remote = listOf(VersionedRecord.of(newer)),
            )

            outcome.toWriteLocally shouldBe listOf(newer)
            outcome.toWriteRemotely shouldBe emptyList()
            outcome.toDeleteLocally shouldBe emptyList()
            outcome.toDeleteRemotely shouldBe emptyList()
        }

        test("identical versions on both sides leave the outcome empty") {
            val outcome = VaultMerge.merge(
                local = listOf(testVersionedRecord("a", updatedAtMillis = 1)),
                remote = listOf(testVersionedRecord("a", updatedAtMillis = 1)),
            )

            outcome shouldBe MergeOutcome(emptyList(), emptyList(), emptyList(), emptyList())
        }
    })
