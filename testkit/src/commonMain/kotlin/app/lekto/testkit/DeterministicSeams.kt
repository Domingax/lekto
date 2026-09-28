package app.lekto.testkit

import app.lekto.core.IdGenerator
import app.lekto.core.Seams
import kotlin.random.Random

/**
 * A [Seams] whose every input is deterministic and test-owned: a [TestClock], a
 * [SequentialIdGenerator] and a seeded [Random].
 *
 * Pass the concrete [clock] or [ids] when a test needs to drive them, and the
 * same [random] seed to replay a run.
 */
fun deterministicSeams(
    clock: TestClock = TestClock(),
    ids: IdGenerator = SequentialIdGenerator(),
    random: Random = Random(0),
): Seams = Seams(clock = clock, ids = ids, random = random)
