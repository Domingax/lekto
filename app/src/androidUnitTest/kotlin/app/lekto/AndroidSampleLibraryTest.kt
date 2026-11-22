package app.lekto

import app.lekto.core.sample.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Android **Sample book** wiring (issue #278) on a simulated Android runtime:
 * the catalogue is packaged as an asset from `core`'s committed resource, so the
 * one copy of the bytes reaches the Android client, and the reader gets one row
 * it can open and remove.
 *
 * The desktop lane proves the classpath loader (`ShippedSampleCatalogueTest`);
 * this is the lane that proves an Android app can see the same catalogue, which a
 * library's Kotlin resources would not give it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidSampleLibraryTest {

    @Test
    fun theCatalogueIsPackagedAndMaterialisesOneSample() {
        val library = androidSampleLibrary(RuntimeEnvironment.getApplication())
        assertNotNull("the build packages no sample catalogue for Android", library)
        val samples = requireNotNull(library)

        val row = samples.materialise("fr")

        assertEquals(Sample.identityOf("fr"), row?.book?.id)
        assertNotNull(samples.open(Sample.identityOf("fr"))?.text)
        assertTrue(samples.dismiss(Sample.identityOf("fr")))
        assertNull(samples.row())
    }
}
