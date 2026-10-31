package app.lekto.reader

import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import app.lekto.core.book.ReadingProgress
import app.lekto.core.reading.ReadingPreferences
import app.lekto.navigation.PlatformBackHandler
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.WIDE_COLUMN_WIDTH
import app.lekto.theme.WIDE_WINDOW_BREAKPOINT
import app.lekto.ui.DismissScrim
import kotlin.math.roundToInt

/** The breathing room kept between the lifted word and the sheet's top edge. */
private val GAP_ABOVE_PANEL = 8.dp

/** The test tag on the page, so the UI tests can inject a tap at a zone's coordinates. */
internal const val READER_PAGE_TAG = "reader-page"

/** The test tag on the page's text itself, so a test can read the words it shows. */
internal const val READER_PAGE_TEXT_TAG = "reader-page-text"

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
 * saved [ReaderDocument.initialOffset], and chrome that never leaves the page
 * (issue #16, issue #243).
 *
 * A page turns two ways: the two chevrons in the bottom band, and — on a touch
 * screen — a horizontal swipe, leftward for the next page and rightward for the
 * previous. The page itself has no tap zones: a tap on a word is word lookup and
 * a tap anywhere else does nothing, so reaching for the text never turns a page
 * by accident. A long press on a word starts a phrase selection ([PhraseSelection]),
 * which is the way to a translation of more than one word.
 *
 * The page is a plain (non-lazy) [SelectionContainer] so every visible word is
 * composed and selectable; pagination and tokenisation are incremental, so a long
 * book shows its first page without processing the rest.
 *
 * [ReaderActions.onPositionChange] is called with the character offset of each
 * page the reader turns to, so the caller can persist the reading position.
 *
 * [initiallyFinished] is the declaration the session was opened with: it never
 * changes where the book opens, and only seeds the end-of-book state's marked
 * face (ADR-0038).
 *
 * At [WIDE_WINDOW_BREAKPOINT] and above the reader reserves the right-hand
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
        val contextColumn = maxWidth >= WIDE_WINDOW_BREAKPOINT
        // The page paginates against the width it is actually given: at the
        // breakpoint the column takes its own share of the window.
        val pageWidth = if (contextColumn) maxWidth - WIDE_COLUMN_WIDTH else maxWidth
        val state = remember(document.text) {
            ReaderState(actions.onPositionChange, document.initialOffset, initiallyFinished)
        }
        val pages = rememberReaderPages(chapterText, styles, chapterStarts, state.anchor, pageWidth)
        state.bind(pages, chapterText.length, document.initialOffset)
        ReaderContent(document, chapterText, entries, styles, state, actions, overlayHeightPx, contextColumn, context)
    }
}

/**
 * The reader's chrome, page and **Aa** panel. The panel is part of the column, not
 * an overlay: opening it lifts the bottom band and shortens the visible page, so
 * it never covers the text (docs/design.md §7.2). The page itself is not
 * repaginated — the panel is a temporary display, so the page and the **Reading
 * progress** stay put, and the page is whole again as soon as the panel closes. A tap
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
    // The arrow keys turn the page (issue #246). The reader takes focus when it
    // opens and takes it back when a panel closes, and a preview handler keeps the
    // keys working after a word link or a band control takes focus.
    val keys = remember { FocusRequester() }
    val panelOpen = state.preferencesVisible || state.chaptersVisible || state.endVisible
    LaunchedEffect(panelOpen) { if (!panelOpen) keys.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(keys)
            .focusable()
            .onPreviewKeyEvent { event -> readerKey(event, state) },
    ) {
        when {
            // The end of the book is a full-screen state, not a page: it leaves no
            // room for the column (issue #200).
            state.endVisible -> EndOfBookPanel(
                title = document.text.title,
                finished = state.finished,
                actions = actions,
                onShowFinished = state::showFinished,
                onShowUnfinished = state::showUnfinished,
            )

            contextColumn -> WideReader(document, chapterText, chapters, current, styles, state, actions, context)

            else -> PhoneReader(
                document,
                chapterText,
                chapters,
                current,
                styles,
                state,
                actions,
                overlayHeightPx,
                context,
            )
        }
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
 * Whether the key is the reader's. The two arrows turn the page, through the same
 * calls the chevrons make; while a panel or the end state is open the keys are
 * theirs, so an arrow moves a chip or a button instead of the book (issue #246).
 */
