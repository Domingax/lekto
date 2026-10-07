package app.lekto.testkit

import app.lekto.core.sync.Revision
import app.lekto.core.sync.SyncCapabilities
import app.lekto.core.sync.SyncChanges
import app.lekto.core.sync.SyncCursor
import app.lekto.core.sync.SyncItem
import app.lekto.core.sync.SyncTarget
import app.lekto.core.sync.WriteOutcome
import app.lekto.core.vault.VersionedRecord
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * The in-memory [SyncTarget] the contract suite and the engine are tested
 * against: a fake that implements the seam rather than a mock of call
 * sequences, so a test exercises real engine code (docs/research/testing-harness.md
 * §4).
 *
 * It models what a real driver cannot avoid — an opaque [Revision] per write, an
 * optional change cursor over write order, and an optional compare-and-swap —
 * and can be told to lose a write's race so the engine's recovery path is
 * reachable. [listCalls] and [changesCalls] let a test prove which path the
 * engine took, [latency] lets it prove the engine runs under virtual time, and
 * [failNextConditionalWrite] simulates a competing writer.
 */
class InMemorySyncTarget(private val conditionalWrites: Boolean = true, private val changeCursor: Boolean = true) :
    SyncTarget {

    private val items = linkedMapOf<String, SyncItem>()
    private val sequences = mutableMapOf<String, Long>()
    private var sequence = 0L
    private var forcedConflicts = 0

    /** How many times the engine listed the whole target. */
    var listCalls = 0
        private set

    /** How many times the engine asked for a change set. */
    var changesCalls = 0
        private set

    /** A delay every operation waits, so a test can drive the engine's virtual time. */
    var latency: Duration = Duration.ZERO

    override fun capabilities(): SyncCapabilities = SyncCapabilities(conditionalWrites, changeCursor)

    override suspend fun list(): List<SyncItem> {
        listCalls++
        delay(latency)
        return items.values.sortedBy { it.id }
    }

    override suspend fun get(id: String): SyncItem? {
        delay(latency)
        return items[id]
    }

    override suspend fun changes(cursor: SyncCursor?): SyncChanges {
        changesCalls++
        delay(latency)
        val since = cursor?.let(::sequenceOf) ?: 0L
        val changed = items.values.filter { sequences.getValue(it.id) > since }.sortedBy { it.id }
        return SyncChanges(changed, SyncCursor("seq-$sequence"))
    }

    override suspend fun put(version: VersionedRecord, expected: Revision?): WriteOutcome {
        delay(latency)
        val current = items[version.id]
        return when {
            forcedConflicts > 0 -> {
                forcedConflicts--
                WriteOutcome.Conflicted(current)
            }

            conditionalWrites && !revisionMatches(expected, current) -> WriteOutcome.Conflicted(current)

            else -> store(version)
        }
    }

    /**
     * Makes the next write behave as if a competing writer moved the revision
     * between the engine reading it and writing it, so its recovery path runs.
     */
    fun failNextConditionalWrite() {
        forcedConflicts++
    }

    /**
     * Stores [version] as if a competing writer landed it, without advancing the
     * change sequence, so a cursor feed does not report it and the engine's view
     * of the target stays stale — the race a conditional write exists to catch.
     */
    fun inject(version: VersionedRecord) {
        items[version.id] = SyncItem(version, Revision("injected-${version.id}"))
        sequences[version.id] = sequence
    }

    /** The item [id] holds now, without suspending, for an assertion. */
    fun stored(id: String): SyncItem? = items[id]

    private fun revisionMatches(expected: Revision?, current: SyncItem?): Boolean =
        if (expected == null) current == null else expected == current?.revision

    private fun store(version: VersionedRecord): WriteOutcome {
        val revision = Revision("rev-${++sequence}")
        items[version.id] = SyncItem(version, revision)
        sequences[version.id] = sequence
        return WriteOutcome.Written(revision)
    }

    private fun sequenceOf(cursor: SyncCursor): Long = cursor.value.removePrefix("seq-").toLong()
}
