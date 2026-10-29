package app.lekto.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.lekto.core.reading.ReadingPreferences
import app.lekto.navigation.PlatformBackHandler
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.ui.DismissScrim
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

/** How many pages to lay out between yields, so the first page is shown at once. */
private const val PAGE_YIELD_EVERY = 8

/** The breathing room kept between the lifted word and the sheet's top edge. */
private val GAP_ABOVE_PANEL = 8.dp

/** The test tag on the page, so the UI tests can inject a tap at a zone's coordinates. */
internal const val READER_PAGE_TAG = "reader-page"

/** The test tag on the chapter sheet, so a UI test can tell it is open. */
internal const val READER_CHAPTER_SHEET_TAG = "reader-chapter-sheet"

/** The test tag on the chapter sheet's **Close** action. */
internal const val READER_CHAPTER_SHEET_CLOSE_TAG = "reader-chapter-sheet-close"

/** The test tag on the scrim behind the chapter sheet, the "tap outside" target. */
internal const val READER_CHAPTER_SHEET_SCRIM_TAG = "reader-chapter-sheet-scrim"

/**
 * The reader's callbacks, bundled so the screen's signature stays small: a tap on
 * a word, a phrase selected by a long-press drag, a way back to the library, the
 * position a page turn lands on, a **Reading preference** change (ADR-0035) the
 * **Aa** panel raises, the dismiss a tap on the page outside a lookup panel runs,
 * and the two declarations the end-of-book state raises (issue #200).
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
    /** The reader declared the book **Finished** from the end-of-book state (ADR-0038). */
    val onMarkFinished: () -> Unit = {},
    /** The reader undid the declaration; only the fact is removed, never the position (ADR-0038). */
    val onMarkUnfinished: () -> Unit = {},
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
 *
 * [initiallyFinished] is the declaration the session was opened with: it never
 * changes where the book opens, and only seeds the end-of-book state's marked
 * face (ADR-0038).
 *
 * At [CONTEXT_COLUMN_BREAKPOINT] and above the reader reserves the right-hand
 * **context column** ([ReaderContext]): the chapter list when nothing is touched,
 * the open lookup when something is (issue #199). The column is part of the
 * layout, not an overlay, so the page keeps its width while a word is selected and
 * dismissed.
 */
@Suppress("LongParameterList") // The document, its actions, the panel height, the seed fact and the column's content.
@Composable
fun ReaderScreen(
    document: ReaderDocument,
    modifier: Modifier = Modifier,
    actions: ReaderActions = ReaderActions(),
    overlayHeightPx: Int = 0,
    initiallyFinished: Boolean = false,
    context: ReaderContext = ReaderContext(),
) {
    // The reader builds its typography from the preference (ADR-0035), so a change
    // recomposes the text and repaginates at once, the way a Reader level change
    // recolours it.
    val theme = MaterialTheme.colorScheme
    val ink = theme.onBackground
    val styles = remember(document.preferences, theme) {
        ReaderStyles.of(document.preferences, MasteryPalette.of(theme), ink)
    }
    val chapterText = remember(document.text, styles) {
        buildChapterText(document.text, styles)
    }
    val entries = remember(document.text) { document.text.chapterEntries() }
    val chapterStarts = remember(document.text) { document.text.chapterStartOffsets() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val contextColumn = maxWidth >= CONTEXT_COLUMN_BREAKPOINT
        // The page paginates against the width it is actually given: at the
        // breakpoint the column takes its own share of the window.
        val pageWidth = if (contextColumn) maxWidth - CONTEXT_COLUMN_WIDTH else maxWidth
        val state = remember(document.text) {
            ReaderState(actions.onPositionChange, document.initialOffset, initiallyFinished)
        }
        val pages = rememberReaderPages(chapterText, styles, chapterStarts, state.paginationStart, pageWidth)
        state.bind(pages, chapterText.length, document.initialOffset)
        ReaderContent(document, chapterText, entries, styles, state, actions, overlayHeightPx, contextColumn, context)
    }
}

/**
 * The reader's chrome, page and **Aa** panel. The panel is part of the column, not
 * an overlay: opening it lifts the bottom band and shortens the visible page, so
 * it never covers the text (docs/design.md §7.2). The page itself is not
 * repaginated — the panel is a temporary display, so the page number and the page
 * count stay put, and the page is whole again as soon as the panel closes. A tap
 * on the page closes whatever panel is open — the **Aa** panel below it or a
 * lookup above it — and the page scrim keeps that tap from looking a word up or
 * turning the page. The chrome stays usable.
 *
 * At the context column's breakpoint the column is a sibling of the page rather
 * than an overlay ([ReaderContextColumn]): the lookup is beside the text, not over
 * it, so the page needs no scrim for it and never moves (issue #199).
 */
@Suppress("LongParameterList") // The reader's pieces, threaded from the one composition that builds them.
@Composable
private fun ReaderContent(
    document: ReaderDocument,
    chapterText: AnnotatedString,
    entries: List<ReaderChapterEntry>,
    styles: ReaderStyles,
    state: ReaderState,
    actions: ReaderActions,
    overlayHeightPx: Int,
    contextColumn: Boolean,
    context: ReaderContext,
) {
    val current = state.page?.let { page -> entries.entryAt(page.start) }
    // A book with one chapter has no list (ADR-0037), so the column's empty state
    // and the sheet's absence read from the same rule.
    val chapters = if (entries.size > 1) entries else emptyList()
    when {
        // The end of the book is a full-screen state, not a page: it leaves no room
        // for the column (issue #200).
        state.endVisible -> EndOfBookPanel(
            title = document.text.title,
            finished = state.finished,
            actions = actions,
            onShowFinished = state::showFinished,
            onShowUnfinished = state::showUnfinished,
        )

        contextColumn -> WideReader(document, chapterText, chapters, current, styles, state, actions, context)

        else -> PhoneReader(document, chapterText, chapters, current, styles, state, actions, overlayHeightPx, context)
    }
    // Back closes the chapter sheet first, then the **Aa** panel, before the shell
    // sees it (issues #185, #198). The sheet only exists below the breakpoint. The
    // end-of-book state is last, so while it is open Back returns to the last page
    // rather than leaving the book (#200).
    PlatformBackHandler(enabled = state.chaptersVisible && !contextColumn) { state.dismissChapters() }
    PlatformBackHandler(enabled = state.preferencesVisible) { state.dismissPreferences() }
    PlatformBackHandler(enabled = state.endVisible) { state.dismissEnd() }
}

/**
 * The desktop shape (issue #199): the page with its bands, and the reserved
 * context column beside it. The lookup is in the column, so the page is never
 * lifted and never scrimmed for it. A sheet left open when the window grew has to
 * close: both shapes cannot be the way into the list.
 */
@Suppress("LongParameterList") // The reader's pieces, threaded from the one composition that builds them.
@Composable
private fun WideReader(
    document: ReaderDocument,
    chapterText: AnnotatedString,
    chapters: List<ReaderChapterEntry>,
    current: ReaderChapterEntry?,
    styles: ReaderStyles,
    state: ReaderState,
    actions: ReaderActions,
    context: ReaderContext,
) {
    LaunchedEffect(Unit) { state.dismissChapters() }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            ReaderBands(
                document = document,
                chapterText = chapterText,
                chapters = chapters,
                current = current,
                styles = styles,
                state = state,
                actions = actions,
                overlayHeightPx = 0,
                contextColumn = true,
            )
        }
        ReaderContextColumn(
            entries = chapters,
            current = current,
            onChoose = { entry -> state.chooseChapter(entry, current) },
            context = context,
        )
    }
}

