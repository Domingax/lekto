package app.lekto.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionaryShortcut
import app.lekto.core.dictionary.DictionarySource
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.speech.SpeechResult
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.reader.MasterySelector
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.LookupCloseButton
import app.lekto.ui.LookupHeader
import app.lekto.ui.LookupSheet
import app.lekto.ui.SheetDivider
import app.lekto.ui.handCursor
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Volume2

/** The test tag on a source's reference, so a UI test can click exactly one. */
internal fun sourceShortcutTag(source: DictionarySource): String = "lookup-source-${source.name}"

/** The test tag on the pronunciation button, so a UI test can click it unambiguously. */
internal const val PRONOUNCE_TAG: String = "lookup-pronounce"

/** The test tag on the sheet's close action. */
internal const val LOOKUP_CLOSE_TAG: String = "lookup-close"

/** The test tag on the footer's state line, so a layout test can compare it with the chips. */
internal const val LOOKUP_LEVEL_TAG: String = "lookup-level"

/** The test tag on the sheet itself, so a layout test can read how much of the screen it takes. */
internal const val LOOKUP_PANEL_TAG: String = "lookup-panel"

/** The test tag on a mastery level's button, so a UI test can click exactly one. */
internal fun masteryTag(level: MasteryLevel): String = "lookup-mastery-${level.name}"

/** The breathing room around the dot between two references. */
private val ReferenceGap = 4.dp

/**
 * The lookup panel's pronunciation control (issue #21): the action the speaker
 * runs and the last honest [result], so a language with no voice renders its
 * message instead of staying silent.
 */
data class Pronunciation(val onSpeak: () -> Unit, val result: SpeechResult? = null)

/**
 * The lookup panel's vocabulary controls (issues #22 and #279): the saved [entry]
 * for the tapped word (`null` until it is saved), the [onSave] that saves it or
 * moves it to another level, the [suggestedLevel] a fresh save would start at,
 * and whether the last write [saveFailed]. One tap on a level chip saves at that
 * level (issue #210).
 *
 * The footer states a fact or a suggestion, never an intention: the saved level
 * is a fact, the suggested one is only likely, and neither is a selection until
 * the vault holds the entry.
 */
data class VocabularyPanel(
    val entry: VocabularyEntry?,
    val onSave: (MasteryLevel) -> Unit,
    val suggestedLevel: MasteryLevel? = null,
    val saveFailed: Boolean = false,
) {
    /** The level the word is saved at, or `null` while it is not — the fact the footer states. */
    val savedLevel: MasteryLevel? get() = entry?.mastery
}

/**
 * The panel's outbound actions (issues #19 and #22), bundled so the panel's
 * signature fits the parameter bound: [onOpenShortcut] hands a reference page to
 * the platform browser and [onDismiss] closes the panel.
 */
data class WordLookupActions(val onOpenShortcut: (DictionaryShortcut) -> Unit, val onDismiss: () -> Unit)

/**
 * The word lookup panel (issue #19; docs/design.md §7.3): the signature
 * interaction, raised from the bottom over the reader as a **sheet** since issue
 * #210. It opens on the word the reader tapped with the offline result already in
 * it, and always offers the external references beside that result — so with no
 * pack and no network it still degrades to the references and an honest message
 * rather than an empty panel. On desktop it is the reader's context column instead
 * (issue #199), which is what [inColumn] asks of the shared [LookupSheet].
 *
 * The sheet covers at most [app.lekto.ui.LOOKUP_SHEET_MAX_HEIGHT_FRACTION] of the
 * screen, so the page stays visible and the touched word stays washed above it.
 * Its header is the word in the serif with its part of speech and pronunciation,
 * the speaker and the close as icons; its body lists the senses, each with its
 * translation, then the external references as last line of the same scroll; its
 * footer **states the word's state** — `Saved · <level>` once it
 * is stored, `Likely <level>` or `Not saved` before, `Couldn't save` after a
 * refused write — and carries the five chips (issue #279). **There is no Save
 * button**: a level *is* the save, one tap for one intention, and the line
 * updating is the receipt.
 *
 * The sheet swallows taps that land on it, so only a tap truly outside it — which
 * its [app.lekto.ui.DismissScrim] handles — dismisses it; a tap on the reader
 * behind it then never turns the page or opens another lookup.
 */
