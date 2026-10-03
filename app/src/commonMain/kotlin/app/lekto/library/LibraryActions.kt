package app.lekto.library

import app.lekto.core.book.Book

/**
 * The actions the library screen raises: import, open a book, dismiss a failure.
 * Bundled so the screen's signature stays small as more actions arrive.
 */
data class LibraryActions(
    val onImport: () -> Unit = {},
    val onOpen: (Book) -> Unit = {},
    val onDismissError: () -> Unit = {},
)
