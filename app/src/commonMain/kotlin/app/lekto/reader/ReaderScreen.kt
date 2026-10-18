package app.lekto.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.reading.ReadingPreferences
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.ui.DismissScrim
import kotlinx.coroutines.yield

private val ChromeHeight = 48.dp
private val PageBarHeight = 64.dp

/** How many pages to lay out between yields, so the first page is shown at once. */
private const val PAGE_YIELD_EVERY = 8

/** The test tag on the page, so the UI tests can inject a tap at a zone's coordinates. */
internal const val READER_PAGE_TAG = "reader-page"

/**
 * The reader's callbacks, bundled so the screen's signature stays small: a tap on
 * a word, a phrase selected by a long-press drag, a way back to the library, the
 * position a page turn lands on, a **Reading preference** change (ADR-0035) the
 * **Aa** panel raises, and — when a lookup panel is open above the reader — the
 * dismiss a tap on the page outside it runs.
 */
data class ReaderActions(
    val onWordTap: (WordTap) -> Unit = {},
    val onPhraseSelected: (PhraseSelection) -> Unit = {},
    val onBack: (() -> Unit)? = null,
    val onPositionChange: (Int) -> Unit = {},
    val onReadingPreferencesChange: (ReadingPreferences) -> Unit = {},
    /**
     * The dismiss of a lookup panel open above the reader, or `null` when none is
     * open. When set, a tap on the page closes that panel instead of looking a
     * word up or turning the page, so "tap outside to dismiss" never misfires.
     */
    val onDismissOverlay: (() -> Unit)? = null,
)

/**
 * The reader screen: a book paginated to the viewport, every word coloured by
 * mastery and tappable, with page navigation in both directions, resumed at the
 * saved [ReaderDocument.initialOffset], and chrome that recedes (issue #16).
 *
 * A tap is a **tap zone** (the model Moon+ Reader and the UX spec's "tap zones"
 * describe): the left third turns to the previous page, the right third to the
 * next, and the middle toggles the chrome. A tap on a word is always word lookup,
 * wherever it lands. The page is a plain (non-lazy) [SelectionContainer] so every
 * visible word is composed and selectable; pagination and tokenisation are
 * incremental, so a long book shows its first page without processing the rest.
 *
 * [ReaderActions.onPositionChange] is called with the character offset of each
 * page the reader turns to, so the caller can persist the reading position.
 */
@Composable
fun ReaderScreen(document: ReaderDocument, modifier: Modifier = Modifier, actions: ReaderActions = ReaderActions()) {
    // The reader builds its typography from the preference (ADR-0035), so a change
    // recomposes the text and repaginates at once, the way a Reader level change
    // recolours it.
    val styles = remember(document.preferences) { ReaderStyles.of(document.preferences) }
    val chapterText = remember(document.chapter, styles) {
        buildChapterText(document.chapter, styles)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val pages = rememberReaderPages(chapterText, styles)
        val state = remember(document.chapter) { ReaderState(actions.onPositionChange) }
        state.bind(pages, chapterText.length, document.initialOffset)
        ReaderContent(document, chapterText, styles, state, actions)
    }
}

/**
 * The reader's chrome, page and **Aa** panel. A tap on the page closes whatever
 * panel is open above it — the **Aa** panel or a lookup — and the page scrim keeps
 * that tap from looking a word up or turning the page. The chrome stays usable.
 */