@Suppress("LongParameterList") // The panel's inputs are independent; a bundle would only hide that.
@Composable
fun WordLookupPanel(
    result: WordLookup,
    term: String,
    shortcuts: List<DictionaryShortcut>,
    actions: WordLookupActions,
    pronunciation: Pronunciation,
    vocabulary: VocabularyPanel = VocabularyPanel(entry = null, onSave = {}),
    modifier: Modifier = Modifier,
    inColumn: Boolean = false,
) {
    LookupSheet(
        modifier = modifier,
        sheetModifier = Modifier.testTag(LOOKUP_PANEL_TAG),
        inColumn = inColumn,
    ) {
        SheetContent(term, result, pronunciation, shortcuts, actions.onOpenShortcut, actions.onDismiss)
        Footer(vocabulary, inColumn)
    }
}

/**
 * The header, the answer and the references, in one scroll: on a phone the sheet
 * fits them under the cap, and on a short screen — a landscape phone — the header
 * scrolls with the senses rather than pushing the pinned footer past the edge. The
 * references come last, after the answer, so a long reference line can never take
 * height from the definition at rest (issue #282). The footer stays, so the level
 * is always reachable.
 */
@Suppress("LongParameterList") // The answer, its references and the two ways out: independent inputs.
@Composable
private fun ColumnScope.SheetContent(
    term: String,
    result: WordLookup,
    pronunciation: Pronunciation,
    shortcuts: List<DictionaryShortcut>,
    onOpenShortcut: (DictionaryShortcut) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        SheetHeader(term, result, pronunciation, onDismiss)
        SheetDivider()
        SpeechMessage(pronunciation.result)
        Answer(result)
        References(shortcuts, onOpenShortcut)
    }
}

/**
 * The word in the serif, its part of speech and pronunciation under it, and the
 * speaker and close as icons (docs/design.md §3, §7.3). The lemma stands on its
 * own muted line when the tapped surface is an inflection, so a reader can tell
 * why the definition does not match the word they touched.
 */
@Composable
private fun SheetHeader(term: String, result: WordLookup, pronunciation: Pronunciation, onDismiss: () -> Unit) {
    val texts = LocalAppTexts.current
    val found = result as? WordLookup.Found
    LookupHeader {
        Column(Modifier.weight(1f).padding(bottom = LektoSpacing.BetweenRelatedLines)) {
            Text(term, style = MaterialTheme.typography.headlineMedium)
            Lemma(term, found?.lemma)
            Meta(found?.entries.orEmpty())
        }
        IconButton(onClick = pronunciation.onSpeak, modifier = Modifier.handCursor().testTag(PRONOUNCE_TAG)) {
            Icon(imageVector = LektoIcons.Volume2, contentDescription = texts[TextKey.LISTEN])
        }
        LookupCloseButton(onDismiss = onDismiss, tag = LOOKUP_CLOSE_TAG)
    }
}

