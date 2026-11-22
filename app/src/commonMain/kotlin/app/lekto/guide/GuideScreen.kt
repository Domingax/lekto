@file:Suppress("TooManyFunctions")
// One guide: its frame, the language list, the page's two beats, the tap's sheet and the band list.

package app.lekto.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.dictionary.dictionaryShortcuts
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.text.baseLanguage
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.dictionary.Pronunciation
import app.lekto.dictionary.VocabularyPanel
import app.lekto.dictionary.WordLookupActions
import app.lekto.dictionary.WordLookupPanel
import app.lekto.reader.MasteryPalette
import app.lekto.reader.ReaderDocument
import app.lekto.reader.WordTap
import app.lekto.reader.readerColor
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.contentColorOn
import app.lekto.ui.TextAction
import app.lekto.ui.handCursor
import app.lekto.ui.icons.ChevronLeft
import app.lekto.ui.icons.ChevronRight
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.plainClickable
import app.lekto.ui.rowHover

/** The test tag on the guide's current title, so a test can read the screen it is on. */
internal const val GUIDE_TITLE_TAG: String = "guide-title"

/** The test tag on `Skip`, which sits on every guide screen. */
internal const val GUIDE_SKIP_TAG: String = "guide-skip"

/** The test tag on the chevron that returns to the previous screen. */
internal const val GUIDE_BACK_TAG: String = "guide-back"

/** The test tag on the guide's `Continue`, on the question and on the page. */
internal const val GUIDE_CONTINUE_TAG: String = "guide-continue"

/** The test tag on the embedded page block, so a test can tap a word on it. */
internal const val GUIDE_PAGE_TAG: String = "guide-page"

/** The test tag on the embedded page block's text, so a test can read the words it shows. */
internal const val GUIDE_PAGE_TEXT_TAG: String = "guide-page-text"

/** The test tag on the level screen's filled action. */
internal const val GUIDE_START_READING_TAG: String = "guide-start-reading"

/** The test tag on the five step marks, so a test can tell the guide from the shell. */
internal const val GUIDE_STEPS_TAG: String = "guide-steps"

/** The test tag on the language question's scrolling list. */
internal const val GUIDE_LANGUAGE_LIST_TAG: String = "guide-language-list"

/** The test tag on the coloured page's four notes. */
internal const val GUIDE_NOTES_TAG: String = "guide-notes"

/** The test tag on the level screen's page preview card. */
internal const val GUIDE_LEVEL_PREVIEW_TAG: String = "guide-level-preview"

/** The number of marks in the guide's progress row: the four guide screens plus the arrival. */
private const val GUIDE_STEPS = 5

/** The height the guide gives the embedded page, so the paragraph breathes. */
private val PAGE_HEIGHT = 320.dp

/** The height the level screen's preview card gives the page, so the six bands fit under it. */
private val PREVIEW_HEIGHT = 260.dp

/** The test tag on one language row, so a test can choose exactly one. */
internal fun guideLanguageRowTag(language: String): String = "guide-language-$language"

/** The test tag on one coloured page note, so a test can read the level it names. */
internal fun guideNoteTag(level: MasteryLevel): String = "guide-note-${level.name}"

/** The test tag on one CEFR band row, so a test can choose exactly one. */
internal fun guideBandTag(level: CefrLevel): String = "guide-band-${level.name}"

/**
 * The actions the guide raises (issue #277): the language selection, the
 * `Continue` that answers it, the tap on a page word, the sheet's dismissal, the
 * level that saves the word, the band that colours the page, the back affordance,
 * `Skip`, the arrival's start, and the download's `Stop` and retry. The real
 * surfaces raise their own events through these, so a test drives the whole flow
 * with plain lambdas.
 */
data class GuideActions(
    val onSelectLanguage: (String) -> Unit = {},
    val onContinue: () -> Unit = {},
    val onTapWord: (WordTap) -> Unit = {},
    val onDismissLookup: () -> Unit = {},
    val onSaveWord: (MasteryLevel) -> Unit = {},
    val onChooseBand: (CefrLevel) -> Unit = {},
    val onBack: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onStopDownload: () -> Unit = {},
    val onRetryDownload: () -> Unit = {},
    val onResumeDownload: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onSpeakWord: () -> Unit = {},
)

