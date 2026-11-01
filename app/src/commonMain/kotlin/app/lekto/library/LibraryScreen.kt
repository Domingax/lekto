package app.lekto.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.lekto.core.book.Book
import app.lekto.core.book.ReadingState
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.ui.ConfirmationDialog
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.SearchField
import app.lekto.ui.ToggleChip
import app.lekto.ui.handCursor
import app.lekto.ui.plainClickable

/** The test tag on the Library's search field (issue #173). */
internal const val LIBRARY_SEARCH_TAG: String = "library-search"

/** The test tag on the empty shelf's filled import action (issue #208). */
internal const val LIBRARY_EMPTY_IMPORT_TAG: String = "library-empty-import"

/** The test tag on a **Reading state** filter chip (issue #200). */
internal fun libraryStateTag(state: ReadingState): String = "library-state-${state.name}"

/** The test tag on the action that clears both the filter and the search. */
internal const val LIBRARY_SHOW_ALL_TAG: String = "library-show-all"

/** The lazy item key of the "Continue reading" heading, which is not a book. */
private const val CONTINUE_READING_KEY: String = "continue-reading"

/** The lazy item key of the "Other books" heading, which is not a book either. */
private const val OTHER_BOOKS_KEY: String = "other-books"

/** The test tag on the Library's title, so a layout test can find it apart from the shell's bar. */
internal const val LIBRARY_TITLE_TAG: String = "library-title"

/**
 * The library screen: the books in the vault, a title search, an Import action, a
 * per-book delete behind a destructive confirmation, and the import's progress
 * and failure inline so neither blocks the list (issues #15, #131, #173, #208;
 * UX spec, "Import in progress — inline progress bar", "AI/import error — inline
 * message", "Dialog").
 *
 * The screen renders [state] and raises the [actions]; the only state it owns is
 * the pending confirmation, which is view-local, so the vault is untouched until
 * the user confirms. The composition root (App) owns the rest and the file
 * picker, which keeps every behaviour testable through semantics alone.
 *
 * **Import** is the Library's action and never a section (issue #185;
 * docs/design.md §6, §7.1): a word beside the title once there is a shelf to add
 * to, and the filled action in the empty state, where it is the one thing to do.
 * The way to another section is the shell's bar, not this screen's header.
 *
 * Its anatomy is the identity's (docs/design.md §7.1): the title is the theme's
 * screen title, the search is the shared [SearchField] rather than a Material
 * field, and a row opens with its **Reading language**'s vignette. The states
 * stay distinct (§8): an empty vault says what to do, a shelf being read shows
 * skeleton rows rather than the empty message, and a query that matches nothing
 * names itself back.
 */