/**
 * The phone shape: the page with its bands, the chapter sheet over it, and the
 * lookup raised from the bottom edge. Both are overlays, so the page scrim is the
 * tap that closes them.
 */
@Suppress("LongParameterList") // The reader's pieces, threaded from the one composition that builds them.
@Composable
private fun PhoneReader(
    document: ReaderDocument,
    chapterText: AnnotatedString,
    chapters: List<ReaderChapterEntry>,
    current: ReaderChapterEntry?,
    styles: ReaderStyles,
    state: ReaderState,
    actions: ReaderActions,
    overlayHeightPx: Int,
    context: ReaderContext,
) {
    Box(Modifier.fillMaxSize()) {
        ReaderBands(
            document = document,
            chapterText = chapterText,
            chapters = chapters,
            current = current,
            styles = styles,
            state = state,
            actions = actions,
            overlayHeightPx = overlayHeightPx,
            contextColumn = false,
        )
        if (state.chaptersVisible) {
            ChapterSheetOverlay(
                entries = chapters,
                current = current,
                onChoose = { entry -> state.chooseChapter(entry, current) },
                onDismiss = state::dismissChapters,
            )
        }
        if (context.isLookupOpen) context.panel(Modifier.align(Alignment.BottomCenter), false)
    }
}

/** The page and its two bands, plus the **Aa** panel under them. */
@Suppress("LongParameterList") // The reader's pieces, threaded from the one composition that builds them.
@Composable
private fun ReaderBands(
    document: ReaderDocument,
    chapterText: AnnotatedString,
    chapters: List<ReaderChapterEntry>,
    current: ReaderChapterEntry?,
    styles: ReaderStyles,
    state: ReaderState,
    actions: ReaderActions,
    overlayHeightPx: Int,
    contextColumn: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        if (state.chromeVisible) {
            ReaderTitle(document.text.title, actions.onBack, state::togglePreferences)
        }
        ReaderPage(
            document,
            chapterText,
            styles,
            state,
            overlayDismiss(state, actions, contextColumn),
            overlayHeightPx,
            actions.onWordTap,
            actions.onPhraseSelected,
        )
        if (state.chromeVisible && state.page != null) {
            ReaderPageBar(
                page = state.index,
                total = state.total,
                chapter = current?.chapter?.title?.takeIf { chapters.isNotEmpty() },
                hasChapters = positionOpensChapters(chapters, contextColumn, actions),
                onPrevious = state::previous,
                onNext = state::next,
                onOpenChapters = { showChapters(state, actions, contextColumn) },
            )
        }
        if (state.chromeVisible && state.preferencesVisible) {
            ReadingPreferencesPanel(
                preferences = document.preferences,
                onChange = actions.onReadingPreferencesChange,
                onDismiss = state::dismissPreferences,
            )
        }
    }
}

