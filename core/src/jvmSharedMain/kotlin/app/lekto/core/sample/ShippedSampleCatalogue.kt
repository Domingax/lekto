package app.lekto.core.sample

/**
 * Reads the **Sample catalogue** this build ships (issue #278): the committed
 * catalogue beside the domain's classes, decoded by the same [readSampleCatalog]
 * the rest of the app uses. It is the loader the desktop client wires; the
 * Android client reads the same bytes from its packaged assets, because an
 * Android app's classloader does not see a library's Kotlin resources.
 *
 * A build with no catalogue, or one this version refuses, reads as `null`: the
 * app then simply has no sample to materialise, rather than failing to start.
 */
fun shippedSampleCatalogue(): SampleCatalogContent? =
    CatalogueResource::class.java.getResourceAsStream(CATALOGUE_PATH)?.use { stream ->
        (readSampleCatalog(stream.readBytes()) as? SampleCatalogRead.Read)?.catalogue
    }

/** The classloader anchor for the catalogue, which ships beside the domain's classes. */
private object CatalogueResource

/** Where the catalogue sits, relative to the classpath root. */
private const val CATALOGUE_PATH: String = "/samples/catalogue.json"