/**
 * One guide screen's scene (issue #277): the flow's state, the page and notes it
 * produced, the download's line and the actions the screen raises. Bundled so the
 * same five values do not travel as five parameters through every beat.
 */
private class GuideBeat(
    val state: GuideUiState,
    val document: ReaderDocument?,
    val notes: List<GuideNote>,
    val download: GuideDownloadUiState,
    val actions: GuideActions,
)

/**
 * The first-launch guide's screens (issue #277), after the five satellite tickets
 * landed: the language question, the sample's page in two beats with its four
 * notes, the tap on the real lookup sheet, and the six CEFR bands under the same
 * page. The screens are render-only; [GuideController] owns the flow.
 *
 * The page is the guide's embedded [GuidePageBlock] — the reader's own word layer,
 * dotted rules and retained layout, inside the guide's frame and without the
 * reader's chrome, page turns or **Aa** panel — and the sheet is the reader's own
 * [WordLookupPanel] fed from the sample's glossary, so neither a drawing nor a
 * second implementation can drift from what the product actually shows. The
 * sample's colours come from its own band map.
 *
 * The arrangement is the guide's, not the reader's: the progress marks, the back
 * chevron and `Skip` share one bar, each screen's title is the guide's own, and the
 * page keeps a fixed height so the notes, the bands or the sheet beside it stay in
 * view. The download card appears on the language question and on the arrival
 * only. On desktop the arrow keys page the screens and `Escape` is `Skip`
 * (installed by the composition root).
 */
@Suppress("LongParameterList") // The state, its roster, the page, the notes, the download and the actions.
@Composable
fun GuideScreen(
    state: GuideUiState,
    languages: List<OfferedLanguage>,
    document: ReaderDocument?,
    notes: List<GuideNote> = emptyList(),
    download: GuideDownloadUiState = GuideDownloadUiState(),
    translationLanguage: String? = null,
    actions: GuideActions = GuideActions(),
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        val beat = GuideBeat(state, document, notes, download, actions)
        Column(Modifier.fillMaxSize()) {
            GuideBar(state.stage, actions.onBack, actions.onSkip)
            when (state.stage) {
                GuideStage.LANGUAGE -> LanguageQuestion(beat, languages)
                GuideStage.PAGE -> PageBeat(beat)
                GuideStage.TAP -> TapBeat(beat)
                GuideStage.LEVEL -> LevelBeat(beat)
            }
        }
        if (state.stage == GuideStage.TAP && state.lookupOpen) {
            TapSheet(beat, state.tapped, translationLanguage)
        }
    }
}

/** The guide's bar: the back chevron at the head, then the step marks, `Skip` at the trailing edge. */
@Composable
private fun GuideBar(stage: GuideStage, onBack: () -> Unit, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsideLabelPair),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stage != GuideStage.LANGUAGE) {
            BackAffordance(onBack)
            Spacer(Modifier.width(LektoSpacing.InsidePanel))
        }
        StepMarks(stage)
        Spacer(Modifier.weight(1f))
        SkipAction(onSkip)
    }
}