@Composable
fun LibraryScreen(state: LibraryUiState, actions: LibraryActions = LibraryActions(), modifier: Modifier = Modifier) {
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    val texts = LocalAppTexts.current

    Column(
        modifier.fillMaxSize().padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
    ) {
        LibraryHeader(vaultEmpty = state.vaultEmpty, importing = state.importing, onImport = actions.onImport)

        // A field over an empty vault would search nothing; the empty state keeps
        // its one sentence and its action (issue #173).
        if (!state.vaultEmpty) {
            SearchField(
                query = state.query,
                onSearch = actions.onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = LektoSpacing.InsidePanel)
                    .testTag(LIBRARY_SEARCH_TAG),
            )
            StateFilters(
                selected = state.selectedStates,
                onToggle = actions.onToggleState,
                modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
            )
        }

        state.error?.let { message -> LibraryError(texts.message(message), actions.onDismissError) }
        if (state.importing) Importing(state.progress)

        LibraryBody(state, actions, onDelete = { target -> pendingDelete = target })
    }

    pendingDelete?.let { target ->
        ConfirmationDialog(
            title = texts.message(AppMessage.of(TextKey.DELETE_BOOK_TITLE, target.title)),
            // The file name identifies the imported file, which is not on the row
            // any more (docs/design.md §7.1).
            message = texts.message(AppMessage.of(TextKey.DELETE_BOOK_MESSAGE, target.fileName)),
            confirmLabel = texts[TextKey.DELETE],
            onConfirm = {
                pendingDelete = null
                actions.onDelete(target)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * What fills the shelf under the chrome, one branch per case (docs/design.md §8):
 * the skeleton rows of a shelf being read, the empty state, the no-match state,
 * or the list. Keeping them apart means the empty message can never stand in for
 * a list that is on its way (issue #208).
 */
@Composable
private fun ColumnScope.LibraryBody(state: LibraryUiState, actions: LibraryActions, onDelete: (Book) -> Unit) {
    when {
        // A shelf being read is not an empty one: skeleton rows say what is
        // coming, and the empty message would claim there is nothing to wait for.
        state.vaultEmpty && state.loading -> LibrarySkeletons()

        // A first import is not an empty shelf either, but its own inline
        // progress, above, is the state: neither the empty words nor an action
        // that would start a second transfer belongs under it.
        state.vaultEmpty && state.importing -> Unit

        state.vaultEmpty -> LibraryEmpty(onImport = actions.onImport)

        state.visibleBooks.isEmpty() -> LibraryNoMatches(state, actions)

        else -> LibraryList(state, onOpen = actions.onOpen, onDelete = onDelete)
    }
}

/**
 * The empty shelf (docs/design.md §7.1, §8; issue #208): what to do, and the one
 * action — the filled button. The header keeps its word to itself here, so the
 * import is not offered twice.
 */
@Composable
private fun LibraryEmpty(onImport: () -> Unit) {
    val texts = LocalAppTexts.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenSections),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = texts[TextKey.LIBRARY_EMPTY_TITLE],
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = texts[TextKey.LIBRARY_EMPTY],
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
        )
        Button(
            onClick = onImport,
            modifier = Modifier
                .handCursor()
                .testTag(LIBRARY_EMPTY_IMPORT_TAG)
                .padding(top = LektoSpacing.InsidePanel),
        ) { Text(texts[TextKey.IMPORT_BOOK]) }
    }
}

/**
 * Why nothing is shown: the query is named back, or the **Reading state** filter
 * explains itself (issues #173, #200). A search offers `Clear search` as well as
 * `Show all books`, because the reader may want to keep the filter; `Show all
 * books` clears both narrowings and is the way back to the whole shelf.
 */
@Composable
private fun LibraryNoMatches(state: LibraryUiState, actions: LibraryActions) {
    val texts = LocalAppTexts.current
    Column(Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenSections)) {
        val message = when {
            state.queryMatchesNothing -> AppMessage.of(TextKey.NO_BOOKS_MATCH_QUERY, state.query.trim())
            state.everyBookIsFinished -> AppMessage.of(TextKey.LIBRARY_ALL_FINISHED)
            else -> AppMessage.of(TextKey.LIBRARY_NO_BOOKS_IN_STATE)
        }
        Text(
            text = texts.message(message),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (state.searching) {
            TextButton(onClick = actions.onClearSearch, modifier = Modifier.handCursor()) {
                Text(texts[TextKey.CLEAR_SEARCH])
            }
        }
        TextButton(
            onClick = actions.onShowAllBooks,
            modifier = Modifier.handCursor().testTag(LIBRARY_SHOW_ALL_TAG),
        ) {
            Text(texts[TextKey.SHOW_ALL_BOOKS])
        }
    }
}

/**
 * The **Reading state** filter (docs/design.md §7.1; issue #200): one row of
 * multi-select chips under the search field, scrolled horizontally when a
 * translation overflows it. A chip carries no count — the shelf is not a
 * dashboard — and the controller keeps the last selected one from being turned
 * off.
 */
@Composable
private fun StateFilters(selected: Set<ReadingState>, onToggle: (ReadingState) -> Unit, modifier: Modifier = Modifier) {
    val texts = LocalAppTexts.current
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        ReadingState.entries.forEach { state ->
            ToggleChip(
                label = texts[state.textKey],
                selected = state in selected,
                onClick = { onToggle(state) },
                modifier = Modifier.testTag(libraryStateTag(state)),
            )
        }
    }
}

/** The text a **Reading state** chip carries, in the reader's app language. */
private val ReadingState.textKey: TextKey
    get() = when (this) {
        ReadingState.UNREAD -> TextKey.READING_STATE_UNREAD
        ReadingState.IN_PROGRESS -> TextKey.READING_STATE_IN_PROGRESS
        ReadingState.FINISHED -> TextKey.READING_STATE_FINISHED
    }

/**
 * The scrollable book list (issue #188; docs/design.md §7.1): the recently read
 * books under `Continue reading`, then the rest under `Other books`, every group
 * by title. A search flattens them, so neither heading shows while the reader is
 * browsing a query (issue #173).
 *
 * Two named groups, not one heading over everything: a single heading left the
 * reader unable to see where "read recently" ended, which was the first note on
 * issue #208's review.
 */
@Composable
private fun ColumnScope.LibraryList(state: LibraryUiState, onOpen: (Book) -> Unit, onDelete: (Book) -> Unit) {
    val texts = LocalAppTexts.current
    LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = LektoSpacing.BetweenRelatedLines)) {
        if (state.searching) {
            bookItems(state.visibleBooks, state, onOpen, onDelete)
        } else {
            shelfGroups(state, texts, onOpen, onDelete)
        }
    }
}

