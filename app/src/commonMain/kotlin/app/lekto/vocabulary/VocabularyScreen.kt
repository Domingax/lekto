@file:Suppress("TooManyFunctions") // The list, its two empty states, its filters and their chips are one surface.

package app.lekto.vocabulary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import app.lekto.core.MasteryLevel
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.reader.MasteryPalette
import app.lekto.reader.readerColorOr
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.ChoiceChip
import app.lekto.ui.ConfirmationDialog
import app.lekto.ui.SearchField
import app.lekto.ui.ToggleChip
import app.lekto.ui.handCursor
import app.lekto.ui.touchClient

/** The test tag on the search field, so a UI test can type into it unambiguously. */
internal const val VOCABULARY_SEARCH_TAG: String = "vocabulary-search"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar (issue #185). */
internal const val VOCABULARY_TITLE_TAG: String = "vocabulary-title"

/** The test tag on the saved-word count beside the title, so a test can read it apart from a row's word. */
internal const val VOCABULARY_COUNT_TAG: String = "vocabulary-count"

/** The test tag on the action that empties the search, so a no-match test can click it. */
internal const val VOCABULARY_CLEAR_SEARCH_TAG: String = "vocabulary-clear-search"

/** The test tag on a mastery filter chip; `null` is the "All levels" chip. */
internal fun vocabularyFilterTag(level: MasteryLevel?): String = "vocabulary-filter-${level?.name ?: "ALL"}"

/** The test tag on the book chip, present only while the list is reached from a book (issue #280). */
internal const val VOCABULARY_BOOK_CHIP_TAG: String = "vocabulary-book-chip"

/**
 * The book a `See the words` opening narrows the Vocabulary to (issue #280):
 * its identity for the filter and the title the chip carries. View state only —
 * it is never written to the **Vault**, and the list's count stays the whole
 * vocabulary.
 */
data class VocabularyBook(val id: String, val title: String)

/**
 * The vocabulary list's state (issue #23), one value per thing the screen can
 * show, so "nothing saved yet" cannot be confused with "nothing matched".
 */
sealed interface VocabularyUiState {

    /** The vocabulary holds nothing, so the screen shows the instructional empty state. */
    data object Empty : VocabularyUiState

    /**
     * The vocabulary holds entries: those matching [query], [masteryLevel] and —
     * when the list was reached from a book — [book] — every entry when the
     * filters are empty — and the filters themselves. An empty [entries] here
     * means the filters matched nothing.
     *
     * [total] is how many words the vocabulary holds, which is not
     * [entries]`size` while a filter narrows the list: the count beside the
     * title counts the vocabulary, not the rows the reader narrowed it to.
     */
    data class Results(
        val entries: List<VocabularyEntry>,
        val query: String,
        val masteryLevel: MasteryLevel? = null,
        val total: Int = entries.size,
        val book: VocabularyBook? = null,
    ) : VocabularyUiState
}

/**
 * The vocabulary list's actions (issue #23, filter #66, clear-search #212): change
 * the search query, change the mastery level filter (`null` is every level), empty
 * the query, widen the list back from a book (issue #280), delete an entry, and open
 * an entry's level sheet (issue #178). It is a section of the shell now, so the bar
 * replaces its back action (issue #185).
 */
data class VocabularyActions(
    val onSearch: (String) -> Unit = {},
    val onFilter: (MasteryLevel?) -> Unit = {},
    val onClearSearch: () -> Unit = {},
    val onClearBook: () -> Unit = {},
    val onDelete: (VocabularyEntry) -> Unit = {},
    val onOpen: (VocabularyEntry) -> Unit = {},
)

/**
 * The vocabulary list (issue #23; issue #212 for its anatomy): the saved words
 * with their level, translation and context sentence, searchable, filterable and
 * deletable, with the instructional empty state `docs/design.md` prescribes when
 * nothing is saved (§7.4).
 *
 * The screen renders [state] and raises the [actions]; the only state it owns is
 * the pending confirmation, which is view-local, so the vault is untouched until
 * the reader confirms — the same shape the Library's delete has (§8). The
 * composition root (`App`) owns the query, the filter and the delete, and runs
 * the blocking vault write off the UI thread.
 *
 * Its anatomy is the identity's (§7.4): the count beside the screen title, the
 * shared [SearchField], a row whose word keeps the book's serif and whose level
 * is a colour dot and its name, and a delete that is a row gesture behind the
 * one confirmation — never a red word on every row. The two empty states stay
 * distinct (§8): one says how words arrive, the other names the search and
 * offers to clear it.
 */
@Composable
fun VocabularyScreen(
    state: VocabularyUiState,
    actions: VocabularyActions = VocabularyActions(),
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<VocabularyEntry?>(null) }
    val texts = LocalAppTexts.current

    Column(modifier.fillMaxSize().padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel)) {
        // One count for both states: nothing saved is `0 words`, which is honest
        // and keeps the header from moving when the first word lands.
        val total = when (state) {
            VocabularyUiState.Empty -> 0
            is VocabularyUiState.Results -> state.total
        }
        Header(total)
        when (state) {
            VocabularyUiState.Empty -> EmptyVocabulary()

            is VocabularyUiState.Results -> Results(
                state,
                actions,
                onDelete = { target -> pendingDelete = target },
                onOpen = actions.onOpen,
            )
        }
    }

    pendingDelete?.let { target ->
        ConfirmationDialog(
            title = texts.message(AppMessage.of(TextKey.DELETE_WORD_TITLE, target.surface)),
            message = texts[TextKey.DELETE_WORD_MESSAGE],
            confirmLabel = texts[TextKey.DELETE],
            onConfirm = {
                pendingDelete = null
                actions.onDelete(target)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** The screen's chrome: its title, and the count of saved words beside it in the muted ink (§7.4). */
@Composable
private fun Header(total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = LocalAppTexts.current[TextKey.VOCABULARY],
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(VOCABULARY_TITLE_TAG),
        )
        SavedWords(total)
    }
}

/** `128 words` in the muted ink, singular at one: the count is about the vocabulary, not the filtered list. */
@Composable
private fun SavedWords(total: Int) {
    val texts = LocalAppTexts.current
    Text(
        text = if (total == 1) {
            texts[TextKey.VOCABULARY_COUNT_ONE]
        } else {
            texts.message(AppMessage.of(TextKey.VOCABULARY_COUNT_MANY, total))
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(VOCABULARY_COUNT_TAG),
    )
}

@Composable
private fun ColumnScope.Results(
    state: VocabularyUiState.Results,
    actions: VocabularyActions,
    onDelete: (VocabularyEntry) -> Unit,
    onOpen: (VocabularyEntry) -> Unit,
) {
    SearchField(
        query = state.query,
        onSearch = actions.onSearch,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LektoSpacing.InsidePanel)
            .testTag(VOCABULARY_SEARCH_TAG),
    )
    MasteryFilter(
        book = state.book,
        selected = state.masteryLevel,
        onFilter = actions.onFilter,
        onClearBook = actions.onClearBook,
        modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
    )
    if (state.entries.isEmpty()) {
        NoWords(state, onClearSearch = actions.onClearSearch)
    } else {
        VocabularyList(state.entries, onDelete, onOpen)
    }
}

/** The rows, each keyed by its word so the lazy list can reuse it, and parted by a hairline (§4). */
@Composable
private fun ColumnScope.VocabularyList(
    entries: List<VocabularyEntry>,
    onDelete: (VocabularyEntry) -> Unit,
    onOpen: (VocabularyEntry) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = LektoSpacing.BetweenRelatedLines)) {
        items(entries, key = { entry -> entry.key.rowKey() }) { entry ->
            VocabularyRow(entry, onDelete, onOpen)
        }
    }
}

