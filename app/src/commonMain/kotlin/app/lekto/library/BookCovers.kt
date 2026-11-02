package app.lekto.library

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import app.lekto.core.book.BookLibrary
import app.lekto.reader.decodeImageBitmap

/**
 * The app's book covers, resolved per **Book**: the Library asks for one and the
 * loader decodes it, or answers `null` when the book has none it can show
 * (issue #259).
 *
 * A cover is a **Derived asset** (ADR-0005), so it never enters the **Vault**
 * and never syncs. The decode is the platform's own — Skia on desktop,
 * `BitmapFactory` on Android — so no image library is added and the licence gate
 * (ADR-0011) is untouched.
 */
fun interface BookCoverLoader {
    fun load(bookId: String): ImageBitmap?

    companion object {
        /** The loader of a Library that shows no art: every row keeps its language plaque. */
        val None: BookCoverLoader = BookCoverLoader { null }
    }
}

/** The [BookCoverLoader] over the covers the library read from the derived store. */
class LibraryBookCovers(private val library: BookLibrary) : BookCoverLoader {

    override fun load(bookId: String): ImageBitmap? {
        val bytes = library.cover(bookId) ?: return null
        return decodeImageBitmap(bytes)
    }
}

/**
 * The covers available to the Library's rows: the composition root provides the
 * real loader, and a test or a build with no derived store leaves the default,
 * so every row shows the language plaque rather than a hole.
 */
val LocalBookCovers = compositionLocalOf<BookCoverLoader> { BookCoverLoader.None }
