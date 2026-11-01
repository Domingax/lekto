package app.lekto.reader

import androidx.compose.ui.graphics.ImageBitmap
import app.lekto.core.vault.DerivedAssetStore

/**
 * The app's images, resolved per **Book**: a reader asks for its book's loader
 * and the loader decodes one derived asset by name.
 *
 * The pictures are **Derived assets** (ADR-0005), written beside the parsed text
 * and never in the **Vault**, so a device decodes its own copy in place rather
 * than reading a file into memory (ADR-0017). `null` from the store is the honest
 * "this build has no images", and every picture then shows its alt.
 */
fun interface BookImageStore {
    fun loaderFor(bookId: String): BookImageLoader
}

/** The [BookImageStore] over the derived assets a book's images were extracted into. */
class DerivedBookImages(private val derived: DerivedAssetStore) : BookImageStore {

    override fun loaderFor(bookId: String): BookImageLoader = BookImageLoader { name ->
        runCatching { decodeImageBitmap(derived.storedPath(imagesPath(bookId, name))) }.getOrNull()
    }

    /** Where the library wrote the images of [bookId] (issue #263). */
    private fun imagesPath(bookId: String, name: String): String = "book-images/$bookId/$name"
}

/**
 * Decodes the picture at the app-private [path], or `null` when it is not an
 * image this platform can read.
 *
 * It is a platform seam rather than a library: Android has `BitmapFactory` and
 * the desktop client already carries Skia through Compose, so no dependency is
 * added and the licence gate (ADR-0011) is untouched.
 */
internal expect fun decodeImageBitmap(path: String): ImageBitmap?
