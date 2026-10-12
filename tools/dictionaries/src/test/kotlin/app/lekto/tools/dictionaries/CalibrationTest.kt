package app.lekto.tools.dictionaries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The production frequency → CEFR calibration and its cut points (ADR-0029). */
class CalibrationTest {

    @Test
    fun `cuts the ranking at the published CEFR vocabulary sizes`() {
        assertEquals(listOf(1500, 2500, 3250, 3750, 4500, 5000), Calibration.CUT_SIZES)
        assertEquals(listOf("A1", "A2", "B1", "B2", "C1", "C2"), Calibration.BANDS)
    }

    @Test
    fun `assigns each rank to its band`() {
        assertEquals("A1", Calibration.bandFor(1))
        assertEquals("A1", Calibration.bandFor(1500))
        assertEquals("A2", Calibration.bandFor(1501))
        assertEquals("A2", Calibration.bandFor(2500))
        assertEquals("B1", Calibration.bandFor(2501))
        assertEquals("B1", Calibration.bandFor(3250))
        assertEquals("B2", Calibration.bandFor(3251))
        assertEquals("B2", Calibration.bandFor(3750))
        assertEquals("C1", Calibration.bandFor(3751))
        assertEquals("C1", Calibration.bandFor(4500))
        assertEquals("C2", Calibration.bandFor(4501))
        assertEquals("C2", Calibration.bandFor(5000))
    }

    @Test
    fun `a rank below the calibrated top has no band`() {
        assertNull(Calibration.bandFor(5001))
    }
}
