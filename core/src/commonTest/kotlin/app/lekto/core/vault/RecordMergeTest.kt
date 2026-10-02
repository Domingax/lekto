package app.lekto.core.vault

import app.lekto.testkit.testVersionedRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The cases the property tests generalise (ticket #14; ADR-0004): later
 * `updatedAt` wins, an `updatedAt` tie breaks on the greater `deviceId`, and a
 * tombstone out-votes an older live record while a newer live record out-votes
 * an older tombstone.
 */
class RecordMergeTest :
    FunSpec({

        test("the later updatedAt wins") {
            val older = testVersionedRecord("a", updatedAtMillis = 1)
            val newer = testVersionedRecord("a", updatedAtMillis = 2)

            RecordMerge.winner(older, newer) shouldBe newer
            RecordMerge.winner(newer, older) shouldBe newer
        }

        test("a tie on updatedAt breaks on the greater deviceId") {
            val low = testVersionedRecord("a", updatedAtMillis = 5, device = "device-a")
            val high = testVersionedRecord("a", updatedAtMillis = 5, device = "device-b")

            RecordMerge.winner(low, high) shouldBe high
            RecordMerge.winner(high, low) shouldBe high
        }

        test("the winner has the maximum updatedAt") {
            val versions = listOf(
                testVersionedRecord("a", updatedAtMillis = 3),
                testVersionedRecord("a", updatedAtMillis = 9, device = "device-b"),
                testVersionedRecord("a", updatedAtMillis = 5, device = "device-c"),
            )

            RecordMerge.mergeAll(versions).updatedAt shouldBe versions[1].updatedAt
        }

        test("a tombstone with a later updatedAt beats a live record") {
            val record = testVersionedRecord("a", updatedAtMillis = 1)
            val tombstone = testVersionedRecord("a", updatedAtMillis = 2, device = "device-b", deleted = true)

            RecordMerge.winner(record, tombstone) shouldBe tombstone
            RecordMerge.winner(tombstone, record) shouldBe tombstone
        }

        test("a live record with a later updatedAt beats an older tombstone") {
            val tombstone = testVersionedRecord("a", updatedAtMillis = 1, deleted = true)
            val record = testVersionedRecord("a", updatedAtMillis = 2, device = "device-b")

            RecordMerge.winner(tombstone, record) shouldBe record
            RecordMerge.winner(record, tombstone) shouldBe record
        }

        test("two tombstones resolve like any two versions") {
            val older = testVersionedRecord("a", updatedAtMillis = 1, deleted = true)
            val newer = testVersionedRecord("a", updatedAtMillis = 2, device = "device-b", deleted = true)

            RecordMerge.winner(older, newer) shouldBe newer
        }

        test("folding a single version returns it unchanged") {
            val only = testVersionedRecord("a", updatedAtMillis = 7)

            RecordMerge.mergeAll(listOf(only)) shouldBe only
        }
    })
