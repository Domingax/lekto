@file:Suppress("MagicNumber") // The sizes the test reads are the point of the test.

package app.lekto.language

import app.lekto.text.appTextsFor
import kotlin.test.Test
import kotlin.test.assertEquals

/** The download size reads in the interface language and in whole units (issue #215). */
class ArtifactSizeTest {

    private val english = appTextsFor("en")
    private val french = appTextsFor("fr")

    @Test
    fun readsMegabytesToTheNearestWholeUnit() {
        assertEquals("12 MB", artifactSizeText(english, 12_345_678))
        assertEquals("2 MB", artifactSizeText(english, 1_500_000))
    }

    @Test
    fun readsKilobytesBelowOneMegabyteAndNeverZero() {
        assertEquals("12 KB", artifactSizeText(english, 12_000))
        assertEquals("1 KB", artifactSizeText(english, 0))
    }

    @Test
    fun readsGigabytesAboveOneGigabyte() {
        assertEquals("2 GB", artifactSizeText(english, 1_500_000_000))
    }

    @Test
    fun readsTheUnitsInFrench() {
        assertEquals("12 Mo", artifactSizeText(french, 12_345_678))
    }
}