/** The two groups of a shelf the reader is browsing: the recent books, then the rest, each named. */
private fun LazyListScope.shelfGroups(
    state: LibraryUiState,
    texts: AppTexts,
    onOpen: (Book) -> Unit,
    onDelete: (Book) -> Unit,
) {
    val recent = state.visibleLastRead
    val rest = state.visibleOthers
    if (recent.isNotEmpty()) {
        headingItem(CONTINUE_READING_KEY, texts[TextKey.CONTINUE_READING], top = LektoSpacing.BetweenRelatedLines)
        bookItems(recent, state, onOpen, onDelete)
    }
    if (rest.isNotEmpty()) {
        // A shelf with no reading history is one list by title, so only a shelf
        // that has a leading group needs the second heading.
        if (recent.isNotEmpty()) {
            headingItem(OTHER_BOOKS_KEY, texts[TextKey.OTHER_BOOKS], top = LektoSpacing.BetweenBlocks)
        }
        bookItems(rest, state, onOpen, onDelete)
    }
}

/** A group's heading item, keyed so a lazy list keeps its place when the shelf moves. */
private fun LazyListScope.headingItem(key: String, text: String, top: Dp) {
    item(key = key) { SectionHeading(text, top) }
}

/** A group's rows, each keyed by its book so the lazy list can reuse them. */
private fun LazyListScope.bookItems(
    books: List<Book>,
    state: LibraryUiState,
    onOpen: (Book) -> Unit,
    onDelete: (Book) -> Unit,
) {
    val actions = BookRowActions(onOpen = onOpen, onDelete = onDelete)
    items(books, key = { book -> book.id }) { book ->
        BookRow(book, state.listing.progressOf(book), state.listing.readingStateOf(book), actions)
    }
}

/** A group's heading: the design's serif section title, so the two groups read as sections (§3). */
@Composable
private fun SectionHeading(text: String, top: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = top, bottom = LektoSpacing.BetweenRelatedLines),
    )
}

/**
 * The library's chrome: its title, and Import as a word beside it once there is a
 * shelf to add to (docs/design.md §6, §7.1). A **FlowRow**, not a **Row**: the
 * French title ("Bibliothèque") is wider than the English one, so on a phone the
 * action wraps to its own line rather than being squeezed.
 */
@OptIn(ExperimentalLayoutApi::class) // `FlowRow` wraps an action below a title too wide to share the line.
@Composable
private fun LibraryHeader(vaultEmpty: Boolean, importing: Boolean, onImport: () -> Unit) {
    val texts = LocalAppTexts.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = texts[TextKey.LIBRARY],
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(LIBRARY_TITLE_TAG),
        )
        // An empty shelf's action is the filled button in the empty state, so the
        // header does not repeat it; while a transfer runs, the word is disabled
        // rather than making a second one.
        if (!vaultEmpty) {
            Box(
                modifier = Modifier
                    .heightIn(min = LektoSpacing.TouchTarget)
                    .handCursor(!importing)
                    .plainClickable(enabled = !importing, role = Role.Button, onClick = onImport),
                contentAlignment = Alignment.Center,
            ) {
                Text(texts[TextKey.IMPORT], style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * The inline library failure — an import or a delete — as a bordered block where
 * the action was, with a way to dismiss it (docs/design.md §7.1, §8): not a
 * full-screen alert, and never something that blocks the reading.
 */
@Composable
private fun LibraryError(message: String, onDismiss: () -> Unit) {
    val texts = LocalAppTexts.current
    val theme = MaterialTheme.colorScheme
    val described = texts.message(AppMessage.of(TextKey.ERROR_DETAIL, message))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LektoSpacing.InsidePanel)
            .background(theme.surface, LektoRadii.Surface)
            .border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Surface)
            .padding(LektoSpacing.InsidePanel),
    ) {
        Text(
            text = message,
            color = theme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = described },
        )
        TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) { Text(texts[TextKey.DISMISS]) }
    }
}

/** The inline import progress, determinate so it reports how far along it is. */
@Composable
private fun Importing(fraction: Float) {
    val texts = LocalAppTexts.current
    Column(Modifier.fillMaxWidth().padding(top = LektoSpacing.InsidePanel)) {
        Text(
            text = texts[TextKey.IMPORTING],
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LektoSpacing.InsideLabelPair)
                .semantics { contentDescription = texts[TextKey.IMPORT_PROGRESS] },
        )
    }
}
