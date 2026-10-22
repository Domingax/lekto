package app.lekto.vocabulary

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.reader.MasteryChip
import app.lekto.reader.MasteryPalette
import app.lekto.reader.readerColorOr
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.ui.handCursor

/** The test tag on the search field, so a UI test can type into it unambiguously. */
internal const val VOCABULARY_SEARCH_TAG: String = "vocabulary-search"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar (issue #185). */
internal const val VOCABULARY_TITLE_TAG: String = "vocabulary-title"

/** The test tag on an entry's delete button, so a UI test can click exactly one. */
internal fun vocabularyDeleteTag(key: WordKey): String = "vocabulary-delete-${key.language.orEmpty()}-${key.key}"

/** The test tag on a mastery filter chip; `null` is the "All levels" chip. */
internal fun vocabularyFilterTag(level: MasteryLevel?): String = "vocabulary-filter-${level?.name ?: "ALL"}"

/** The test tag on an entry's mastery label, so a UI test can read it apart from the filter chips. */
internal fun vocabularyMasteryTag(key: WordKey): String = "vocabulary-mastery-${key.language.orEmpty()}-${key.key}"

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
     */
    data class Results(val entries: List<VocabularyEntry>, val query: String, val masteryLevel: MasteryLevel? = null) :
        VocabularyUiState
}

/**
 * The vocabulary list's actions (issue #23, filter #66): change the search
 * query, change the mastery level filter (`null` is every level) and delete an
 * entry. It is a section of the shell now, so the bar replaces its back action
 * (issue #185).
 */
data class VocabularyActions(
    val onSearch: (String) -> Unit = {},
    val onFilter: (MasteryLevel?) -> Unit = {},
    val onDelete: (VocabularyEntry) -> Unit = {},
)

/**
 * The vocabulary list (issue #23): the saved words with their translation,
 * context sentence and mastery level, searchable and deletable, with the
 * instructional empty state `docs/design.md` prescribes when nothing is saved
 * (§7.4).
 *
 * The screen is deliberately stateless: it renders [state] and raises [actions],
 * so its behaviours are testable through semantics alone. The composition root
 * (`App`) owns the query and the delete, and runs the blocking vault write off
 * the UI thread.
 */
@Composable
fun VocabularyScreen(
    state: VocabularyUiState,
    actions: VocabularyActions = VocabularyActions(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Header()
        when (state) {
            VocabularyUiState.Empty -> Text(
                text = LocalAppTexts.current[TextKey.VOCABULARY_EMPTY],
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 24.dp),
            )

            is VocabularyUiState.Results -> Results(state, actions)
        }
    }
}

/** The screen's title alone: the shell's bar and the back stack replace its old way back (issue #185). */
@Composable
private fun Header() {
    Text(
        text = LocalAppTexts.current[TextKey.VOCABULARY],
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.testTag(VOCABULARY_TITLE_TAG),
    )
}

@Composable
private fun ColumnScope.Results(state: VocabularyUiState.Results, actions: VocabularyActions) {
    val texts = LocalAppTexts.current
    Search(state.query, actions.onSearch)
    MasteryFilter(state.masteryLevel, actions.onFilter)
    if (state.entries.isEmpty()) {
        Text(
            text = noMatchMessage(texts, state.query, state.masteryLevel),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
    } else {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            items(state.entries, key = { entry -> entry.key.rowKey() }) { entry ->
                EntryRow(entry, actions.onDelete)
            }
        }
    }
}

@Composable
private fun Search(query: String, onSearch: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onSearch,
        label = { Text(LocalAppTexts.current[TextKey.SEARCH]) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(VOCABULARY_SEARCH_TAG),
    )
}

/**
 * The mastery filter (issue #66): an "All levels" chip and one per level, so the
 * list can be narrowed to a level. The chips are the shared [MasteryChip], so
 * the filter and the lookup panel's selector stay identical.
 */
@Composable
private fun MasteryFilter(selected: MasteryLevel?, onFilter: (MasteryLevel?) -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MasteryChip(
            level = null,
            label = texts[TextKey.ALL_LEVELS],
            selected = selected == null,
            onClick = { onFilter(null) },
            modifier = Modifier.testTag(vocabularyFilterTag(null)),
        )
        MasteryLevel.entries.forEach { level ->
            MasteryChip(
                level = level,
                label = texts.masteryName(level),
                selected = level == selected,
                onClick = { onFilter(level) },
                modifier = Modifier.testTag(vocabularyFilterTag(level)),
            )
        }
    }
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

/** One entry: its word, translation, context sentence, mastery and delete action. */
@Composable
private fun EntryRow(entry: VocabularyEntry, onDelete: (VocabularyEntry) -> Unit) {
    val texts = LocalAppTexts.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry.surface, style = MaterialTheme.typography.titleMedium)
            TextButton(
                onClick = { onDelete(entry) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.handCursor().testTag(vocabularyDeleteTag(entry.key)),
            ) { Text(texts[TextKey.DELETE]) }
        }
        Mastery(entry.mastery, entry.key)
        entry.translation?.let { translation ->
            Text(translation, style = MaterialTheme.typography.bodyMedium)
        }
        entry.contextSentence?.let { sentence ->
            Text(sentence, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

/**
 * The entry's mastery: its level named in ordinary ink, with the reader's colour
 * as a swatch beside it. The name is what carries the meaning, so the level is
 * legible even where the palette's light colours have too little contrast to be
 * read as text; the swatch ties the entry to the colour the reader paints it.
 */
@Composable
private fun Mastery(level: MasteryLevel, key: WordKey) {
    val texts = LocalAppTexts.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val theme = MaterialTheme.colorScheme
        Box(
            Modifier
                .size(10.dp)
                .background(level.readerColorOr(theme.outline, MasteryPalette.of(theme)), CircleShape),
        )
        Text(
            texts.masteryName(level),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag(vocabularyMasteryTag(key)),
        )
    }
}

/**
 * The Bundle-safe key for a word's row (issue #69). Compose saves a lazy item's
 * state through a saveable-state holder, and Android only accepts a key it can
 * put in a `Bundle`; a [WordKey] is a data class and is not one, so keying a
 * `LazyColumn` on it crashed the vocabulary list on Android. This projects the
 * key onto the `String` Android needs, joined the same way the vault's own
 * record id joins its fields, so distinct words get distinct rows.
 */
internal fun WordKey.rowKey(): String = "${language.orEmpty()}\u0000$key"

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