/** The five marks: the reached screens filled, the rest outlined. */
@Composable
private fun StepMarks(stage: GuideStage) {
    Row(
        modifier = Modifier.testTag(GUIDE_STEPS_TAG),
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(GUIDE_STEPS) { index ->
            Box(
                Modifier
                    .width(18.dp)
                    .height(3.dp)
                    .background(
                        if (index < stage.position) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            )
        }
    }
}

/** The chevron that returns to the previous screen, beside `Skip`. */
@Composable
private fun BackAffordance(onBack: () -> Unit) {
    val texts = LocalAppTexts.current
    TextAction(onClick = onBack, modifier = Modifier.testTag(GUIDE_BACK_TAG)) {
        Icon(
            imageVector = LektoIcons.ChevronLeft,
            contentDescription = texts[TextKey.BACK],
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The guide's way out, on every screen. */
@Composable
private fun SkipAction(onSkip: () -> Unit) {
    val texts = LocalAppTexts.current
    TextAction(onClick = onSkip, modifier = Modifier.testTag(GUIDE_SKIP_TAG)) {
        Text(texts[TextKey.GUIDE_SKIP], style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Screen 1: the language question. One row per offered language — its code and its
 * name — and no preselection. A row tap selects it and starts the background
 * download, the card names their combined size and offers `Stop`, and `Continue`
 * appears once a language is chosen. The list is the roster the caller resolved, so
 * it reads no manifest and shows no spinner; eight rows scroll at 320 dp. A build
 * that ships one sample never reaches this screen.
 */
@Composable
private fun ColumnScope.LanguageQuestion(beat: GuideBeat, languages: List<OfferedLanguage>) {
    val texts = LocalAppTexts.current
    val selected = beat.state.selectedLanguage
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = LektoSpacing.ScreenEdge)) {
        GuideHeading(texts[TextKey.GUIDE_LANGUAGE_TITLE], texts[TextKey.GUIDE_LANGUAGE_INTRO])
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = LektoSpacing.BetweenBlocks)
                .testTag(GUIDE_LANGUAGE_LIST_TAG),
        ) {
            items(languages, key = { offered -> offered.language }) { offered ->
                LanguageRow(offered, selected = offered.language == selected, onSelect = beat.actions.onSelectLanguage)
                Hairline()
            }
        }
        GuideDownloadLine(
            beat.download,
            GuideDownloadActions(
                onStop = beat.actions.onStopDownload,
                onRetry = beat.actions.onRetryDownload,
                onResume = beat.actions.onResumeDownload,
            ),
            modifier = Modifier.padding(top = LektoSpacing.BetweenRows),
        )
        Text(
            text = texts[TextKey.GUIDE_LANGUAGE_FOOTNOTE],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = LektoSpacing.InsidePanel),
        )
        if (selected != null) {
            Button(
                onClick = beat.actions.onContinue,
                modifier = Modifier.align(Alignment.End).handCursor().testTag(GUIDE_CONTINUE_TAG),
            ) {
                Text(texts[TextKey.GUIDE_CONTINUE])
            }
        }
    }
}

/** One language: its code in the muted ink, its name, the chevron, and the ink rule when selected. */
@Composable
private fun LanguageRow(offered: OfferedLanguage, selected: Boolean, onSelect: (String) -> Unit) {
    val texts = LocalAppTexts.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LektoSpacing.TouchTarget)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .rowHover(hovered, MaterialTheme.colorScheme.onBackground)
            .handCursor()
            .hoverable(interaction)
            .plainClickable(role = Role.Button) { onSelect(offered.language) }
            .semantics { this.selected = selected }
            .testTag(guideLanguageRowTag(offered.language))
            .padding(vertical = LektoSpacing.BetweenRows),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        InkRule(selected)
        LanguageCode(offered.language)
        Text(
            text = texts.languageName(offered.language),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = LektoIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The language's code, in the muted ink, in the fixed column every row shares. */
@Composable
private fun LanguageCode(language: String) {
    Text(
        text = language.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(28.dp),
    )
}

/** The 2 dp ink rule that marks a chosen row, transparent when the row is not chosen. */
@Composable
private fun InkRule(selected: Boolean) {
    Box(
        Modifier
            .width(2.dp)
            .height(24.dp)
            .background(if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent),
    )
}

/**
 * Screen 2: the page in two beats. The first shows the paragraph with no colour at
 * all and promises no gesture; the second paints the same paragraph from the
 * sample's own map and explains four of its words, one per level, under it. The
 * download card is not here: it lives on the question and on the arrival.
 */
@Composable
private fun ColumnScope.PageBeat(beat: GuideBeat) {
    val state = beat.state
    val texts = LocalAppTexts.current
    val title = if (state.plain) TextKey.GUIDE_PAGE_PLAIN_TITLE else TextKey.GUIDE_PAGE_COLOURED_TITLE
    val intro = if (state.plain) TextKey.GUIDE_PAGE_PLAIN_INTRO else TextKey.GUIDE_PAGE_COLOURED_INTRO
    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge),
    ) {
        GuideHeading(texts[title], texts[intro])
        GuideReadingIn(state.sample?.language)
        beat.document?.let { page ->
            GuidePageBlock(page, Modifier.fillMaxWidth().height(PAGE_HEIGHT).padding(top = LektoSpacing.BetweenRows))
        }
        if (!state.plain) GuideNotes(beat.notes, Modifier.padding(top = LektoSpacing.BetweenRows))
        GuideContinue(
            beat.actions.onContinue,
            Modifier.padding(top = LektoSpacing.InsidePanel).align(Alignment.End),
        )
    }
}

/**
 * Screen 3: the tap. One word carries the selection wash, and the real sheet is
 * raised only when the reader taps a word the sample's glossary answers — the
 * demonstrated word is the natural target, but any word with a meaning answers and
 * a tap on empty space does nothing. A level saves **that** word into the real
 * **Vocabulary** and recolours it on the page; closing the sheet writes nothing.
 * `Continue` remains the way forward.
 */
@Composable
private fun ColumnScope.TapBeat(beat: GuideBeat) {
    val state = beat.state
    val texts = LocalAppTexts.current
    val washed = state.tapped ?: beat.notes.firstOrNull()
    val marked = beat.document?.let { page ->
        when (val note = washed) {
            null -> page
            else -> page.copy(selectedRange = note.start until note.end)
        }
    }
    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge),
    ) {
        GuideHeading(texts[TextKey.GUIDE_TAP_TITLE], texts[TextKey.GUIDE_TAP_INTRO])
        GuideReadingIn(state.sample?.language)
        marked?.let { page ->
            GuidePageBlock(
                page,
                Modifier.fillMaxWidth().height(PAGE_HEIGHT).padding(top = LektoSpacing.BetweenRows),
                selectedRange = page.selectedRange,
                onWordTap = beat.actions.onTapWord,
            )
        }
        Text(
            text = texts[TextKey.GUIDE_TAP_FOOTNOTE],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
        )
        if (!state.lookupOpen) {
            GuideContinue(
                beat.actions.onContinue,
                Modifier.padding(top = LektoSpacing.InsidePanel).align(Alignment.End),
            )
        }
    }
}

