package app.lekto.library

import app.lekto.core.book.Book

/**
 * The actions the library screen raises: import, open a book, delete a book,
 * dismiss a failure, narrow the list by a title query and clear that query
 * (issue #173). Bundled so the screen's signature stays small as more actions
 * arrive.
 */
data class LibraryActions(
    val onImport: () -> Unit = {},
    val onOpen: (Book) -> Unit = {},
    val onDelete: (Book) -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenVocabulary: () -> Unit = {},
    val onOpenLanguages: () -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onClearSearch: () -> Unit = {},
)
