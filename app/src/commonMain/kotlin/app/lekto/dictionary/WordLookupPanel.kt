package app.lekto.dictionary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionaryShortcut
import app.lekto.core.dictionary.DictionarySource
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.speech.SpeechResult
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.reader.MasteryChip
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Volume2
import app.lekto.ui.icons.X
import app.lekto.ui.swallowTaps

/** The test tag on a source's reference, so a UI test can click exactly one. */
internal fun sourceShortcutTag(source: DictionarySource): String = "lookup-source-${source.name}"

/** The test tag on the pronunciation button, so a UI test can click it unambiguously. */
internal const val PRONOUNCE_TAG: String = "lookup-pronounce"

/** The test tag on the sheet's close action. */
internal const val LOOKUP_CLOSE_TAG: String = "lookup-close"

/** The test tag on the sheet itself, so a layout test can read how much of the screen it takes. */
internal const val LOOKUP_PANEL_TAG: String = "lookup-panel"

/** The test tag on a mastery level's button, so a UI test can click exactly one. */
internal fun masteryTag(level: MasteryLevel): String = "lookup-mastery-${level.name}"

/** The most of the screen the sheet may cover, so the page and the washed word stay visible. */
internal const val SHEET_MAX_HEIGHT_FRACTION: Float = 0.45f

/** The 1 dp hairline a surface draws around itself (docs/design.md §4: no shadows). */
private val Hairline = 1.dp

/** The drag handle's bar: its width and its height. */
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp

/** The gap between two level chips. */
private val ChipGap = 4.dp

/** The breathing room inside one reference's touch target. */
private val ReferencePadding = 4.dp

/**
 * The lookup panel's pronunciation control (issue #21): the action the speaker
 * runs and the last honest [result], so a language with no voice renders its
 * message instead of staying silent.
 */
data class Pronunciation(val onSpeak: () -> Unit, val result: SpeechResult? = null)

/**
 * The lookup panel's vocabulary controls (issue #22): the saved [entry] for the
 * tapped word (`null` until it is saved) and the [onSave] that saves it or moves
 * it to another level. [defaultLevel] is the level a fresh save starts at, so
 * the footer names a level before the word is saved; one tap on a level chip
 * saves at that level (issue #210).
 */
data class VocabularyPanel(
    val entry: VocabularyEntry?,
    val onSave: (MasteryLevel) -> Unit,
    val defaultLevel: MasteryLevel = MasteryLevel.RECOGNIZED,
) {
    /** The level the selector marks: the saved one, or the fresh default. */
    val level: MasteryLevel get() = entry?.mastery ?: defaultLevel
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
 * rather than an empty panel.
 *
 * The sheet covers at most [SHEET_MAX_HEIGHT_FRACTION] of the screen, so the page
 * stays visible and the touched word stays washed above it. Its header is the
 * word in the serif with its part of speech and pronunciation, the speaker and
 * the close as icons; its body lists the senses, each with its translation; its
 * footer names the level and carries the five chips. **There is no Save button**:
 * a level *is* the save, one tap for one intention, and the reader's colour
 * updating is the only confirmation.
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
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxSheetHeight = if (constraints.hasBoundedHeight) maxHeight * SHEET_MAX_HEIGHT_FRACTION else Dp.Infinity
        val theme = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .testTag(LOOKUP_PANEL_TAG),
            shape = LektoRadii.SheetTop,
            color = theme.surface,
            border = BorderStroke(Hairline, theme.outline),
        ) {
            Box(Modifier.fillMaxWidth()) {
                // The sheet's own surface, behind the controls: a tap that lands on
                // no control stops here instead of reaching the scrim over the page,
                // while the controls stay above it and take their tap first.
                Box(Modifier.matchParentSize().swallowTaps())
                Column(Modifier.fillMaxWidth()) {
                    DragHandle()
                    SheetContent(term, result, pronunciation, actions.onDismiss)
                    Footer(shortcuts, actions.onOpenShortcut, vocabulary)
                }
            }
        }
    }
}

/**
 * The header and the answer, in one scroll: on a phone the sheet fits them under
 * the cap, and on a short screen — a landscape phone — the header scrolls with
 * the senses rather than pushing the pinned footer past the edge. The footer
 * stays, so the level and the references are always reachable.
 */