/**
 * Whether the position is a control: always below the breakpoint, where it raises
 * the chapter sheet; at the breakpoint only while a lookup is open, because the
 * list is already the column's idle state, and the position is then the way back
 * to it without closing the word by hand (issue #199). A lookup is open exactly
 * when the caller offers a dismiss.
 */
private fun positionOpensChapters(
    chapters: List<ReaderChapterEntry>,
    contextColumn: Boolean,
    actions: ReaderActions,
): Boolean = chapters.isNotEmpty() && (!contextColumn || actions.onDismissOverlay != null)

/**
 * Brings the chapter list back: the list and a lookup are never shown together, so
 * this closes one and reveals the other — the sheet below the breakpoint, the
 * column's own list above it, where there is no sheet to raise.
 */
private fun showChapters(state: ReaderState, actions: ReaderActions, contextColumn: Boolean) {
    actions.onDismissOverlay?.invoke()
    if (!contextColumn) state.openChapters()
}

/**
 * The dismiss a tap on the page runs: the **Aa** panel's, or the lookup sheet's,
 * or none. In the context column the lookup sits beside the page rather than over
 * it, so the page has no lookup scrim there and a tap keeps looking words up and
 * turning pages (issue #199).
 */
private fun overlayDismiss(state: ReaderState, actions: ReaderActions, contextColumn: Boolean): (() -> Unit)? = when {
    state.preferencesVisible -> state::dismissPreferences
    contextColumn -> null
    else -> actions.onDismissOverlay
}

/**
 * The reader's page state: where the anchor is, which page that is, and the
 * chrome's visibility. The anchor is a character offset, so a reflow — a
 * **Reading preference** change, a resize — [bind]s a new pagination without
 * losing the reader's place (ADR-0035).
 */
