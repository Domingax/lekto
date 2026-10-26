package app.lekto.vocabulary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.reader.MasteryPalette
import app.lekto.reader.readerColorOr
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.ui.ChipRowScrollbar
import app.lekto.ui.ConfirmationDialog
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.SearchField
import app.lekto.ui.handCursor

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

/** The padding every filter chip shares, the same inside shape the mastery chips use. */
internal val FILTER_CHIP_PADDING = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/**
 * The vocabulary list's state (issue #23), one value per thing the screen can
 * show, so "nothing saved yet" cannot be confused with "nothing matched".
 */
sealed interface VocabularyUiState {

    /** The vocabulary holds nothing, so the screen shows the instructional empty state. */
    data object Empty : VocabularyUiState

    /**
     * The vocabulary holds entries: those matching [query] and [masteryLevel] —
     * every entry when the query is blank and [masteryLevel] is `null` — and the
     * two filters themselves. An empty [entries] here means the filters matched
     * nothing.
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
    ) : VocabularyUiState
}

/**
 * The vocabulary list's actions (issue #23, filter #66, clear-search #212): change
 * the search query, change the mastery level filter (`null` is every level), empty
 * the query, and delete an entry. It is a section of the shell now, so the bar
 * replaces its back action (issue #185).
 */
data class VocabularyActions(
    val onSearch: (String) -> Unit = {},
    val onFilter: (MasteryLevel?) -> Unit = {},
    val onClearSearch: () -> Unit = {},
    val onDelete: (VocabularyEntry) -> Unit = {},
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
            is VocabularyUiState.Results -> Results(state, actions, onDelete = { target -> pendingDelete = target })
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
) {
    SearchField(
        query = state.query,
        onSearch = actions.onSearch,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LektoSpacing.InsidePanel)
            .testTag(VOCABULARY_SEARCH_TAG),
    )
    MasteryFilter(state.masteryLevel, actions.onFilter)
    if (state.entries.isEmpty()) {
        NoWords(state, onClearSearch = actions.onClearSearch)
    } else {
        VocabularyList(state.entries, onDelete)
    }
}

/** The rows, each keyed by its word so the lazy list can reuse it, and parted by a hairline (§4). */
@Composable
private fun ColumnScope.VocabularyList(entries: List<VocabularyEntry>, onDelete: (VocabularyEntry) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = LektoSpacing.BetweenRelatedLines)) {
        items(entries, key = { entry -> entry.key.rowKey() }) { entry ->
            VocabularyRow(entry, onDelete)
        }
    }
}

/**
 * The mastery filter (issue #66; ink-filled selection #212): `All levels` and one
 * chip per level, so the list can be narrowed to a level. The row scrolls
 * sideways — a partly cut chip is what says so — and the current filter is the
 * theme's ink behind the paper, the identity's one shape for a selected filter
 * (`docs/design.md` §2.1, §7.4). The chip's dot keeps the level's own colour, so
 * the filter reads the way the rows do.
 */
@Composable
private fun MasteryFilter(selected: MasteryLevel?, onFilter: (MasteryLevel?) -> Unit) {
    val texts = LocalAppTexts.current
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRelatedLines)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
        ) {
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
        // Only a row with more chips than room needs a bar: a pointer cannot swipe
        // them, and the cut chip alone is not a control (issue #212).
        if (scroll.maxValue > 0) {
            ChipRowScrollbar(scroll, Modifier.fillMaxWidth().padding(top = LektoSpacing.InsideLabelPair))
        }
    }
}

/**
 * One filter: the level's coloured dot and its name in a hairline pill, filled
 * with the theme's ink when it is the current filter. The name carries the
 * meaning and the dot ties it to the reader's ramp, so a reader who cannot tell
 * the hues apart loses nothing (§2.2).
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
    val marked = modifier.semantics { this.selected = selected }
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MASTERY_DOT_GAP),
        ) {
            level?.let { drawn -> LevelDot(drawn.readerColorOr(theme.outline, MasteryPalette.of(theme))) }
            Text(label)
        }
    }
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
            shape = LektoRadii.Control,
            contentPadding = FILTER_CHIP_PADDING,
            modifier = marked.handCursor(),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(HAIRLINE_THICKNESS, theme.outline),
            shape = LektoRadii.Control,
            contentPadding = FILTER_CHIP_PADDING,
            modifier = marked.handCursor(),
        ) { content() }
    }
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