@Composable
private fun ColumnScope.SheetContent(
    term: String,
    result: WordLookup,
    pronunciation: Pronunciation,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        SheetHeader(term, result, pronunciation, onDismiss)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SpeechMessage(pronunciation.result)
        Answer(result)
    }
}

/** The grabber that says the sheet was raised from the bottom edge. It is a hint, not a control. */
@Composable
private fun DragHandle() {
    Box(Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRelatedLines), contentAlignment = Alignment.Center) {
        Box(Modifier.width(HandleWidth).height(HandleHeight).background(MaterialTheme.colorScheme.outline, CircleShape))
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = LektoSpacing.InsidePanel,
            top = LektoSpacing.InsideLabelPair,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(bottom = LektoSpacing.BetweenRelatedLines)) {
            Text(term, style = MaterialTheme.typography.headlineMedium)
            Lemma(term, found?.lemma)
            Meta(found?.entries.orEmpty())
        }
        IconButton(onClick = pronunciation.onSpeak, modifier = Modifier.handCursor().testTag(PRONOUNCE_TAG)) {
            Icon(imageVector = LektoIcons.Volume2, contentDescription = texts[TextKey.LISTEN])
        }
        IconButton(onClick = onDismiss, modifier = Modifier.handCursor().testTag(LOOKUP_CLOSE_TAG)) {
            Icon(imageVector = LektoIcons.X, contentDescription = texts[TextKey.CLOSE])
        }
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
 * The pinned footer: the level that saves, then the external references as one
 * muted line (docs/design.md §7.3). The level's **name** stands beside the chips,
 * so the numbers never need a legend, and one tap on a chip stores the word at
 * that level. In the fixed footer the two stay reachable however long the senses
 * run.
 */
@OptIn(ExperimentalLayoutApi::class) // `FlowRow` drops the chips below the name on a narrow phone.
@Composable
private fun Footer(
    shortcuts: List<DictionaryShortcut>,
    onOpenShortcut: (DictionaryShortcut) -> Unit,
    vocabulary: VocabularyPanel,
) {
    val theme = MaterialTheme.colorScheme
    HorizontalDivider(color = theme.outline)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.InsideLabelPair),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
    ) {
        LevelName(vocabulary.level)
        MasterySelector(vocabulary)
    }
    References(shortcuts, onOpenShortcut)
}

/** The current level's name, in ordinary ink, so a chip's number is never the only signal. */
@Composable
private fun LevelName(level: MasteryLevel) {
    val texts = LocalAppTexts.current
    Column {
        Text(
            text = texts[TextKey.MASTERY],
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(texts.masteryName(level), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The five-point selector (issue #22): tapping a level saves the word there, or
 * moves an already-saved word, so mastery can be changed after saving. Each chip
 * carries its level's colour — the current one filled, the others ringed — so the
 * reader's text colours can be read straight off the selector. The chips are
 * compact, a number, so all five fit one row (docs/design.md, §7.3).
 */
@Composable
private fun MasterySelector(vocabulary: VocabularyPanel) {
    Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
        MasteryLevel.entries.forEach { level ->
            MasteryChip(
                level = level,
                label = level.shortLabel(),
                selected = level == vocabulary.level,
                onClick = { vocabulary.onSave(level) },
                modifier = Modifier.testTag(masteryTag(level)),
                compact = true,
            )
        }
    }
}

/** A selector chip's compact label: its number, and a check for the known level. */
private fun MasteryLevel.shortLabel(): String = when (this) {
    MasteryLevel.UNKNOWN -> "0"
    MasteryLevel.RECOGNIZED -> "1"
    MasteryLevel.FAMILIAR -> "2"
    MasteryLevel.LEARNED -> "3"
    MasteryLevel.KNOWN -> "4"
}

/**
 * The external references as a single muted line, not four buttons (issue #210):
 * each name is still its own control, so a screen reader and a mouse reach each
 * one, but the row reads as the sentence `WordReference · Reverso · …` the design
 * draws. The line wraps rather than clipping when the names outgrow the sheet.
 */
@OptIn(ExperimentalLayoutApi::class) // `FlowRow` wraps a reference line too wide to share one row.
@Composable
private fun References(shortcuts: List<DictionaryShortcut>, onOpenShortcut: (DictionaryShortcut) -> Unit) {
    if (shortcuts.isEmpty()) return
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = LektoSpacing.InsideLabelPair),
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
            modifier = Modifier.padding(horizontal = ReferencePadding),
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
        )
    }
}