/** The real sheet over the tap screen, for the word the reader tapped and the sample's answer. */
@Composable
private fun BoxScope.TapSheet(beat: GuideBeat, note: GuideNote?, translationLanguage: String?) {
    val sample = beat.state.sample ?: return
    if (note == null) return
    val lookup = remember(sample, note, translationLanguage) { guideLookup(sample, note, translationLanguage) }
    val shortcuts = remember(note, translationLanguage) {
        dictionaryShortcuts(note.surface, baseLanguage(sample.language), translationLanguage)
    }
    WordLookupPanel(
        result = lookup,
        term = note.surface,
        shortcuts = shortcuts,
        actions = WordLookupActions(onOpenShortcut = {}, onDismiss = beat.actions.onDismissLookup),
        pronunciation = Pronunciation(onSpeak = beat.actions.onSpeakWord),
        vocabulary = VocabularyPanel(
            entry = beat.state.savedLevel?.takeIf { beat.state.savedKey == note.key }?.let { level ->
                savedEntry(note, level)
            },
            onSave = beat.actions.onSaveWord,
            saveFailed = beat.state.saveFailed,
        ),
        context = note.context,
        onOpenSettings = beat.actions.onOpenSettings,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}

/** The entry the footer reads its saved level from; the vault holds the real one. */
private fun savedEntry(note: GuideNote, level: MasteryLevel): VocabularyEntry = VocabularyEntry.saved(
    key = note.key,
    surface = note.surface,
    translation = note.meaning,
    contextSentence = note.context,
    mastery = level,
)

/**
 * Screen 4: the same page above the six CEFR bands, one line each. Choosing a band
 * writes the **Reader level** through the Languages surface's own action and
 * repaints the page; the filled action finishes the guide onto the shelf. The
 * download card is not here either.
 */
@Composable
private fun ColumnScope.LevelBeat(beat: GuideBeat) {
    val state = beat.state
    val texts = LocalAppTexts.current
    val language = state.sample?.language
    val title = texts.message(
        AppMessage.of(TextKey.GUIDE_LEVEL_TITLE, language?.let(texts::languageName).orEmpty()),
    )
    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge),
    ) {
        GuideHeading(title, texts[TextKey.GUIDE_LEVEL_INTRO])
        GuideReadingIn(language)
        PreviewCard(beat.document)
        BandList(state.chosenBand, beat.actions.onChooseBand)
        Button(
            onClick = beat.actions.onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LektoSpacing.BetweenBlocks)
                .handCursor()
                .testTag(GUIDE_START_READING_TAG),
        ) {
            Text(texts[TextKey.GUIDE_START_READING])
        }
    }
}