@Stable
@Suppress("TooManyFunctions") // One screen's state: page moves, chrome, and the end state it owns, kept in one anchor.
private class ReaderState(
    private val onPositionChange: (Int) -> Unit,
    initialOffset: Int,
    initiallyFinished: Boolean,
) {
    var chromeVisible by mutableStateOf(true)
        private set

    /** Whether the **Aa** panel is open; it is part of the chrome and hides with it. */
    var preferencesVisible by mutableStateOf(false)
        private set

    /** Whether the chapter sheet is open; it and the **Aa** panel are never both up. */
    var chaptersVisible by mutableStateOf(false)
        private set

    /**
     * Whether the reader is on the full-screen end-of-book state (issue #200;
     * ADR-0038). It opens only on a next tap at the last page, never on reaching
     * it, and it hides the chrome.
     */
    var endVisible by mutableStateOf(false)
        private set

    /** Whether the reader has declared the book **Finished**; the end panel shows its marked face from it. */
    var finished by mutableStateOf(initiallyFinished)
        private set

    /**
     * Where pagination starts: the saved position when the book opens, then the
     * chapter a jump chose. Changing it restarts the pages with the segment the
     * reader is looking at, so a jump shows its chapter without waiting for the
     * pages before it (ADR-0037).
     */
    var paginationStart by mutableStateOf(initialOffset)
        private set

    private var pages: List<ReaderPage> = emptyList()
    private var textLength: Int = 0
    private var anchor by mutableStateOf(initialOffset)
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

    /**
     * Advances one page, or — on the last page — raises the end-of-book state
     * (issue #200). Reaching the last page by any other route never opens it:
     * only this explicit next does, and it never declares the book finished by
     * itself.
     */
    fun next() {
        if (reached && index >= pages.lastIndex) {
            openEnd()
        } else {
            goTo(index + 1)
        }
    }

    /** Raises the full-screen end state, which hides the reader's chrome. */
    private fun openEnd() {
        chromeVisible = false
        preferencesVisible = false
        chaptersVisible = false
        endVisible = true
    }

    /** Closes the end state and returns to the last page, the way system back does. */
    fun dismissEnd() {
        endVisible = false
        chromeVisible = true
    }

    /** Shows the end state's marked face; the record write is the caller's action (ADR-0038). */
    fun showFinished() {
        finished = true
    }

    /** Shows the end state's unmarked face; the record removal is the caller's action (ADR-0038). */
    fun showUnfinished() {
        finished = false
    }

    /** Applies a tap on the middle third: hide or show the chrome. */
    fun toggleChrome() {
        chromeVisible = !chromeVisible
        if (!chromeVisible) {
            preferencesVisible = false
            chaptersVisible = false
        }
    }

    /** Opens or closes the **Aa** panel; it is part of the chrome, so toggling it shows the chrome. */
    fun togglePreferences() {
        chromeVisible = true
        chaptersVisible = false
        preferencesVisible = !preferencesVisible
    }

    /** Closes the **Aa** panel; a tap outside it, or its own **Close**, lands here. */
    fun dismissPreferences() {
        preferencesVisible = false
    }

    /** Opens the chapter sheet, closing the **Aa** panel so the two never overlap. */
    fun openChapters() {
        chromeVisible = true
        preferencesVisible = false
        chaptersVisible = true
    }

    /** Closes the chapter sheet; a choice, a tap outside, **Close** or Back lands here. */
    fun dismissChapters() {
        chaptersVisible = false
    }

    /**
     * Moves to [offset] — a chapter's first block — and saves it at once, so
     * **Last read** moves and the target is shown without waiting for the whole
     * book to be counted (ADR-0037).
     */
    fun jumpTo(offset: Int) {
        chaptersVisible = false
        preferencesVisible = false
        anchor = offset.coerceIn(0, textLength)
        paginationStart = anchor
        onPositionChange(anchor)
    }

    /**
     * Applies a chapter choice: it closes the list and jumps, and a jump to the
     * chapter already shown writes nothing (ADR-0037). The sheet and the context
     * column share this rule, so a choice cannot behave differently by shape.
     */
    fun chooseChapter(entry: ReaderChapterEntry, current: ReaderChapterEntry?) {
        if (current?.startOffset == entry.startOffset) {
            dismissChapters()
        } else {
            jumpTo(entry.startOffset)
        }
    }

    private fun goTo(target: Int) {
        if (pages.isEmpty()) return
        anchor = pages[target.coerceIn(0, pages.lastIndex)].start
        onPositionChange(anchor)
    }
}

