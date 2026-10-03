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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.lekto.core.text.WordToken
import kotlinx.coroutines.yield

private val HorizontalMargin = 24.dp
private val ChromeHeight = 48.dp
private val PageBarHeight = 64.dp

/** How many pages to lay out between yields, so the first page is shown at once. */
private const val PAGE_YIELD_EVERY = 8

/** The test tag on the page, so the UI tests can inject a tap at a zone's coordinates. */
internal const val READER_PAGE_TAG = "reader-page"

/**
 * The reader's callbacks, bundled so the screen's signature stays small: a tap on
 * a word, a way back to the library, and the position a page turn lands on.
 */
data class ReaderActions(
    val onWordTap: (WordToken) -> Unit = {},
    val onBack: (() -> Unit)? = null,
    val onPositionChange: (Int) -> Unit = {},
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
    val chapterText = remember(document.chapter, document.renderer.styles) {
        buildChapterText(document.chapter, document.renderer.styles)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val pages = rememberReaderPages(chapterText, document.renderer.styles.body)
        val state = remember(pages, chapterText, document.initialOffset, actions.onPositionChange) {
            ReaderState(pages, chapterText.length, document.initialOffset, actions.onPositionChange)
        }
        Column(Modifier.fillMaxSize()) {
            if (state.chromeVisible) ReaderTitle(document.chapter.title, actions.onBack)
            ReaderPage(document, chapterText, state, actions.onWordTap)
            if (state.chromeVisible && state.page != null) {
                ReaderPageBar(state.index, state.total, state::previous, state::next)
            }
        }
    }
}

/** The reader's page state: where the anchor is, which page that is, and the chrome's visibility. */
@Stable
private class ReaderState(
    private val pages: List<ReaderPage>,
    private val textLength: Int,
    initialOffset: Int,
    private val onPositionChange: (Int) -> Unit,
) {
    var chromeVisible by mutableStateOf(true)
        private set
    private var anchor by mutableStateOf(initialOffset.coerceIn(0, textLength))

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
    }

    private fun goTo(target: Int) {
        if (pages.isEmpty()) return
        anchor = pages[target.coerceIn(0, pages.lastIndex)].start
        onPositionChange(anchor)
    }
}

/** The page, its word layer built for this page only, and the tap zones over it. */
@Composable
private fun ColumnScope.ReaderPage(
    document: ReaderDocument,
    chapterText: AnnotatedString,
    state: ReaderState,
    onWordTap: (WordToken) -> Unit,
) {
    val tokens = rememberPageTokens(document, chapterText, state.page, onWordTap)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val marginPx = with(LocalDensity.current) { HorizontalMargin.roundToPx() }
    val latestTap = rememberUpdatedState(readerSurfaceTap(marginPx, tokens, layout, state))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag(READER_PAGE_TAG)
            .readerTapInput(latestTap)
            .padding(horizontal = HorizontalMargin),
    ) {
        if (tokens != null) {
            SelectionContainer {
                Text(
                    text = tokens.text,
                    style = document.renderer.styles.body,
                    modifier = Modifier.fillMaxSize(),
                    onTextLayout = { result -> layout = result },
                )
            }
        }
    }
}

/** The page's word layer, rebuilt only when the page or the chapter changes. */
@Composable
private fun rememberPageTokens(
    document: ReaderDocument,
    chapterText: AnnotatedString,
    page: ReaderPage?,
    onWordTap: (WordToken) -> Unit,
): ReaderTokens? {
    val latestTap = rememberUpdatedState(onWordTap)
    return remember(chapterText, page, document.chapter, document.renderer) {
        page?.let { slice ->
            buildPageTokens(chapterText, slice, document.renderer, document.chapter) { word ->
                latestTap.value(word)
            }
        }
    }
}

/** The tap the page handles: a word's link wins, otherwise the tap's zone decides. */
private fun readerSurfaceTap(
    marginPx: Int,
    tokens: ReaderTokens?,
    layout: TextLayoutResult?,
    state: ReaderState,
): (Offset, Float) -> Unit = { position, width ->
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

/**
 * Pages the chapter incrementally: a fresh list is filled by a coroutine in this
 * composable's scope, so the first page appears before the last is measured. The
 * list's snapshot reads drive recomposition as pages arrive.
 */
@Composable
private fun BoxWithConstraintsScope.rememberReaderPages(
    chapterText: AnnotatedString,
    style: TextStyle,
): List<ReaderPage> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val contentWidth = with(density) { (maxWidth - HorizontalMargin * 2).roundToPx() }.coerceAtLeast(0)
    val contentHeight = with(density) { (maxHeight - ChromeHeight - PageBarHeight).roundToPx() }.coerceAtLeast(0)
    val pages = remember(chapterText, contentWidth, contentHeight, density.density, density.fontScale) {
        mutableStateListOf<ReaderPage>()
    }
    val layout = remember(measurer, style, contentWidth, contentHeight) {
        ReaderLayout(measurer, style, contentWidth, contentHeight)
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
private fun ReaderTitle(title: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ChromeHeight).padding(horizontal = HorizontalMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let { back -> TextButton(onClick = back) { Text("Library") } }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = if (onBack == null) 0.dp else 8.dp),
        )
    }
}

/** The chrome: page position and the two navigation affordances. */
@Composable
private fun ReaderPageBar(page: Int, total: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(PageBarHeight).padding(horizontal = HorizontalMargin),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, enabled = page > 0) { Text("Previous page") }
        Text("Page ${page + 1} of $total")
        TextButton(onClick = onNext, enabled = page < total - 1) { Text("Next page") }
    }
}
