package app.lekto.core.sample

import app.lekto.core.language.SampleRoster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The loader the clients use to read the shipped **Sample catalogue** (issue
 * #278) reaches the committed bytes: the same eight samples `SampleRoster`
 * lists, decoded through the app's own reader. A build whose resource fell out of
 * the jar would materialise nothing, which nothing else would catch.
 */
class ShippedSampleCatalogueTest {

    @Test
    fun `the shipped catalogue loads from the classpath the app reads`() {
        val catalogue = assertNotNull(shippedSampleCatalogue(), "the build ships no readable sample catalogue")

        assertEquals(SampleRoster.LANGUAGES, catalogue.samples.map(Sample::language))
    }
}