/** The page, its word layer built for this page only, and the tap zones over it. */
@Suppress("LongMethod", "LongParameterList") // The page's pieces share one layout; splitting would hide its state.
@Composable
private fun ColumnScope.ReaderPage(
    document: ReaderDocument,
    chapterText: AnnotatedString,
    styles: ReaderStyles,
    state: ReaderState,
    dismissOverlay: (() -> Unit)?,
    overlayHeightPx: Int,
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
    var pageHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val marginPx = with(density) { styles.horizontalMargin.roundToPx() }
    val pageBarPx = with(density) { if (state.chromeVisible) PAGE_BAR_HEIGHT.toPx() else 0f }
    val visibleBottomPx = pageHeightPx - (overlayHeightPx - pageBarPx).coerceAtLeast(0f)
    val liftPx = pageLift(
        layout = layout,
        page = state.page,
        selected = document.selectedRange,
        visibleBottomPx = visibleBottomPx,
        gapPx = with(density) { GAP_ABOVE_PANEL.toPx() },
    )
    val latestTap = rememberUpdatedState(
        readerSurfaceTap(marginPx, tokens, layout, state, enabled = dismissOverlay == null, liftPx = liftPx),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            // The page keeps its full-height pagination; the **Aa** panel only clips it.
            .clipToBounds()
            .onSizeChanged { size -> pageHeightPx = size.height }
            .testTag(READER_PAGE_TAG)
            .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
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
        PageText(
            tokens = tokens,
            styles = styles,
            horizontalMargin = styles.horizontalMargin,
            liftPx = liftPx,
            onPrevious = state::previous,
            onNext = state::next,
        ) { result -> layout = result }
        // Drawn over the whole page, margins included, so an outside tap dismisses
        // the open panel rather than landing in a tap zone or on a word.
        dismissOverlay?.let { dismiss -> DismissScrim(onDismiss = dismiss) }
    }
}

/**
 * How far the page must move up for the selected word to clear an overlay of
 * [overlayHeightPx] (the lookup sheet). The lift is only as large as the word
 * demands, so a word already above the sheet does not move, and a word under it
 * rises just above it; the page is never repaginated, so the top of the page is
 * clipped for as long as the sheet is open. The panel overlaps the page only
 * above the page bar, which is why the visible bottom subtracts that bar.
 */
@Suppress("LongParameterList") // The layout, the page and the overlay are independent inputs.
private fun pageLift(
    layout: TextLayoutResult?,
    page: ReaderPage?,
    selected: IntRange?,
    visibleBottomPx: Float,
    gapPx: Float,
): Float {
    if (page == null || layout == null || visibleBottomPx <= 0f) return 0f
    val wordBottom = selected
        ?.first?.minus(page.start)
        ?.takeIf { local -> local in 0 until page.end - page.start }
        ?.let { local -> layout.getBoundingBox(local).bottom }
    return if (wordBottom == null) 0f else (wordBottom + gapPx - visibleBottomPx).coerceAtLeast(0f)
}

/**
 * The page's text, inset by the reading margin; a slice built for this page,
 * styled and selectable. The text also carries the two page turns as
 * accessibility actions beside the two chevron controls, so a screen-reader or
 * switch user has a way through the book without hunting for the bottom band.
 */
