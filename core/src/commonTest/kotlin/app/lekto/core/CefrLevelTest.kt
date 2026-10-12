package app.lekto.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The six-point CEFR axis the Reader level and Word difficulty share (ADR-0029, ADR-0030). */
class CefrLevelTest {

    @Test
    fun theBandsRunFromA1ToC2() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5), CefrLevel.entries.map { level -> level.band })
        assertEquals(
            listOf("A1", "A2", "B1", "B2", "C1", "C2"),
            CefrLevel.entries.map { level -> level.name },
        )
    }

    @Test
    fun aBandResolvesToItsLevel() {
        assertEquals(CefrLevel.B1, CefrLevel.ofBand(2))
        assertEquals(CefrLevel.C2, CefrLevel.ofBand(5))
    }

    @Test
    fun aBandOutsideTheScaleHasNoLevel() {
        assertNull(CefrLevel.ofBand(-1))
        assertNull(CefrLevel.ofBand(6))
    }
}
