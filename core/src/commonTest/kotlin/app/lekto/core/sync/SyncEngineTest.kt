package app.lekto.core.sync

import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VersionedRecord
import app.lekto.testkit.InMemorySyncTarget
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultStore
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
                a.engine.delete("word")
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
                a.engine.delete("word")
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

        test("deleting an unknown id leaves nothing behind") {
            runTest {
                val a = Device("device-a", InMemorySyncTarget())

                a.engine.delete("absent")

                a.tombstones.all() shouldBe emptyList()
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
private class Device(name: String, target: SyncTarget) {
    val vault = InMemoryVaultStore()
    val tombstones = InMemoryTombstoneStore()
    private val clock = TestClock()
    val engine = SyncEngine(vault, tombstones, target, clock, DeviceId(name))

    /** Fixes the clock, so a deletion the engine records has a chosen timestamp. */
    fun at(millis: Long) {
        clock.set(Instant.fromEpochMilliseconds(millis))
    }
}