@Suppress("LongParameterList") // The page's inputs are independent; a bundle would only hide that.
@Composable
private fun PageText(
    tokens: ReaderTokens?,
    styles: ReaderStyles,
    horizontalMargin: Dp,
    liftPx: Float,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLayout: (TextLayoutResult) -> Unit,
) {
    val texts = LocalAppTexts.current
    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, -liftPx.roundToInt()) }
            .padding(horizontal = horizontalMargin),
    ) {
        if (tokens != null) {
            SelectionContainer {
                Text(
                    text = tokens.text,
                    style = styles.body,
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(texts[TextKey.PREVIOUS_PAGE]) {
                                    onPrevious()
                                    true
                                },
                                CustomAccessibilityAction(texts[TextKey.NEXT_PAGE]) {
                                    onNext()
                                    true
                                },
                            )
                        },
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
        document.text,
        document.renderer,
        styles,
        selected,
        document.masteryRevision,
    ) {
        page?.let { slice ->
            buildPageTokens(chapterText, slice, document.renderer, document.text, styles, selected) { word ->
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
    liftPx: Float,
): (Offset, Float) -> Unit = { position, width ->
    // While a panel is open above the page (the **Aa** panel or a lookup), a tap on
    // the page only dismisses it — through the page scrim — and must not look a
    // word up or turn the page.
    if (enabled) {
        val zone = readerTapZone(position.x, width.toInt())
        // A tap on a word is left to the word's link, wherever it lands. The text
        // is lifted with the page, so its layout coordinates are that much lower.
        val onWord = wordAt(Offset(position.x, position.y + liftPx), marginPx, tokens?.words.orEmpty(), layout) != null
        when {
            onWord -> Unit
            zone == ReaderTapZone.CHROME -> state.toggleChrome()
            zone == ReaderTapZone.PREVIOUS -> state.previous()
            zone == ReaderTapZone.NEXT -> state.next()
        }
    }
}

/**
 * Pages the whole book incrementally: a fresh list is filled by a coroutine in
 * this composable's scope, so the first page appears before the last is
 * measured. Pages never cross a chapter start (ADR-0037), and the segment
 * holding [focus] is measured first, so a jump shows its chapter at once while
 * the rest of the book is counted behind it. The list's snapshot reads drive
 * recomposition as pages arrive.
 *
 * [pageWidth] is the room the page actually gets: at the context column's
 * breakpoint the reserved column takes its share of the window, so measuring the
 * whole width would wrap every line too late (issue #199).
 */
@Suppress("LongParameterList") // The text, its style, its chapter starts, its focus and the page's width.
@Composable
private fun BoxWithConstraintsScope.rememberReaderPages(
    chapterText: AnnotatedString,
    styles: ReaderStyles,
    chapterStarts: Set<Int>,
    paginationStart: Int,
    pageWidth: Dp,
): List<ReaderPage> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val contentWidth = with(density) {
        (pageWidth - styles.horizontalMargin * 2).roundToPx()
    }.coerceAtLeast(0)
    val contentHeight = with(density) { (maxHeight - CHROME_HEIGHT - PAGE_BAR_HEIGHT).roundToPx() }.coerceAtLeast(0)
    val pages =
        remember(chapterText, contentWidth, contentHeight, density.density, density.fontScale, paginationStart) {
            mutableStateListOf<ReaderPage>()
        }
    val layout = remember(measurer, styles, contentWidth, contentHeight) {
        ReaderLayout(measurer, styles.body, contentWidth, contentHeight)
    }
    LaunchedEffect(layout, chapterStarts, paginationStart) {
        fillPages(chapterText, layout, chapterStarts, paginationStart, pages)
    }
    return pages
}

/**
 * Appends pages until the book ends, yielding every [PAGE_YIELD_EVERY] so the
 * UI stays live. The segments are the chapter starts: the one holding [focus]
 * is measured first, then the later ones, then the earlier ones, and the list is
 * kept in reading order throughout.
 */
@Suppress("LongParameterList") // The paginator's inputs are the text, its layout and its state list.
private suspend fun fillPages(
    text: AnnotatedString,
    layout: ReaderLayout,
    chapterStarts: Set<Int>,
    paginationStart: Int,
    pages: SnapshotStateList<ReaderPage>,
) {
    val boundaries = (chapterStarts + 0 + text.length)
        .filter { offset -> offset in 0..text.length }
        .distinct()
        .sorted()
    val segments = boundaries.zipWithNext().filter { (start, end) -> end > start }
    if (segments.isEmpty()) {
        paginateChapter(text, layout).forEach { page -> pages += page }
        return
    }
    val focusIndex = segments.indexOfLast { (start, _) -> start <= paginationStart }.coerceAtLeast(0)
    val ordered = segments.drop(focusIndex) + segments.take(focusIndex)
    var sinceYield = 0
    for ((start, end) in ordered) {
        for (page in paginateChapter(text.subSequence(start, end), layout)) {
            pages += ReaderPage(page.start + start, page.end + start)
            sinceYield++
            if (sinceYield == PAGE_YIELD_EVERY) {
                sinceYield = 0
                yield()
            }
        }
        pages.sortWith(compareBy { page -> page.start })
    }
}
