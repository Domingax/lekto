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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.reading.ReadingPreferences
import app.lekto.navigation.PlatformBackHandler
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.DismissScrim
import app.lekto.ui.handCursor
import app.lekto.ui.icons.ALargeSmall
import app.lekto.ui.icons.ArrowLeft
import app.lekto.ui.icons.ChevronLeft
import app.lekto.ui.icons.ChevronRight
import app.lekto.ui.icons.LektoIcons
import kotlinx.coroutines.yield

/** The top band's height: the icon, the title and the **Aa** control (docs/design.md §6, §7.2). */
internal val CHROME_HEIGHT = 48.dp

/** The bottom band's height: the two chevron controls and the position (docs/design.md §6, §7.2). */
internal val PAGE_BAR_HEIGHT = 64.dp

/** How many pages to lay out between yields, so the first page is shown at once. */
private const val PAGE_YIELD_EVERY = 8

/** The test tag on the page, so the UI tests can inject a tap at a zone's coordinates. */
internal const val READER_PAGE_TAG = "reader-page"

/** The test tag on the reader's way back to the shelf, an icon in its left band (docs/design.md §7.2). */
internal const val READER_BACK_TAG = "reader-back"

/** The test tag on the book's title in the top band. */
internal const val READER_TITLE_TAG = "reader-title"

/** The test tag on the position line in the bottom band. */
internal const val READER_POSITION_TAG = "reader-position"

/** The test tag on the left chevron, which marks the previous third and turns the page. */
internal const val READER_PREVIOUS_PAGE_TAG = "reader-previous-page"

/** The test tag on the right chevron, which marks the next third and turns the page. */
internal const val READER_NEXT_PAGE_TAG = "reader-next-page"

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
    val theme = MaterialTheme.colorScheme
    val ink = theme.onBackground
    val styles = remember(document.preferences, theme) {
        ReaderStyles.of(document.preferences, MasteryPalette.of(theme), ink)
    }
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
 * The reader's chrome, page and **Aa** panel. The panel is part of the column, not
 * an overlay: opening it lifts the bottom band and shortens the visible page, so
 * it never covers the text (docs/design.md §7.2). The page itself is not
 * repaginated — the panel is a temporary display, so the page number and the page
 * count stay put, and the page is whole again as soon as the panel closes. A tap
 * on the page closes whatever panel is open — the **Aa** panel below it or a
 * lookup above it — and the page scrim keeps that tap from looking a word up or
 * turning the page. The chrome stays usable.
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
            ReaderTitle(document.chapter.title, actions.onBack, state::togglePreferences)
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
            ReaderPageBar(state.index, state.total, state::previous, state::next)
        }
        if (state.chromeVisible && state.preferencesVisible) {
            ReadingPreferencesPanel(
                preferences = document.preferences,
                onChange = actions.onReadingPreferencesChange,
                onDismiss = state::dismissPreferences,
            )
        }
    }
    // Back closes the **Aa** panel before the shell sees it (issue #185), the way a
    // tap outside the panel does.
    PlatformBackHandler(enabled = state.preferencesVisible) { state.dismissPreferences() }
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
@Suppress("LongMethod", "LongParameterList") // The page's pieces share one layout; splitting would hide its state.
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
            // The page keeps its full-height pagination; the **Aa** panel only clips it.
            .clipToBounds()
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
            onPrevious = state::previous,
            onNext = state::next,
        ) { result -> layout = result }
        // Drawn over the whole page, margins included, so an outside tap dismisses
        // the open panel rather than landing in a tap zone or on a word.
        dismissOverlay?.let { dismiss -> DismissScrim(onDismiss = dismiss) }
    }
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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLayout: (TextLayoutResult) -> Unit,
) {
    val texts = LocalAppTexts.current
    Box(Modifier.fillMaxSize().padding(horizontal = horizontalMargin)) {
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
    val contentHeight = with(density) { (maxHeight - CHROME_HEIGHT - PAGE_BAR_HEIGHT).roundToPx() }.coerceAtLeast(0)
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

/**
 * The reader's top band (docs/design.md §6, §7.2): the way back, the book's title
 * in the theme's muted ink, and the **Aa** control. Nothing else.
 */
@Composable
private fun ReaderTitle(title: String, onBack: (() -> Unit)?, onPreferences: () -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier.fillMaxWidth().height(CHROME_HEIGHT).padding(horizontal = LektoSpacing.ScreenEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `arrow-left`, not a word: the reader's band is an icon, a title and the
        // **Aa** control (docs/design.md §7.2), and the icon mirrors in a
        // right-to-left layout. Its description names it, since no label does.
        onBack?.let { back ->
            IconButton(
                onClick = back,
                modifier = Modifier.handCursor().testTag(READER_BACK_TAG),
            ) {
                Icon(imageVector = LektoIcons.ArrowLeft, contentDescription = texts[TextKey.BACK])
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = LektoSpacing.BetweenRelatedLines)
                .testTag(READER_TITLE_TAG),
        )
        // The `Aa` icon, not its two letters: the identity's set carries it, and
        // its description names the control for a screen reader.
        IconButton(
            onClick = onPreferences,
            modifier = Modifier.handCursor().testTag(READING_PREFERENCES_TAG),
        ) {
            Icon(imageVector = LektoIcons.ALargeSmall, contentDescription = texts[TextKey.READING_PREFERENCES])
        }
    }
}

/**
 * The bottom band (docs/design.md §6, §7.2): two chevrons that mark where the
 * page's tap zones are **and** turn the page when tapped, and the position in the
 * theme's muted ink. A chevron disabled at a bound stays visible but faded, so the
 * band keeps its shape as the reader moves.
 */
@Composable
private fun ReaderPageBar(page: Int, total: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier.fillMaxWidth().height(PAGE_BAR_HEIGHT).padding(horizontal = LektoSpacing.ScreenEdge),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapZoneButton(
            imageVector = LektoIcons.ChevronLeft,
            contentDescription = texts[TextKey.PREVIOUS_PAGE],
            enabled = page > 0,
            onClick = onPrevious,
            tag = READER_PREVIOUS_PAGE_TAG,
        )
        Text(
            text = texts.message(AppMessage.of(TextKey.PAGE_OF, page + 1, total)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(READER_POSITION_TAG),
        )
        TapZoneButton(
            imageVector = LektoIcons.ChevronRight,
            contentDescription = texts[TextKey.NEXT_PAGE],
            enabled = page < total - 1,
            onClick = onNext,
            tag = READER_NEXT_PAGE_TAG,
        )
    }
}

/**
 * One chevron marking a tap zone and turning the page: muted ink, and faded while
 * it is disabled at a bound. Its description names the turn, so a screen reader
 * meets a control rather than a decorative glyph.
 */
@Suppress("LongParameterList") // The icon, its spoken name, its state and its tap are independent.
@Composable
private fun TapZoneButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.handCursor(enabled).testTag(tag),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
        )
    }
}

/** The opacity of a disabled control, matching Material's own content alphas. */
private const val DISABLED_ALPHA = 0.38f