/** The level screen's card: the embedded page at the band being chosen, under its label. */
@Composable
private fun PreviewCard(document: ReaderDocument?) {
    val texts = LocalAppTexts.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRows).testTag(GUIDE_LEVEL_PREVIEW_TAG),
        shape = LektoRadii.Surface,
        color = MaterialTheme.colorScheme.background,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(LektoSpacing.InsidePanel)) {
            Text(
                text = texts[TextKey.GUIDE_LEVEL_PREVIEW],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            document?.let { page ->
                GuidePageBlock(page, Modifier.fillMaxWidth().height(PREVIEW_HEIGHT))
            }
        }
    }
}

/**
 * The six bands: code, name, the one thing each reads, and the tapped one washed
 * and ruled. [chosen] is `null` until the reader taps a band, so the default band
 * the page previews at is a suggestion, never a selection.
 */
@Composable
private fun BandList(chosen: CefrLevel?, onChoose: (CefrLevel) -> Unit) {
    val texts = LocalAppTexts.current
    Column(Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRows)) {
        CefrLevel.entries.forEachIndexed { index, level ->
            BandRow(level, selected = level == chosen, name = texts.bandName(level), line = texts.bandLine(level)) {
                onChoose(level)
            }
            if (index != CefrLevel.entries.lastIndex) Hairline()
        }
    }
}

/** One band row: its code, its name, what it reads, and the ink rule that marks the choice. */
@Suppress("LongParameterList") // The band, its choice, its name, its one line and the tap.
@Composable
private fun BandRow(level: CefrLevel, selected: Boolean, name: String, line: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LektoSpacing.TouchTarget)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .rowHover(hovered, MaterialTheme.colorScheme.onBackground)
            .handCursor()
            .hoverable(interaction)
            .plainClickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(guideBandTag(level))
            .padding(vertical = LektoSpacing.BetweenRelatedLines),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkRule(selected)
        Text(
            text = level.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = LektoSpacing.BetweenRelatedLines).width(32.dp),
        )
        Text(text = name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The coloured page's four notes, one per level: its chip and number, the word, the level, the meaning. */
@Composable
private fun GuideNotes(notes: List<GuideNote>, modifier: Modifier = Modifier) {
    val texts = LocalAppTexts.current
    val ramp = MasteryPalette.of(MaterialTheme.colorScheme)
    Column(
        modifier.fillMaxWidth().testTag(GUIDE_NOTES_TAG),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows),
    ) {
        notes.forEach { note ->
            NoteRow(note, texts.masteryName(note.level), note.level.readerColor(ramp))
        }
    }
}

/** One note: the level's coloured number beside the word, its name and the meaning. */
@Composable
private fun NoteRow(note: GuideNote, levelName: String, colour: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(guideNoteTag(note.level)),
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(colour, LektoRadii.Control),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = note.level.level.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = contentColorOn(colour),
            )
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
                Text(
                    text = note.surface,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = levelName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = note.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A screen's big title and its one supporting line. */
@Composable
private fun GuideHeading(title: String, intro: String) {
    Column(Modifier.fillMaxWidth().padding(top = LektoSpacing.InsidePanel)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(GUIDE_TITLE_TAG),
        )
        Text(
            text = intro,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
        )
    }
}

/**
 * The language the sample is read in, under a page's title (issue #277): a build
 * that ships one sample never asks the question, so its page has to name the
 * language it opened in.
 */
@Composable
private fun GuideReadingIn(language: String?) {
    if (language.isNullOrBlank()) return
    val texts = LocalAppTexts.current
    Text(
        text = texts.message(AppMessage.of(TextKey.GUIDE_READING_IN, texts.languageName(language))),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = LektoSpacing.InsideLabelPair),
    )
}

/** The page's quiet way forward, aligned to the trailing edge as the mockups draw it. */
@Composable
private fun GuideContinue(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextAction(onClick = onClick, modifier = modifier.testTag(GUIDE_CONTINUE_TAG)) {
        Text(LocalAppTexts.current[TextKey.GUIDE_CONTINUE], style = MaterialTheme.typography.bodyLarge)
    }
}

/** The 1 dp hairline that parts two rows, never the first from the bar. */
@Composable
private fun Hairline() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}