@Suppress("ReturnCount") // The guards are the point: a key that is not the reader's must pass through.
private fun readerKey(event: KeyEvent, state: ReaderState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (state.preferencesVisible || state.chaptersVisible || state.endVisible) return false
    return when (event.key) {
        Key.DirectionLeft -> {
            state.previous()
            true
        }

        Key.DirectionRight -> {
            state.next()
            true
        }

        else -> false
    }
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
        ReaderTitle(document.text.title, actions.onBack, state::togglePreferences)
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
        if (state.page != null) {
            ReaderPageBar(
                progress = state.progress,
                chapter = current?.chapter?.title?.takeIf { chapters.isNotEmpty() },
                hasChapters = positionOpensChapters(chapters, contextColumn, actions),
                canPrevious = state.canPrevious,
                canNext = state.canNext,
                onPrevious = state::previous,
                onNext = state::next,
                onOpenChapters = { showChapters(state, actions, contextColumn) },
            )
        }
        if (state.preferencesVisible) {
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
internal class ReaderState(
    private val onPositionChange: (Int) -> Unit,
    initialOffset: Int,
    initiallyFinished: Boolean,
) {
    /** Whether the **Aa** panel is open; it is part of the chrome. */
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
     * Where the reader is: a character offset into the rendered text, chosen so
     * the place survives a reflow onto another screen or font (ADR-0035). It is
     * the only position the reader keeps; the page under it is derived from the
     * measured window (ADR-0040, issue #241).
     */
    var anchor by mutableStateOf(initialOffset)
        private set

    private var pages: List<ReaderPage> = emptyList()
    private var textLength: Int = 0
    private var opened = false

    /**
     * The page last drawn. A chapter jump or a step across a boundary starts a
     * new window, and the pager measures it from its start; until that reaches
     * the anchor the reader keeps this page on screen rather than showing a
     * blank page and no bar (ADR-0040).
     */
    private var shown: ReaderPage? = null

    /**
     * Points the state at the latest window. The first [bind] — opening the book
     * — takes the saved [initialOffset]; a later one only re-reads the pages, so
     * a preference change repaginates around the same anchor.
     */
    fun bind(pages: List<ReaderPage>, textLength: Int, initialOffset: Int) {
        this.pages = pages
        this.textLength = textLength
        if (!opened) {
            anchor = initialOffset.coerceIn(0, textLength)
            opened = true
        }
        if (reached) pages.getOrNull(index)?.let { drawn -> shown = drawn }
    }

    /** True once the page holding the anchor is measured, so a resumed session never flashes past its place. */
    private val reached: Boolean
        get() = pages.lastOrNull()?.let { last -> last.end > anchor || last.end >= textLength } == true

    private val index: Int get() = if (reached) pageIndexFor(pages, anchor) else 0

    /** The page under the anchor, or the last one drawn while its replacement is measured. */
    val page: ReaderPage? get() = if (reached) pages.getOrNull(index) else shown

    /** How far through the book the reader is, the same share the Library row shows. */
    val progress: Int get() = ReadingProgress(anchor, textLength).percent

    /** Whether there is a page before this one, in this chapter or the one before it. */
    val canPrevious: Boolean get() = (page?.start ?: 0) > 0

    /**
     * Whether the next action does anything. A page after this one turns; the
     * last page raises the end-of-book state, so the control is only dead before
     * a page has been measured.
     */
    val canNext: Boolean get() = page != null

    /**
     * Moves to the page before. Inside a chapter it is already measured, so the
     * anchor lands on its first character; across a boundary the window holds
     * nothing before the chapter start, so the anchor steps just behind it and
     * the pager measures the chapter before (ADR-0040).
     */
    fun previous() {
        val current = page ?: return
        val before = pages.getOrNull(index - 1)
        when {
            before != null && before.end == current.start -> moveTo(before.start)
            current.start > 0 -> moveTo(current.start - 1)
        }
    }

    /**
     * Advances one page, or — on the last page — raises the end-of-book state
     * (issue #200). Reaching the last page by any other route never opens it:
     * only this explicit next does, and it never declares the book finished by
     * itself.
     */
    fun next() {
        val current = page ?: return
        if (current.end >= textLength) openEnd() else moveTo(current.end)
    }

    /** Raises the full-screen end state. */
    private fun openEnd() {
        preferencesVisible = false
        chaptersVisible = false
        endVisible = true
    }

    /** Closes the end state and returns to the last page, the way system back does. */
    fun dismissEnd() {
        endVisible = false
    }

    /** Shows the end state's marked face; the record write is the caller's action (ADR-0038). */
    fun showFinished() {
        finished = true
    }

    /** Shows the end state's unmarked face; the record removal is the caller's action (ADR-0038). */
    fun showUnfinished() {
        finished = false
    }

    /** Opens or closes the **Aa** panel. */
    fun togglePreferences() {
        chaptersVisible = false
        preferencesVisible = !preferencesVisible
    }

    /** Closes the **Aa** panel; a tap outside it, or its own **Close**, lands here. */
    fun dismissPreferences() {
        preferencesVisible = false
    }

    /** Opens the chapter sheet, closing the **Aa** panel so the two never overlap. */
    fun openChapters() {
        preferencesVisible = false
        chaptersVisible = true
    }

    /** Closes the chapter sheet; a choice, a tap outside, **Close** or Back lands here. */
    fun dismissChapters() {
        chaptersVisible = false
    }

    /**
     * Moves to [offset] — a chapter's first block — and saves it at once, so
     * **Last read** moves and the target is shown without waiting for the book to
     * be counted (ADR-0037, ADR-0040).
     */
    fun jumpTo(offset: Int) {
        chaptersVisible = false
        preferencesVisible = false
        moveTo(offset)
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

    /** Puts the anchor at [offset] and records it as the book's **Reading position**. */
    private fun moveTo(offset: Int) {
        anchor = offset.coerceIn(0, textLength)
        onPositionChange(anchor)
    }
}

/** The page, its word layer built for this page only, and the swipe over it. */
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
    // they are picked; the open panel's range is the fallback once it ends. The
    // drag is page-local; the committed range is not, so it is moved onto the page
    // before the wash compares them (issue #244).
    var dragRange by remember { mutableStateOf<IntRange?>(null) }
    val pageStart = state.page?.start ?: 0
    val selected = washedRange(dragRange, document.selectedRange, pageStart)
    val tokens = rememberPageTokens(document, chapterText, styles, state.page, selected)
    val latestTokens = rememberUpdatedState(tokens)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var pageHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val marginPx = with(density) { styles.horizontalMargin.roundToPx() }
    val pageBarPx = with(density) { PAGE_BAR_HEIGHT.toPx() }
    val visibleBottomPx = pageHeightPx - (overlayHeightPx - pageBarPx).coerceAtLeast(0f)
    val liftPx = pageLift(
        layout = layout,
        page = state.page,
        selected = document.selectedRange,
        visibleBottomPx = visibleBottomPx,
        gapPx = with(density) { GAP_ABOVE_PANEL.toPx() },
    )
    val latestTap = rememberUpdatedState(
        readerSurfaceTap(
            page = PageTap(marginPx, tokens, layout, state.page?.start ?: 0, chapterText.text, onWordTap),
            enabled = dismissOverlay == null,
            liftPx = liftPx,
        ),
    )
    val latestSwipe = rememberUpdatedState<(PageSwipe) -> Unit> { swipe ->
        when (swipe) {
            PageSwipe.PREVIOUS -> state.previous()
            PageSwipe.NEXT -> state.next()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            // The page keeps its full-height pagination; the **Aa** panel only clips it.
            .clipToBounds()
            .onSizeChanged { size -> pageHeightPx = size.height }
            .testTag(READER_PAGE_TAG)
            // A tap that is not on a word does nothing: page turns are the
            // swipe and the two chevrons, so reaching for the text is safe.
            .readerSwipeInput(enabled = dismissOverlay == null, onSwipe = latestSwipe)
            .readerTapInput(latestTap)
            .phraseSelectionInput(
                words = { latestTokens.value?.words.orEmpty() },
                layout = { layout },
                marginPx = marginPx,
                chapter = chapterText.text,
                pageStart = pageStart,
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
        // the open panel rather than looking a word up.
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
internal fun pageLift(
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
                        .testTag(READER_PAGE_TEXT_TAG)
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
): ReaderTokens? = remember(
    chapterText,
    page,
    document.text,
    document.renderer,
    styles,
    selected,
    document.masteryRevision,
) {
    page?.let { slice ->
        buildPageTokens(chapterText, slice, document.renderer, document.text, styles, selected)
    }
}

/**
 * The tap the page handles: a word opens its lookup wherever it lands, and a tap
 * that misses every word does nothing — page turns are the swipe and the two
 * chevrons (issue #243). The word is found by hit-testing the page's tokens
 * against its layout; the words carry no link of their own, because one annotation
 * per word costs ~55 ms of text layout on Android and the reader pays it on every
 * page turn (ADR-0007, issue #251).
 */
private fun readerSurfaceTap(page: PageTap, enabled: Boolean, liftPx: Float): (Offset) -> Unit = { position ->
    // While a panel is open above the page (the **Aa** panel or a lookup), a tap on
    // the page only dismisses it — through the page scrim — and must not look a
    // word up.
    if (enabled) {
        // The text is lifted with the page, so its layout coordinates are that much lower.
        val local = Offset(position.x, position.y + liftPx)
        wordAt(local, page.marginPx, page.tokens?.words.orEmpty(), page.layout)?.let { word ->
            page.onWordTap(wordTapOf(word, page.pageStart, page.chapterText))
        }
    }
}

/**
 * The page window around [anchor]: a list filled by a coroutine in this
 * composable's scope, starting at the anchor's chapter and stopping a few pages
 * past it, so a jump or an open costs one chapter rather than the book
 * (ADR-0040). The list's snapshot reads drive recomposition as pages arrive.
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
    anchor: Int,
    pageWidth: Dp,
): List<ReaderPage> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val contentWidth = with(density) {
        (pageWidth - styles.horizontalMargin * 2).roundToPx()
    }.coerceAtLeast(0)
    val contentHeight = with(density) { (maxHeight - CHROME_HEIGHT - PAGE_BAR_HEIGHT).roundToPx() }.coerceAtLeast(0)
    // The window belongs to a chapter: a page turn inside it keeps what is
    // already measured, while crossing a boundary starts the next one's window
    // (ADR-0037, ADR-0040).
    val windowStart = remember(anchor, chapterStarts) { chapterStartAt(anchor, chapterStarts) }
    val layout = remember(measurer, styles, contentWidth, contentHeight) {
        ReaderLayout(measurer, styles.body, contentWidth, contentHeight)
    }
    // A new layout is a new pagination: the font, the margin and the viewport all
    // change where a page breaks, so the window starts over with the new one.
    val pages = remember(chapterText, layout, windowStart) {
        mutableStateListOf<ReaderPage>()
    }
    LaunchedEffect(layout, chapterStarts, anchor, pages) {
        fillWindow(chapterText, layout, chapterStarts, anchor, pages) { withFrameNanos { } }
    }
    return pages
}
