@file:Suppress("MagicNumber") // The offsets 1, 2 and 3 are the test's data.

package app.lekto

import app.lekto.core.book.ReadingPosition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The single writer that keeps rapid page turns from racing the vault: it saves
 * one position at a time, lets a newer position replace a pending one so a burst
 * of turns coalesces rather than racing, and never lets a failing save escape to
 * bring the reader down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingProgressWriterTest {

    @Test
    fun burstOfTurnsCoalescesRatherThanRacingTheVault() = runTest {
        val saved = mutableListOf<ReadingPosition>()
        // A save that suspends, so several turns can arrive while it is in flight.
        val writer = ReadingProgressWriter(backgroundScope, StandardTestDispatcher(testScheduler)) { position ->
            saved += position
            delay(50)
        }
        runCurrent() // let the single collector subscribe before the burst

        writer.record(ReadingPosition("book", 1))
        writer.record(ReadingPosition("book", 2))
        writer.record(ReadingPosition("book", 3))
        runCurrent()
        advanceTimeBy(200)
        runCurrent()

        // Never three concurrent writers, and the last position always wins.
        assertTrue(saved.size < 3, "a burst must coalesce, but ${saved.size} saves ran")
        assertEquals(3, saved.last().offset)
    }

    @Test
    fun everySaveRunsWhenTurnsAreSpaced() = runTest {
        val saved = mutableListOf<ReadingPosition>()
        val writer = ReadingProgressWriter(backgroundScope, UnconfinedTestDispatcher(testScheduler)) { position ->
            saved += position
        }

        writer.record(ReadingPosition("book", 1))
        advanceUntilIdle()
        writer.record(ReadingPosition("book", 2))
        advanceUntilIdle()

        assertEquals(listOf(1, 2), saved.map { position -> position.offset })
    }

    @Test
    fun aFailingSaveDoesNotEscape() = runTest {
        val writer = ReadingProgressWriter(backgroundScope, UnconfinedTestDispatcher(testScheduler)) {
            error("disk full")
        }

        writer.record(ReadingPosition("book", 1))
        advanceUntilIdle()
    }
}
