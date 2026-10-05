package app.lekto.vocabulary

import androidx.compose.foundation.background
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
import app.lekto.reader.readerColorOr

/** The test tag on the search field, so a UI test can type into it unambiguously. */
internal const val VOCABULARY_SEARCH_TAG: String = "vocabulary-search"

/** The test tag on an entry's delete button, so a UI test can click exactly one. */
internal fun vocabularyDeleteTag(key: WordKey): String = "vocabulary-delete-${key.language.orEmpty()}-${key.key}"

/**
 * The vocabulary list's state (issue #23), one value per thing the screen can
 * show, so "nothing saved yet" cannot be confused with "this search matched
 * nothing".
 */
sealed interface VocabularyUiState {

    /** The vocabulary holds nothing, so the screen shows the instructional empty state. */
    data object Empty : VocabularyUiState

    /**
     * The vocabulary holds entries: those matching [query] — every entry when
     * [query] is blank — and the [query] itself. An empty [entries] here means
     * the query matched nothing.
     */
    data class Results(val entries: List<VocabularyEntry>, val query: String) : VocabularyUiState
}

/**
 * The vocabulary list's actions (issue #23): change the search query, delete an
 * entry, and go back to the library.
 */
data class VocabularyActions(
    val onSearch: (String) -> Unit = {},
    val onDelete: (VocabularyEntry) -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The vocabulary list (issue #23): the saved words with their translation,
 * context sentence and mastery level, searchable and deletable, with the
 * instructional empty state the UX spec prescribes when nothing is saved
 * (`docs/ux-design-specification.md`, "Empty States").
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
        Header(actions.onBack)
        when (state) {
            VocabularyUiState.Empty -> Text(
                text = "No words saved yet — tap a word while reading to save it",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 24.dp),
            )

            is VocabularyUiState.Results -> Results(state, actions)
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onBack) { Text("Library") }
        Text("Vocabulary", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun ColumnScope.Results(state: VocabularyUiState.Results, actions: VocabularyActions) {
    Search(state.query, actions.onSearch)
    if (state.entries.isEmpty()) {
        Text(
            text = "No words match \"${state.query}\".",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
    } else {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            items(state.entries, key = { entry -> entry.key }) { entry ->
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
        label = { Text("Search") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(VOCABULARY_SEARCH_TAG),
    )
}

/** One entry: its word, translation, context sentence, mastery and delete action. */
@Composable
private fun EntryRow(entry: VocabularyEntry, onDelete: (VocabularyEntry) -> Unit) {
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
                modifier = Modifier.testTag(vocabularyDeleteTag(entry.key)),
            ) { Text("Delete") }
        }
        Mastery(entry.mastery)
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
private fun Mastery(level: MasteryLevel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .background(level.readerColorOr(MaterialTheme.colorScheme.outline), CircleShape),
        )
        Text(level.label(), style = MaterialTheme.typography.labelLarge)
    }
}

/** A mastery level's readable name, so the list shows the level and not just its colour. */
private fun MasteryLevel.label(): String = when (this) {
    MasteryLevel.UNKNOWN -> "Unknown"
    MasteryLevel.FAMILIAR -> "Familiar"
    MasteryLevel.RECOGNIZED -> "Recognized"
    MasteryLevel.MASTERED -> "Mastered"
    MasteryLevel.KNOWN -> "Known"
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
