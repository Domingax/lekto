package app.lekto.core.vault

import app.lekto.testkit.testVersionedRecord
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.forAll

/**
 * The record id every generated version in one property run shares, so versions
 * compete rather than coexist: distinct ids would never meet in
 * [RecordMerge.winner].
 */
private const val ONE_ID = "record"

/** The device ids a generated version may carry; two distinct ones are the tie-break's premise. */
private val DEVICES = arrayOf("device-a", "device-b", "device-c")

/**
 * A [VersionedRecord] built from generated pieces: a live record half the time
 * and a tombstone the other half, with a generated timestamp and a device id.
 */
private val anyVersion: Arb<VersionedRecord> = Arb.bind(
    Arb.boolean(),
    Arb.long(-1_000_000L..1_000_000L),
    Arb.element(*DEVICES),
) { deleted, millis, device -> testVersionedRecord(ONE_ID, millis, device, deleted) }

/**
 * Two versions at the *same* timestamp, on distinct device ids — the exact shape
 * the `deviceId` tie-break exists for, which a random timestamp would almost
 * never produce. The second device id is `"device-b"` plus a suffix, so it is
 * always greater than the first's `"device-a"`.
 */
private val tieTimestamp: Arb<Long> = Arb.long(-1_000_000L..1_000_000L)
private val firstDeleted: Arb<Boolean> = Arb.boolean()
private val secondDeleted: Arb<Boolean> = Arb.boolean()
private val secondDevice: Arb<String> = Arb.element("device-b", "device-c", "device-d")

/**
 * The merge as a property (ticket #14; ADR-0004): the ticket's acceptance
 * criteria, each over generated data.
 *
 * The subject is [RecordMerge], which is pure and operates on one record id at a
 * time. The whole-vault convergence these properties underpin is pinned
 * separately in [VaultMergePropertyTest].
 */
@OptIn(ExperimentalKotest::class)
class RecordMergePropertyTest :
    FunSpec({

        test("merging a record with itself changes nothing (idempotent)") {
            forAll(PropTestConfig(seed = 20261030, iterations = 400), anyVersion) { version ->
                RecordMerge.winner(version, version) == version &&
                    RecordMerge.mergeAll(listOf(version)) == version
            }
        }

        test("merging is commutative for distinct device ids") {
            forAll(
                PropTestConfig(seed = 20261031, iterations = 400),
                tieTimestamp,
                firstDeleted,
                secondDeleted,
                secondDevice,
            ) { millis, firstDead, secondDead, secondId ->
                val first = testVersionedRecord(ONE_ID, millis, "device-a", firstDead)
                val second = testVersionedRecord(ONE_ID, millis, secondId, secondDead)

                first.deviceId != second.deviceId &&
                    RecordMerge.winner(first, second) == RecordMerge.winner(second, first)
            }
        }

        test("an updatedAt tie breaks on the greater deviceId") {
            forAll(
                PropTestConfig(seed = 20261032, iterations = 400),
                tieTimestamp,
                firstDeleted,
                secondDeleted,
                secondDevice,
            ) { millis, firstDead, secondDead, secondId ->
                val first = testVersionedRecord(ONE_ID, millis, "device-a", firstDead)
                val second = testVersionedRecord(ONE_ID, millis, secondId, secondDead)
                val winner = RecordMerge.winner(first, second)
                val loser = if (winner == first) second else first

                winner.updatedAt == loser.updatedAt && winner.deviceId.value > loser.deviceId.value
            }
        }

        test("folding the same versions in any order yields the same result") {
            val versions = Arb.list(anyVersion, 1..6)
            forAll(PropTestConfig(seed = 20261033, iterations = 300), versions) { list ->
                val expected = RecordMerge.mergeAll(list)
                list.indices.all { drop -> RecordMerge.mergeAll(list.rotated(drop)) == expected }
            }
        }

        test("a tombstone with a later timestamp beats a live record") {
            forAll(
                PropTestConfig(seed = 20261034, iterations = 300),
                Arb.long(-1_000_000L..1_000_000L),
                Arb.element(*DEVICES),
            ) { base, device ->
                val live = testVersionedRecord(ONE_ID, base, device)
                val tombstone = testVersionedRecord(ONE_ID, base + 1, device, deleted = true)

                RecordMerge.winner(live, tombstone) == tombstone &&
                    RecordMerge.winner(tombstone, live) == tombstone
            }
        }

        test("the winner is the version with the maximum timestamp") {
            val versions = Arb.list(anyVersion, 1..6)
            forAll(PropTestConfig(seed = 20261035, iterations = 300), versions) { list ->
                val winner = RecordMerge.mergeAll(list)
                list.all { it.updatedAt <= winner.updatedAt }
            }
        }
    })

/** This list starting at [drop], wrapping — a distinct order for every index. */
private fun <T> List<T>.rotated(drop: Int): List<T> = drop(drop % size) + take(drop % size)