@Suppress("LongParameterList") // The reader's pieces, threaded from the one composition that builds them.
@Composable
private fun BoxWithConstraintsScope.ReaderContent(
    document: ReaderDocument,
    chapterText: AnnotatedString,
    styles: ReaderStyles,
    state: ReaderState,
    actions: ReaderActions,
) {
    Column(Modifier.fillMaxSize()) {
        if (state.chromeVisible) {
            ReaderTitle(document.chapter.title, actions.onBack, state::togglePreferences, styles.horizontalMargin)
        }
        ReaderPage(
            document,
            chapterText,
            styles,
            state,
            overlayDismiss(state, actions),
            actions.onWordTap,
            actions.onPhraseSelected,
        )
        if (state.chromeVisible && state.page != null) {
            ReaderPageBar(state.index, state.total, styles.horizontalMargin, state::previous, state::next)
        }
    }
    if (state.chromeVisible && state.preferencesVisible) {
        ReadingPreferencesPanel(
            preferences = document.preferences,
            onChange = actions.onReadingPreferencesChange,
            onDismiss = state::dismissPreferences,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** The dismiss a tap on the page runs: the **Aa** panel's, or the lookup panel's, or none. */
private fun overlayDismiss(state: ReaderState, actions: ReaderActions): (() -> Unit)? =
    if (state.preferencesVisible) state::dismissPreferences else actions.onDismissOverlay

/**
 * The reader's page state: where the anchor is, which page that is, and the
 * chrome's visibility. The anchor is a character offset, so a reflow — a
 * **Reading preference** change, a resize — [bind]s a new pagination without
 * losing the reader's place (ADR-0035).
 */
@Stable
private class ReaderState(private val onPositionChange: (Int) -> Unit) {
    var chromeVisible by mutableStateOf(true)
        private set

    /** Whether the **Aa** panel is open; it is part of the chrome and hides with it. */
    var preferencesVisible by mutableStateOf(false)
        private set

    private var pages: List<ReaderPage> = emptyList()
    private var textLength: Int = 0
    private var anchor by mutableStateOf(0)
    private var opened = false

    /**
     * Points the state at the latest pagination. The first [bind] — opening the
     * book — takes the saved [initialOffset]; a later one only re-reads the pages,
     * so a preference change repaginates around the same anchor.
     */
    fun bind(pages: List<ReaderPage>, textLength: Int, initialOffset: Int) {
        this.pages = pages
        this.textLength = textLength
        if (!opened) {
            anchor = initialOffset.coerceIn(0, textLength)
            opened = true
        }
    }

    /** True once the page holding the anchor is measured, so a resumed session never flashes past its place. */
    private val reached: Boolean
        get() = pages.lastOrNull()?.let { last -> last.end > anchor || last.end >= textLength } == true

    val index: Int get() = if (reached) pageIndexFor(pages, anchor) else 0
    val total: Int get() = pages.size
    val page: ReaderPage? get() = if (reached) pages.getOrNull(index) else null

    fun previous() = goTo(index - 1)

    fun next() = goTo(index + 1)

    /** Applies a tap on the middle third: hide or show the chrome. */
    fun toggleChrome() {
        chromeVisible = !chromeVisible
        if (!chromeVisible) preferencesVisible = false
    }

    /** Opens or closes the **Aa** panel; it is part of the chrome, so toggling it shows the chrome. */
    fun togglePreferences() {
        chromeVisible = true
        preferencesVisible = !preferencesVisible
    }

    /** Closes the **Aa** panel; a tap outside it, or its own **Close**, lands here. */
    fun dismissPreferences() {
        preferencesVisible = false
    }

    private fun goTo(target: Int) {
        if (pages.isEmpty()) return
        anchor = pages[target.coerceIn(0, pages.lastIndex)].start
        onPositionChange(anchor)
    }
}

/** The page, its word layer built for this page only, and the tap zones over it. */
@Suppress("LongParameterList") // The page's renderer pieces are threaded as one call; a bundle would only hide them.
@Composable
private fun ColumnScope.ReaderPage(
    document: ReaderDocument,
    chapterText: AnnotatedString,
    styles: ReaderStyles,
    state: ReaderState,
    dismissOverlay: (() -> Unit)?,
    onWordTap: (WordTap) -> Unit,
    onPhraseSelected: (PhraseSelection) -> Unit,
) {
    // The range the current long-press drag covers, so the chosen words wash as
    // they are picked; the open panel's range is the fallback once it ends.
    var dragRange by remember { mutableStateOf<IntRange?>(null) }
    val selected = dragRange ?: document.selectedRange
    val tokens = rememberPageTokens(document, chapterText, styles, state.page, selected, onWordTap)
    val latestTokens = rememberUpdatedState(tokens)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val marginPx = with(LocalDensity.current) { styles.horizontalMargin.roundToPx() }
    val latestTap = rememberUpdatedState(
        readerSurfaceTap(marginPx, tokens, layout, state, enabled = dismissOverlay == null),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag(READER_PAGE_TAG)
            .readerTapInput(latestTap)
            .phraseSelectionInput(
                words = { latestTokens.value?.words.orEmpty() },
                layout = { layout },
                marginPx = marginPx,
                chapter = chapterText.text,
                pageStart = state.page?.start ?: 0,
                onSelecting = { range -> dragRange = range },
                onSelected = { phrase ->
                    dragRange = null
                    onPhraseSelected(phrase)
                },
            ),
    ) {
        PageText(tokens, styles, styles.horizontalMargin) { result -> layout = result }
        // Drawn over the whole page, margins included, so an outside tap dismisses
        // the open panel rather than landing in a tap zone or on a word.
        dismissOverlay?.let { dismiss -> DismissScrim(onDismiss = dismiss) }
    }
}

/** The page's text, inset by the reading margin; a slice built for this page, styled and selectable. */
@Composable
private fun PageText(
    tokens: ReaderTokens?,
    styles: ReaderStyles,
    horizontalMargin: Dp,
    onLayout: (TextLayoutResult) -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(horizontal = horizontalMargin)) {
        if (tokens != null) {
            SelectionContainer {
                Text(
                    text = tokens.text,
                    style = styles.body,
                    modifier = Modifier.fillMaxSize(),
                    onTextLayout = onLayout,
                )
            }
        }
    }
}

/** The page's word layer, rebuilt only when the page, the chapter, the style or the mastery behind it changes. */
@Suppress("LongParameterList") // The page's inputs are independent; a bundle would only hide that.
@Composable
private fun rememberPageTokens(
    document: ReaderDocument,
    chapterText: AnnotatedString,
    styles: ReaderStyles,
    page: ReaderPage?,
    selected: IntRange?,
    onWordTap: (WordTap) -> Unit,
): ReaderTokens? {
    val latestTap = rememberUpdatedState(onWordTap)
    return remember(
        chapterText,
        page,
        document.chapter,
        document.renderer,
        styles,
        selected,
        document.masteryRevision,
    ) {
        page?.let { slice ->
            buildPageTokens(chapterText, slice, document.renderer, document.chapter, styles, selected) { word ->
                latestTap.value(word)
            }
        }
    }
}

/** The tap the page handles: a word's link wins, otherwise the tap's zone decides. */
@Suppress("LongParameterList") // The tap's inputs are the page's own geometry; a bundle would only hide them.
private fun readerSurfaceTap(
    marginPx: Int,
    tokens: ReaderTokens?,
    layout: TextLayoutResult?,
    state: ReaderState,
    enabled: Boolean,
): (Offset, Float) -> Unit = { position, width ->
    // While a panel is open above the page (the **Aa** panel or a lookup), a tap on
    // the page only dismisses it — through the page scrim — and must not look a
    // word up or turn the page.
    if (enabled) {
        val zone = readerTapZone(position.x, width.toInt())
        // A tap on a word is left to the word's link, wherever it lands.
        val onWord = wordAt(position, marginPx, tokens?.words.orEmpty(), layout) != null
        when {
            onWord -> Unit
            zone == ReaderTapZone.CHROME -> state.toggleChrome()
            zone == ReaderTapZone.PREVIOUS -> state.previous()
            zone == ReaderTapZone.NEXT -> state.next()
        }
    }
}

/**
 * Pages the chapter incrementally: a fresh list is filled by a coroutine in this
 * composable's scope, so the first page appears before the last is measured. The
 * list's snapshot reads drive recomposition as pages arrive.
 */
@Composable
private fun BoxWithConstraintsScope.rememberReaderPages(
    chapterText: AnnotatedString,
    styles: ReaderStyles,
): List<ReaderPage> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val contentWidth = with(density) {
        (maxWidth - styles.horizontalMargin * 2).roundToPx()
    }.coerceAtLeast(0)
    val contentHeight = with(density) { (maxHeight - ChromeHeight - PageBarHeight).roundToPx() }.coerceAtLeast(0)
    val pages = remember(chapterText, contentWidth, contentHeight, density.density, density.fontScale) {
        mutableStateListOf<ReaderPage>()
    }
    val layout = remember(measurer, styles, contentWidth, contentHeight) {
        ReaderLayout(measurer, styles.body, contentWidth, contentHeight)
    }
    LaunchedEffect(layout) { fillPages(chapterText, layout, pages) }
    return pages
}

/** Appends pages until the chapter ends, yielding every [PAGE_YIELD_EVERY] so the UI stays live. */
private suspend fun fillPages(text: AnnotatedString, layout: ReaderLayout, pages: SnapshotStateList<ReaderPage>) {
    var sinceYield = 0
    for (page in paginateChapter(text, layout)) {
        pages += page
        sinceYield++
        if (sinceYield == PAGE_YIELD_EVERY) {
            sinceYield = 0
            yield()
        }
    }
}

@Composable
private fun ReaderTitle(title: String, onBack: (() -> Unit)?, onPreferences: () -> Unit, horizontalMargin: Dp) {
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier.fillMaxWidth().height(ChromeHeight).padding(horizontal = horizontalMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let { back -> TextButton(onClick = back) { Text(texts[TextKey.LIBRARY]) } }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 0.dp else 8.dp),
        )
        TextButton(onClick = onPreferences, modifier = Modifier.testTag(READING_PREFERENCES_TAG)) {
            Text(texts[TextKey.READING_PREFERENCES])
        }
    }
}

/** The chrome: page position and the two navigation affordances. */
@Suppress("LongParameterList") // The page, its count, the margin and the two turns; a bundle would only hide that.
@Composable
private fun ReaderPageBar(page: Int, total: Int, horizontalMargin: Dp, onPrevious: () -> Unit, onNext: () -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier.fillMaxWidth().height(PageBarHeight).padding(horizontal = horizontalMargin),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, enabled = page > 0) { Text(texts[TextKey.PREVIOUS_PAGE]) }
        Text(texts.message(AppMessage.of(TextKey.PAGE_OF, page + 1, total)))
        TextButton(onClick = onNext, enabled = page < total - 1) { Text(texts[TextKey.NEXT_PAGE]) }
    }
}
