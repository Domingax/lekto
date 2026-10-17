package app.lekto.settings

import app.lekto.core.sync.SyncReport
import app.lekto.text.appTextsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The last sync's outcome in plain language (issue #28): what moved, or that
 * nothing needed to, singular and plural book originals, and the honest note when
 * a conditional write could not be settled and will retry. The message carries
 * its counts, so it reads in the reader's **app language** (ADR-0034); the test
 * renders it in the base language, which is the pinned baseline.
 */
class SyncReportSummaryTest {

    @Test
    fun `an empty report says everything was already up to date`() {
        assertEquals("Everything was already up to date.", render(report()))
    }

    @Test
    fun `a report names each kind of change that moved`() {
        val summary = render(report(uploaded = 1, downloaded = 2, deletions = 3))

        assertEquals("Synced: downloaded 2, uploaded 1, removed 3.", summary)
    }

    @Test
    fun `a single carried book original is singular`() {
        assertEquals("Synced: carried 1 book original.", render(report(attachments = 1)))
    }

    @Test
    fun `several carried book originals are plural`() {
        assertEquals("Synced: carried 2 book originals.", render(report(attachments = 2)))
    }

    @Test
    fun `an unresolved write is reported as a retry rather than as loss`() {
        val summary = render(report(unresolved = 1))

        assertTrue(summary.contains("couldn't be settled"), "the retry is named: $summary")
    }

    @Test
    fun `a resynced device says it restarted from the server`() {
        val summary = render(report(resynced = true))

        assertTrue(summary.contains("restarted from the server"), "the resync is named: $summary")
    }

    @Test
    fun `a run that moved nothing and left writes unsettled does not claim success`() {
        val summary = render(report(unresolved = 2))

        assertTrue(summary.startsWith("Nothing was synced"), "a refused run is not up to date: $summary")
        assertEquals("Nothing was synced. Some changes couldn't be settled; they'll retry next time.", summary)
    }

    private fun render(report: SyncReport): String = appTextsFor(null).message(report.summary())

    @Suppress("LongParameterList") // The report's own fields, each an axis a case varies.
    private fun report(
        uploaded: Int = 0,
        downloaded: Int = 0,
        deletions: Int = 0,
        attachments: Int = 0,
        unresolved: Int = 0,
        resynced: Boolean = false,
    ) = SyncReport(uploaded, downloaded, deletions, attachments, unresolved, resynced)
}
