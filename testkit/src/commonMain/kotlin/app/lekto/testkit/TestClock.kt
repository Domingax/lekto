package app.lekto.testkit

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A [Clock] whose reading the test controls, so that time never comes from the
 * wall and a run is reproducible.
 *
 * Starts at the epoch by default; move it with [set] or [advanceBy].
 */
class TestClock(private var current: Instant = Instant.fromEpochMilliseconds(0L)) : Clock {

    override fun now(): Instant = current

    /** Fixes the current instant. */
    fun set(instant: Instant) {
        current = instant
    }

    /** Moves the clock forward and returns the new instant. */
    fun advanceBy(duration: Duration): Instant {
        current += duration
        return current
    }
}
