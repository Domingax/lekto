package app.lekto.settings

import app.lekto.core.sync.SyncReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The last sync's outcome in plain language (issue #28): what moved, or that
 * nothing needed to, singular and plural book originals, and the honest note when
 * a conditional write could not be settled and will retry.
 */
class SyncReportSummaryTest {

    @Test
    fun `an empty report says everything was already up to date`() {
        assertEquals("Everything was already up to date.", report().summary())
    }

    @Test
    fun `a report names each kind of change that moved`() {
        val summary = report(uploaded = 1, downloaded = 2, deletions = 3).summary()

        assertEquals("Synced: downloaded 2, uploaded 1, removed 3.", summary)
    }

    @Test
    fun `a single carried book original is singular`() {
        assertEquals("Synced: carried 1 book original.", report(attachments = 1).summary())
    }

    @Test
    fun `several carried book originals are plural`() {
        assertEquals("Synced: carried 2 book originals.", report(attachments = 2).summary())
    }

    @Test
    fun `an unresolved write is reported as a retry rather than as loss`() {
        val summary = report(unresolved = 1).summary()

        assertTrue(summary.contains("couldn't be settled"), "the retry is named: $summary")
    }

    @Suppress("LongParameterList") // The report's own fields, each an axis a case varies.
    private fun report(
        uploaded: Int = 0,
        downloaded: Int = 0,
        deletions: Int = 0,
        attachments: Int = 0,
        unresolved: Int = 0,
    ) = SyncReport(uploaded, downloaded, deletions, attachments, unresolved)
}
