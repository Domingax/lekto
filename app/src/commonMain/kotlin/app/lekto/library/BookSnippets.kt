package app.lekto.library

import androidx.compose.runtime.compositionLocalOf
import app.lekto.core.book.BookLibrary

/**
 * The app's **Resume snippets**, resolved per **Book**: the Library asks for one
 * and the loader answers the paragraph stored beside the parsed text, or `null`
 * when the book has none (issue #288).
 *
 * A snippet is a **Derived asset** (ADR-0005), so it never enters the **Vault**
 * and never syncs. Reading it is a store lookup, which is why the block asks the
 * loader off the main thread rather than taking the text in its state.
 */
fun interface BookSnippetLoader {
    fun snippet(bookId: String): String?

    companion object {
        /** The loader of a Library that shows no snippet: the block ends at its action. */
        val None: BookSnippetLoader = BookSnippetLoader { null }
    }
}

/** The [BookSnippetLoader] over the snippets the library derived beside the parsed text. */
class LibraryBookSnippets(private val library: BookLibrary) : BookSnippetLoader {

    override fun snippet(bookId: String): String? = library.snippet(bookId)
}

/**
 * The snippets available to the Library's resume block: the composition root
 * provides the real loader, and a test or a build with no derived store leaves
 * the default, so the block simply ends at its button rather than showing a hole.
 */
val LocalBookSnippets = compositionLocalOf<BookSnippetLoader> { BookSnippetLoader.None }
