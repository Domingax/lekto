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
import io.kotest.property.arbitrary.map
import io.kotest.property.forAll

/** The ids a generated merge works over, a small fixed alphabet so collisions are likely. */
private val MERGE_IDS = arrayOf("a", "b", "c")

/** The device ids a generated version may carry. */
private val DEVICES = arrayOf("device-a", "device-b", "device-c")

/** A version of one of [MERGE_IDS]: a live record or a tombstone, at a generated time. */
private val anySidedVersion: Arb<VersionedRecord> = Arb.bind(
    Arb.element(*MERGE_IDS),
    Arb.boolean(),
    Arb.long(-1_000_000L..1_000_000L),
    Arb.element(*DEVICES),
) { id, deleted, millis, device -> testVersionedRecord(id, millis, device, deleted) }

/** A side of a vault: at most one version per id, as a real vault holds. */
private val anySide: Arb<List<VersionedRecord>> = Arb.list(anySidedVersion, 0..3).map { versions ->
    val byId = LinkedHashMap<String, VersionedRecord>()
    versions.forEach { version -> byId.putIfAbsent(version.id, version) }
    byId.values.toList()
}

/**
 * The whole-vault merge as a property (ticket #14; ADR-0004): the same records,
 * however they are split between two devices and in whatever order the sides are
 * presented, converge on the same result.
 *
 * [RecordMergePropertyTest] proves the per-record comparison; this pins
 * [VaultMerge]'s grouping over a generated alphabet of ids.
 */
@OptIn(ExperimentalKotest::class)
class VaultMergePropertyTest :
    FunSpec({

        test("merging is symmetric: swapping the sides mirrors the outcome") {
            forAll(PropTestConfig(seed = 20261036, iterations = 300), anySide, anySide) { local, remote ->
                val forward = VaultMerge.merge(local, remote)
                val swapped = VaultMerge.merge(remote, local)

                forward.toWriteLocally == swapped.toWriteRemotely &&
                    forward.toWriteRemotely == swapped.toWriteLocally &&
                    forward.toDeleteLocally == swapped.toDeleteRemotely &&
                    forward.toDeleteRemotely == swapped.toDeleteLocally
            }
        }

        test("a winning tombstone deletes the live copy on the other side") {
            forAll(
                PropTestConfig(seed = 20261037, iterations = 300),
                Arb.long(-1_000_000L..1_000_000L),
                Arb.element(*DEVICES),
                Arb.element(*DEVICES),
            ) { base, liveDevice, tombstoneDevice ->
                val live = testVersionedRecord("a", base, liveDevice)
                val dead = testVersionedRecord("a", base + 1, tombstoneDevice, deleted = true)

                val outcome = VaultMerge.merge(local = listOf(live), remote = listOf(dead))

                outcome.toDeleteLocally.map { it.id } == listOf("a") &&
                    outcome.toWriteLocally.isEmpty() &&
                    outcome.toDeleteRemotely.isEmpty()
            }
        }

        test("applying the outcome converges: a second merge has nothing to do") {
            forAll(PropTestConfig(seed = 20261038, iterations = 300), anySide, anySide) { local, remote ->
                val outcome = VaultMerge.merge(local, remote)

                val settledLocal = local.applied(outcome.toWriteLocally, outcome.toDeleteLocally)
                val settledRemote = remote.applied(outcome.toWriteRemotely, outcome.toDeleteRemotely)
                val second = VaultMerge.merge(settledLocal, settledRemote)

                second == MergeOutcome(emptyList(), emptyList(), emptyList(), emptyList())
            }
        }
    })

/**
 * The side after it writes [writes] and stores [deletes]: the versions its
 * counterparty told it to adopt. A local stand-in for what a real
 * [VaultStore]-backed engine would do — records written, tombstones recorded —
 * enough to prove the merge converges.
 */
private fun List<VersionedRecord>.applied(writes: List<VaultRecord>, deletes: List<Tombstone>): List<VersionedRecord> {
    val byId = associateByTo(LinkedHashMap()) { it.id }
    writes.map(VersionedRecord::of).forEach { version -> byId[version.id] = version }
    deletes.map(VersionedRecord::of).forEach { version -> byId[version.id] = version }
    return byId.values.toList()
}