/** The dictionary form the tapped surface resolved to, when it differs from what the reader touched. */
@Composable
private fun Lemma(term: String, lemma: String?) {
    if (lemma == null || lemma == term) return
    Text(lemma, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Every part of speech the pack gives the word, then its pronunciation, under
 * the word. A word with several readings names them all rather than claiming the
 * first, and the body repeats the one each group of senses belongs to.
 */
@Composable
private fun Meta(entries: List<DictionaryEntry>) {
    val parts = entries.mapNotNull(DictionaryEntry::partOfSpeech).distinct() +
        listOfNotNull(entries.firstNotNullOfOrNull(DictionaryEntry::pronunciation))
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The pinned footer: the word's state and the level that saves (docs/design.md
 * §7.3). The state line names the saved level as a fact, the suggested one as
 * only likely, and a refused write as a failure — so one tap on a chip stores the
 * word and the footer states what happened (issue #279). The footer holds only
 * that selector: the external references scroll with the answer above it, so no
 * wrapped reference line can take height from the definition (issue #282).
 *
 * In the context column there is no room for the line beside five chips (issue
 * #199), so it stands above them: the same blocks, stacked, rather than a line
 * broken mid-word.
 */
@Composable
private fun Footer(vocabulary: VocabularyPanel, inColumn: Boolean) {
    SheetDivider()
    MasterySelector(
        level = vocabulary.savedLevel,
        onSelect = vocabulary.onSave,
        chipTag = ::masteryTag,
        levelTag = LOOKUP_LEVEL_TAG,
        modifier = Modifier.padding(horizontal = LektoSpacing.InsidePanel),
        stacked = inColumn,
        name = { StateLine(vocabulary) },
    )
}

/**
 * The footer's sentence: `Saved · <level>` in the ink once the vault holds the
 * word, `Couldn't save` after a refused write, `Likely <level>` for a suggestion,
 * and `Not saved` when there is none. A suggestion is never marked like a fact,
 * so the level is muted until it is true.
 */
@Composable
private fun StateLine(vocabulary: VocabularyPanel) {
    val saved = vocabulary.savedLevel
    Text(
        text = stateText(vocabulary, saved),
        style = MaterialTheme.typography.bodyMedium,
        color = if (saved != null && !vocabulary.saveFailed) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/** The footer's text for [vocabulary]'s state, in the reader's language. */
@Composable
private fun stateText(vocabulary: VocabularyPanel, saved: MasteryLevel?): String {
    val texts = LocalAppTexts.current
    return when {
        vocabulary.saveFailed -> texts[TextKey.LOOKUP_STATE_FAILED]

        saved != null -> texts.message(AppMessage.of(TextKey.LOOKUP_STATE_SAVED, texts.masteryName(saved)))

        vocabulary.suggestedLevel != null -> texts.message(
            AppMessage.of(TextKey.LOOKUP_STATE_LIKELY, texts.masteryName(vocabulary.suggestedLevel)),
        )

        else -> texts[TextKey.LOOKUP_STATE_NOT_SAVED]
    }
}

/**
 * The external references as a single muted line, not four buttons (issue #210):
 * each name is still its own control, so a screen reader and a mouse reach each
 * one, but the row reads as the sentence `WordReference · Reverso · …` the design
 * draws. It closes the scrolling body, below the answer, so its wrapping never
 * competes with the definition for the sheet's height (issue #282).
 */
@OptIn(ExperimentalLayoutApi::class) // `FlowRow` wraps a reference line too wide to share one row.
@Composable
private fun References(shortcuts: List<DictionaryShortcut>, onOpenShortcut: (DictionaryShortcut) -> Unit) {
    if (shortcuts.isEmpty()) return
    SheetDivider()
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.Center,
    ) {
        shortcuts.forEachIndexed { index, shortcut ->
            if (index > 0) ReferenceSeparator()
            Reference(shortcut, onOpenShortcut)
        }
    }
}

/** One reference: a muted name that opens its canonical page, with the full 48 dp touch target. */
@Composable
private fun Reference(shortcut: DictionaryShortcut, onOpenShortcut: (DictionaryShortcut) -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = LektoSpacing.TouchTarget)
            .handCursor()
            .clickable { onOpenShortcut(shortcut) }
            .testTag(sourceShortcutTag(shortcut.source)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shortcut.source.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The dot between two references, aligned with them on the line. */
@Composable
private fun ReferenceSeparator() {
    Box(Modifier.heightIn(min = LektoSpacing.TouchTarget), contentAlignment = Alignment.Center) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ReferenceGap),
        )
    }
}
