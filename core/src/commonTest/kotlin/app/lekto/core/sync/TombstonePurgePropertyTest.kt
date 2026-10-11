package app.lekto.core.sync

import app.lekto.core.vault.VersionedRecord
import app.lekto.testkit.InMemoryRemoteIndexStore
import app.lekto.testkit.InMemorySyncTarget
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.TOMBSTONE_RETENTION_MILLIS
import app.lekto.testkit.TestClock
import app.lekto.testkit.testTombstone
import app.lekto.testkit.testVaultRecord
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.time.Instant

/**
 * The tombstone store stays bounded by time (issue #120; ADR-0015), proved by a
 * generated workload: a set of deletions spread over the last few windows, a
 * "now" to sync at, and a device that returns after the window.
 *
 * After the sync the engine holds a tombstone only for the deletions within the
 * window (retain), the target no longer holds the older ones (purge), and the
 * device that returns past the window is treated as new — it adopts the target
 * rather than pushing its stale live copies, so none of the deleted items
 * resurrects (resync). The same workload over a generated clock is the property
 * that a fixed example could only sample.
 */
@OptIn(ExperimentalKotest::class)
class TombstonePurgePropertyTest :
    FunSpec({

        test("a generated retain/purge/resync workload keeps the tombstones bounded by the window") {
            checkAll(
                PropTestConfig(seed = 20261201, iterations = 200),
                Arb.list(Arb.long(0L..(3 * TOMBSTONE_RETENTION_MILLIS)), 0..25),
                Arb.long(0L..(3 * TOMBSTONE_RETENTION_MILLIS)),
            ) { deletionTimes, nowMillis ->
                val target = InMemorySyncTarget()
                val tombstones = InMemoryTombstoneStore()
                val clock = TestClock()
                val engine = SyncEngine(
                    local = InMemoryVaultStore(),
                    tombstones = tombstones,
                    remoteIndex = InMemoryRemoteIndexStore(),
                    target = target,
                    clock = clock,
                )
                deletionTimes.forEachIndexed { index, at ->
                    target.put(VersionedRecord.of(testTombstone("tomb-$index", updatedAtMillis = at)), null)
                }
                clock.set(Instant.fromEpochMilliseconds(nowMillis))

                engine.sync()

                // Retain and purge: exactly the tombstones within the window survive.
                val retained = deletionTimes.indices
                    .filter { index -> nowMillis - deletionTimes[index] <= TOMBSTONE_RETENTION_MILLIS }
                    .map { index -> "tomb-$index" }
                    .sorted()
                tombstones.all().map { it.id }.sorted() shouldBe retained
                target.list().items.map { it.id }.sorted() shouldBe retained

                // Resync: a device that returns past the window is treated as new.
                val staleVault = InMemoryVaultStore()
                deletionTimes.indices.forEach { index ->
                    staleVault.put(testVaultRecord("tomb-$index", updatedAtMillis = 0, device = "device-a"))
                }
                val stale = SyncEngine(
                    local = staleVault,
                    tombstones = InMemoryTombstoneStore(),
                    remoteIndex = InMemoryRemoteIndexStore(
                        initialLastSyncedAt = Instant.fromEpochMilliseconds(nowMillis - TOMBSTONE_RETENTION_MILLIS - 1),
                    ),
                    target = target,
                    clock = clock,
                )

                val report = stale.sync()

                report.resynced shouldBe true
                staleVault.all() shouldBe emptyList()
                target.list().items.none { it.version is VersionedRecord.Live } shouldBe true
            }
        }
    })
