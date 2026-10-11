package app.lekto.core.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The policy for automatic sync runs while the app is open (issue #121;
 * ADR-0028): how long to wait between runs, and how that wait grows when runs
 * keep failing.
 *
 * The interval is bounded — a vault of ten thousand records is not a reason to
 * poll often, so the base is a quarter of an hour — and the wait doubles with
 * each consecutive failure up to [maxInterval], so a server that is reachable but
 * broken is backed off rather than hammered. A success resets the wait to
 * [baseInterval]. The first run needs no wait at all: app start and resume raise
 * one directly.
 */
data class SyncSchedule(
    /** The wait between runs once the last run succeeded. */
    val baseInterval: Duration = 15.minutes,
    /** The longest wait, so a failing target is polled at most once an hour. */
    val maxInterval: Duration = 1.hours,
) {

    /**
     * How long to wait before the next automatic run, given the number of
     * consecutive [failures]. A non-positive count asks for [baseInterval]; each
     * failure doubles it, capped at [maxInterval], so the value is always within
     * `[baseInterval, maxInterval]`.
     */
    fun intervalAfter(failures: Int): Duration {
        var interval = baseInterval
        repeat(failures.coerceIn(0, MAX_DOUBLINGS)) { interval *= 2 }
        return if (interval > maxInterval) maxInterval else interval
    }

    private companion object {
        /** Enough doublings to reach any cap; extra failures change nothing. */
        const val MAX_DOUBLINGS = 16
    }
}