/**
 * The filter row (issue #66; the **Aa** panel's chip, #212; the book chip, #280):
 * the book the list was reached from, then `All levels` and one chip per level,
 * drawn as the reader's own value chips so the filter and the panel cannot drift
 * into two looks.
 *
 * A touch screen gets one line that scrolls, and a partly cut chip is what says
 * it moves: that is the thumb's idiom, and a phone has no height to spare. A
 * pointer gets the chips wrapped instead — a mouse cannot swipe a row of buttons,
 * and Material's own guidance is to wrap a small chip set rather than hide its
 * ends behind a scrollbar (the seven chips take two rows at most).
 */
@OptIn(ExperimentalLayoutApi::class) // `FlowRow` is the wrapping layout a pointer gets.
@Suppress("LongParameterList") // The book, the level, their two taps and the modifier are independent.
@Composable
private fun MasteryFilter(
    book: VocabularyBook?,
    selected: MasteryLevel?,
    onFilter: (MasteryLevel?) -> Unit,
    onClearBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (touchClient) {
        Row(
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
        ) {
            FilterChips(book, selected, onFilter, onClearBook)
        }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
            verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        ) {
            FilterChips(book, selected, onFilter, onClearBook)
        }
    }
}

/** The book chip and the six level chips themselves, emitted into whichever layout the platform asked for. */
@Composable
private fun FilterChips(
    book: VocabularyBook?,
    selected: MasteryLevel?,
    onFilter: (MasteryLevel?) -> Unit,
    onClearBook: () -> Unit,
) {
    val texts = LocalAppTexts.current
    book?.let { narrowed -> BookChip(narrowed, onClearBook) }
    FilterChip(
        level = null,
        label = texts[TextKey.ALL_LEVELS],
        selected = selected == null,
        onClick = { onFilter(null) },
        modifier = Modifier.testTag(vocabularyFilterTag(null)),
    )
    MasteryLevel.entries.forEach { level ->
        FilterChip(
            level = level,
            label = texts.masteryName(level),
            selected = level == selected,
            onClick = { onFilter(level) },
            modifier = Modifier.testTag(vocabularyFilterTag(level)),
        )
    }
}

/**
 * The book the list was reached from (issue #280): its title on a selected chip,
 * the same control as the level filters. Tapping it drops the narrowing and the
 * chip with it, so the reader can widen the list without leaving the screen; the
 * filter is view state and never a **Vault** record.
 */
