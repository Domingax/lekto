package app.lekto.core.sync

import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.Tombstone
import app.lekto.core.vault.VersionedRecord
import app.lekto.testkit.InMemoryRemoteIndexStore
import app.lekto.testkit.InMemorySyncTarget
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.TOMBSTONE_RETENTION_MILLIS
import app.lekto.testkit.TestClock
import app.lekto.testkit.testTombstone
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The sync engine's behaviours (ticket #26; ADR-0009, ADR-0015): a full sync that
 * reconciles two vaults with no shared history, incremental sync by cursor and by
 * listing, propagation of a deletion, deterministic conflict resolution, and a
 * dispatcher that is never hard-coded.
 *
 * Two [Device]s share one [InMemorySyncTarget], so the engine is exercised as
 * the real thing over a real seam rather than against mocks. All tests run under
 * `runTest`, so the engine's timing is virtual and a failure reproduces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest :
    FunSpec({

        test("initial full sync reconciles two vaults that share no history") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                val b = Device("device-b", target)
                val word = testVaultRecord("word", updatedAtMillis = 1, device = "device-a")
                val progress = testVaultRecord("book", kind = "progress", updatedAtMillis = 2, device = "device-a")
                val phrase = testVaultRecord("phrase", updatedAtMillis = 3, device = "device-b")
                a.vault.put(word)
                a.vault.put(progress)
                b.vault.put(phrase)

                a.engine.sync()
                b.engine.sync()
                a.engine.sync()

                val reconciled = listOf("book", "phrase", "word")
                a.vault.all().map { it.id }.sorted() shouldBe reconciled
                b.vault.all().map { it.id }.sorted() shouldBe reconciled
                b.vault.get("word") shouldBe word
                b.vault.get("book") shouldBe progress
                a.vault.get("phrase") shouldBe phrase
            }
        }

        test("incremental sync uses a change cursor when the target offers one") {
            runTest {
                val target = InMemorySyncTarget(changeCursor = true)
                val a = Device("device-a", target)
                val b = Device("device-b", target)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-a"))
                a.engine.sync()
                b.engine.sync()

                val edited = testVaultRecord("word", updatedAtMillis = 5, device = "device-b")
                b.vault.put(edited)
                b.engine.sync()
                a.engine.sync()

                a.vault.get("word") shouldBe edited
                target.listCalls shouldBe 0
                (target.changesCalls >= 2) shouldBe true
            }
        }

        test("incremental sync falls back to a listing without a cursor") {
            runTest {
                val target = InMemorySyncTarget(changeCursor = false)
                val a = Device("device-a", target)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-a"))

                a.engine.sync()
                a.engine.sync()

                target.changesCalls shouldBe 0
                (target.listCalls >= 2) shouldBe true
            }
        }

        test("a second sync re-reads no remote item when nothing changed") {
            runTest {
                val target = InMemorySyncTarget(changeCursor = false)
                val b = Device("device-b", target)
                b.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-b"))
                b.engine.sync()
                val a = Device("device-a", target)
                a.engine.sync()
                val afterFirst = target.reads

                a.engine.sync()

                (afterFirst >= 1) shouldBe true
                target.reads shouldBe afterFirst
            }
        }

        test("the remembered revisions survive a fresh engine, so a restart re-reads nothing") {
            runTest {
                val target = InMemorySyncTarget(changeCursor = false)
                val b = Device("device-b", target)
                b.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-b"))
                b.engine.sync()
                val index = InMemoryRemoteIndexStore()
                val a = Device("device-a", target, index)
                a.engine.sync()
                val afterFirst = target.reads

                // A new engine over the same device-local index, as the next launch builds.
                Device("device-a", target, index).engine.sync()

                target.reads shouldBe afterFirst
            }
        }

        test("a changed remote record is read again on the next sync") {
            runTest {
                val target = InMemorySyncTarget(changeCursor = false)
                val a = Device("device-a", target)
                val b = Device("device-b", target)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-a"))
                a.engine.sync()
                b.engine.sync()
                val afterFirst = target.reads

                b.vault.put(testVaultRecord("word", updatedAtMillis = 5, device = "device-b"))
                b.engine.sync()
                a.engine.sync()

                (target.reads > afterFirst) shouldBe true
                a.vault.get("word") shouldBe testVaultRecord("word", updatedAtMillis = 5, device = "device-b")
            }
        }

        test("an unchanged id the index does not hold is re-read rather than dropped") {
            runTest {
                val backing = InMemorySyncTarget(changeCursor = false)
                // A driver that names every id unchanged even when the engine has
                // never seen it: the engine must re-read it, not treat it as absent.
                val liar = object : SyncTarget by backing {
                    override suspend fun list(known: Map<String, Revision>): SyncListing {
                        val all = backing.list(emptyMap())
                        return SyncListing(emptyList(), all.items.map { it.id }.toSet())
                    }
                }
                val word = testVaultRecord("word", updatedAtMillis = 1, device = "device-b")
                backing.put(VersionedRecord.of(word), null)
                val a = Device("device-a", liar)

                a.engine.sync()

                a.vault.get("word") shouldBe word
            }
        }

        test("a deletion on one device propagates and does not resurrect") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                val b = Device("device-b", target)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-a"))
                a.engine.sync()
                b.engine.sync()
                b.vault.get("word") shouldBe testVaultRecord("word", updatedAtMillis = 1, device = "device-a")

                a.at(millis = 10)
                a.delete("word")
                a.engine.sync()
                b.engine.sync()

                b.vault.get("word") shouldBe null
                b.tombstones.get("word") shouldBe testTombstone("word", updatedAtMillis = 10, device = "device-a")

                a.engine.sync()
                b.engine.sync()

                a.vault.get("word") shouldBe null
                b.vault.get("word") shouldBe null
                target.stored("word")?.version shouldBe VersionedRecord.of(
                    testTombstone("word", updatedAtMillis = 10, device = "device-a"),
                )
            }
        }

        test("a conflicting edit resolves deterministically by the merge rules") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                val b = Device("device-b", target)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-a"))
                a.engine.sync()
                b.engine.sync()

                val aEdit = testVaultRecord("word", updatedAtMillis = 5, device = "device-a")
                val bEdit = testVaultRecord("word", updatedAtMillis = 5, device = "device-b")
                a.vault.put(aEdit)
                b.vault.put(bEdit)

                a.engine.sync()
                b.engine.sync()
                a.engine.sync()

                a.vault.get("word") shouldBe bEdit
                b.vault.get("word") shouldBe bEdit
                target.stored("word")?.version shouldBe VersionedRecord.of(bEdit)
            }
        }

        test("a conditional write that loses its race is retried against the fresh revision") {
            runTest {
                val target = InMemorySyncTarget(conditionalWrites = true)
                val a = Device("device-a", target)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-a"))
                a.engine.sync()

                val edited = testVaultRecord("word", updatedAtMillis = 5, device = "device-a")
                a.vault.put(edited)
                target.failNextConditionalWrite()
                val report = a.engine.sync()

                report.unresolved shouldBe 0
                a.vault.get("word") shouldBe edited
                target.stored("word")?.version shouldBe VersionedRecord.of(edited)
            }
        }

        test("a conditional write that loses its race to a newer version adopts it") {
            runTest {
                val target = InMemorySyncTarget(conditionalWrites = true)
                val a = Device("device-a", target)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-a"))
                a.engine.sync()

                // A competing writer lands a later version the change feed has not
                // reported yet, so the engine still believes it holds the old one.
                val rival = testVaultRecord("word", updatedAtMillis = 9, device = "device-z")
                target.inject(VersionedRecord.of(rival))
                a.vault.put(testVaultRecord("word", updatedAtMillis = 5, device = "device-a"))
                target.failNextConditionalWrite()
                a.engine.sync()

                a.vault.get("word") shouldBe rival
                a.tombstones.get("word") shouldBe null
                target.stored("word")?.version shouldBe VersionedRecord.of(rival)
            }
        }

        test("a record re-saved after a deletion supersedes the tombstone") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                val b = Device("device-b", target)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-a"))
                a.engine.sync()
                b.engine.sync()

                a.at(millis = 10)
                a.delete("word")
                a.engine.sync()
                b.engine.sync()

                val resaved = testVaultRecord("word", updatedAtMillis = 20, device = "device-a")
                a.vault.put(resaved)
                a.engine.sync()
                b.engine.sync()

                a.vault.get("word") shouldBe resaved
                b.vault.get("word") shouldBe resaved
                a.tombstones.get("word") shouldBe null
                b.tombstones.get("word") shouldBe null
            }
        }

        test("an imported book's original reaches the other device") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                val b = Device("device-b", target)
                val book = testVaultRecord("book", kind = "books", updatedAtMillis = 1, device = "device-a")
                val original = byteArrayOf(0, 1, 2, 3, -1, 127)
                a.vault.put(book)
                a.vault.putAttachment("book", original)

                a.engine.sync()
                b.engine.sync()

                b.vault.get("book") shouldBe book
                (b.vault.getAttachment("book")?.contentEquals(original)) shouldBe true
                ("book" in target.attachmentIds()) shouldBe true
            }
        }

        test("a deletion removes a book's original on both devices") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                val b = Device("device-b", target)
                a.vault.put(testVaultRecord("book", kind = "books", updatedAtMillis = 1, device = "device-a"))
                a.vault.putAttachment("book", byteArrayOf(1, 2, 3))
                a.engine.sync()
                b.engine.sync()
                (b.vault.getAttachment("book")?.contentEquals(byteArrayOf(1, 2, 3))) shouldBe true

                a.at(millis = 10)
                a.delete("book")
                a.engine.sync()
                b.engine.sync()

                a.vault.getAttachment("book") shouldBe null
                b.vault.getAttachment("book") shouldBe null
                ("book" in target.attachmentIds()) shouldBe false
            }
        }

        test("an original is uploaded once, not on every sync") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                a.vault.put(testVaultRecord("book", kind = "books", updatedAtMillis = 1, device = "device-a"))
                a.vault.putAttachment("book", byteArrayOf(1))

                a.engine.sync()
                val afterFirst = target.attachmentWrites
                a.engine.sync()

                afterFirst shouldBe 1
                target.attachmentWrites shouldBe afterFirst
            }
        }

        test("a tombstone older than the retention window is dropped, on the target too") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                a.at(0)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 0, device = "device-a"))
                a.delete("word")
                a.engine.sync()
                target.stored("word")?.version shouldBe VersionedRecord.of(testTombstone("word", 0, "device-a"))

                a.at(TOMBSTONE_RETENTION_MILLIS + 1)
                a.engine.sync()

                a.tombstones.get("word") shouldBe null
                target.stored("word") shouldBe null
            }
        }

        test("a tombstone within the retention window is kept") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                a.at(0)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 0, device = "device-a"))
                a.delete("word")
                a.engine.sync()

                a.at(TOMBSTONE_RETENTION_MILLIS)
                a.engine.sync()

                a.tombstones.get("word") shouldBe testTombstone("word", 0, "device-a")
                target.stored("word")?.version shouldBe VersionedRecord.of(testTombstone("word", 0, "device-a"))
            }
        }

        test("a tombstone whose deletion has not reached the target is kept") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                // The target still holds a live copy: this device's deletion never
                // propagated, so dropping the tombstone would resurrect the item.
                val live = testVaultRecord("word", updatedAtMillis = 0, device = "device-b")
                target.put(VersionedRecord.of(live), null)
                a.at(1)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 0, device = "device-a"))
                a.delete("word")

                a.at(TOMBSTONE_RETENTION_MILLIS + 2)
                a.engine.sync()

                a.tombstones.get("word") shouldBe testTombstone("word", 1, "device-a")
                target.stored("word")?.version shouldBe VersionedRecord.of(testTombstone("word", 1, "device-a"))
            }
        }

        test("a device away past the window resyncs, so a stale copy cannot resurrect") {
            runTest {
                val target = InMemorySyncTarget()
                val creator = Device("device-b", target)
                val purger = Device("device-c", target)
                val stale = Device("device-a", target)

                creator.at(0)
                creator.vault.put(testVaultRecord("word", updatedAtMillis = 0, device = "device-b"))
                val book = testVaultRecord("book", kind = "books", updatedAtMillis = 0, device = "device-b")
                creator.vault.put(book)
                creator.engine.sync()
                stale.at(0)
                stale.engine.sync()
                purger.at(0)
                purger.engine.sync()
                stale.vault.get("word") shouldBe testVaultRecord("word", updatedAtMillis = 0, device = "device-b")
                stale.vault.get("book") shouldBe book

                creator.at(1)
                creator.delete("word")
                creator.engine.sync()
                purger.at(2)
                purger.engine.sync()

                // The purger has synced since the delete, so it is not new; it
                // drops the tombstone once it is past the window.
                purger.at(TOMBSTONE_RETENTION_MILLIS + 2)
                purger.engine.sync()
                target.stored("word") shouldBe null

                // The stale device returns past the window and is treated as new:
                // it adopts the target whole, including the record it had already
                // seen, rather than pushing its stale copy of the deleted one.
                stale.at(TOMBSTONE_RETENTION_MILLIS + 3)
                val report = stale.engine.sync()

                report.resynced shouldBe true
                stale.vault.get("book") shouldBe book
                stale.vault.get("word") shouldBe null
                target.stored("word") shouldBe null
            }
        }

        test("a device away within the window keeps its records and does not resync") {
            runTest {
                val target = InMemorySyncTarget()
                val a = Device("device-a", target)
                a.at(0)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 0, device = "device-a"))
                a.engine.sync()

                a.at(TOMBSTONE_RETENTION_MILLIS)
                val report = a.engine.sync()

                report.resynced shouldBe false
                a.vault.get("word") shouldBe testVaultRecord("word", updatedAtMillis = 0, device = "device-a")
            }
        }

        test("the engine runs on the caller's virtual time, not a hard-coded dispatcher") {
            runTest {
                val target = InMemorySyncTarget()
                target.latency = 5.minutes
                val a = Device("device-a", target)
                a.vault.put(testVaultRecord("word", updatedAtMillis = 1, device = "device-a"))

                a.engine.sync()

                testScheduler.currentTime shouldBeGreaterThanOrEqual 5.minutes.inWholeMilliseconds
            }
        }
    })

