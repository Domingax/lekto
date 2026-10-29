package app.lekto.library

import app.lekto.core.book.Book
import app.lekto.core.book.ReadingState

/**
 * The actions the library screen raises: import, open a book, delete a book,
 * dismiss a failure, narrow the list by a title query and clear that query
 * (issue #173), and add or remove a **Reading state** from the filter or clear
 * both narrowings (issue #200). The way to another section is the shell's, not
 * this screen's (issue #185), so no navigation lives here. Bundled so the
 * screen's signature stays small as more actions arrive.
 */
data class LibraryActions(
    val onImport: () -> Unit = {},
    val onOpen: (Book) -> Unit = {},
    val onDelete: (Book) -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onClearSearch: () -> Unit = {},
    val onToggleState: (ReadingState) -> Unit = {},
    val onShowAllBooks: () -> Unit = {},
)