@Composable
private fun BookChip(book: VocabularyBook, onClear: () -> Unit) {
    ToggleChip(
        label = book.title,
        selected = true,
        onClick = onClear,
        modifier = Modifier.testTag(VOCABULARY_BOOK_CHIP_TAG),
    )
}

/**
 * One filter, drawn by the shared [ChoiceChip]: the level's coloured dot before
 * its name, and the chip's frame the same as the **Aa** panel's. The name carries
 * the meaning and the dot ties it to the reader's ramp, so a reader who cannot
 * tell the hues apart loses nothing (§2.2).
 */
@Suppress("LongParameterList") // The chip's level, its label, its state and its tap are independent.
@Composable
private fun FilterChip(
    level: MasteryLevel?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.colorScheme
    ChoiceChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        leading = level?.let { drawn ->
            { LevelDot(drawn.readerColorOr(theme.outline, MasteryPalette.of(theme))) }
        },
    )
}

/**
 * The empty vocabulary (§7.4, §8): a heading and the one sentence that says how
 * words arrive. It is not the no-match state — that one names the query back and
 * offers to clear it — so the two can never be read for each other.
 */
@Composable
private fun EmptyVocabulary() {
    val texts = LocalAppTexts.current
    Centred {
        Text(
            text = texts[TextKey.VOCABULARY_EMPTY_TITLE],
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = texts[TextKey.VOCABULARY_EMPTY],
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
        )
    }
}

/**
 * Why the filtered list is empty (§7.4, §8): the query — or the level, or both —
 * named back. A query also says what it covered and offers to clear it; a level
 * alone needs no action, because its chip is right above the message.
 */
@Composable
private fun NoWords(state: VocabularyUiState.Results, onClearSearch: () -> Unit) {
    val texts = LocalAppTexts.current
    val searched = state.query.trim().isNotEmpty()
    Centred {
        Text(
            text = noMatchMessage(texts, state.query, state.masteryLevel),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (searched) {
            Text(
                text = texts[TextKey.NO_WORDS_MATCH_DETAIL],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
            )
            TextButton(
                onClick = onClearSearch,
                modifier = Modifier.handCursor().testTag(VOCABULARY_CLEAR_SEARCH_TAG),
            ) { Text(texts[TextKey.CLEAR_SEARCH]) }
        }
    }
}

/** A message block under the chrome: centred, one block away from the filters (docs/design.md §4). */
@Composable
private fun Centred(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenBlocks),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Why the filtered list is empty, naming each filter that emptied it. */
private fun noMatchMessage(texts: AppTexts, query: String, masteryLevel: MasteryLevel?): String {
    val searched = query.trim()
    return when {
        searched.isNotEmpty() && masteryLevel != null -> texts.message(
            AppMessage.of(TextKey.NO_WORDS_MATCH_QUERY_AT_LEVEL, searched, texts.masteryName(masteryLevel)),
        )

        searched.isNotEmpty() -> texts.message(AppMessage.of(TextKey.NO_WORDS_MATCH_QUERY, searched))

        masteryLevel != null -> texts.message(
            AppMessage.of(TextKey.NO_WORDS_AT_LEVEL, texts.masteryName(masteryLevel)),
        )

        else -> texts[TextKey.NO_WORDS_TO_SHOW]
    }
}

/**
 * The entries matching [query] (issue #23): a blank query returns them all, and a
 * non-blank one matches case-insensitively against the spelling the user sees,
 * its translation and its context sentence — and against the lemma the entry is
 * keyed by, so an inflected word is found by its dictionary form.
 */
fun searchVocabulary(entries: List<VocabularyEntry>, query: String): List<VocabularyEntry> {
    val needle = query.trim()
    if (needle.isEmpty()) return entries
    return entries.filter { entry -> entry.matches(needle) }
}

private fun VocabularyEntry.matches(needle: String): Boolean = surface.contains(needle, ignoreCase = true) ||
    key.key.contains(needle, ignoreCase = true) ||
    translation.orEmpty().contains(needle, ignoreCase = true) ||
    contextSentence.orEmpty().contains(needle, ignoreCase = true)

/**
 * The entries saved at [masteryLevel] (issue #66), or every entry when
 * [masteryLevel] is `null` — the "All levels" chip. The filter and
 * [searchVocabulary] compose, either order giving the same set.
 */
fun filterByMastery(entries: List<VocabularyEntry>, masteryLevel: MasteryLevel?): List<VocabularyEntry> =
    if (masteryLevel == null) entries else entries.filter { entry -> entry.mastery == masteryLevel }

/**
 * The entries whose book history names [bookId] (issue #280), or every entry when
 * [bookId] is `null` — the Vocabulary opened any other way. An entry is listed
 * under each book it was saved from; a word with no history belongs to none, so a
 * book read before the history existed narrows to an honest empty list.
 */
fun filterByBook(entries: List<VocabularyEntry>, bookId: String?): List<VocabularyEntry> =
    if (bookId == null) entries else entries.filter { entry -> bookId in entry.bookIds }