/**
 * One device in a sync scenario: its vault, its deletion memory, its clock and
 * the engine that ties them to a shared [target]. Each [Device] has a distinct
 * [DeviceId], so the merge's tie-break is exercised rather than shadowed.
 */
private class Device(name: String, target: SyncTarget, remoteIndex: RemoteIndexStore = InMemoryRemoteIndexStore()) {
    val vault = InMemoryVaultStore()
    val tombstones = InMemoryTombstoneStore()
    private val clock = TestClock()
    private val deviceId = DeviceId(name)
    val engine = SyncEngine(vault, tombstones, remoteIndex, target, clock)

    /** Fixes the clock, so a delete's tombstone carries a chosen timestamp. */
    fun at(millis: Long) {
        clock.set(Instant.fromEpochMilliseconds(millis))
    }

    /**
     * Deletes [id] the way a user-facing writer does: remove the record and leave
     * the tombstone the merge compares on beside it (issue #131). The engine has no
     * delete of its own (#137), so an id the vault does not hold is a no-op, as it
     * is for the vocabulary and the library.
     */
    fun delete(id: String) {
        if (vault.get(id) == null) return
        vault.remove(id)
        tombstones.put(Tombstone(id = id, updatedAt = clock.now(), deviceId = deviceId))
    }
}
