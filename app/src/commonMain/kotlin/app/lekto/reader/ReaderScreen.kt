package app.lekto.reader

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.lekto.core.text.WordToken
import kotlinx.coroutines.yield

private val HorizontalMargin = 24.dp
private val ChromeHeight = 48.dp
private val PageBarHeight = 64.dp

/** The reading surface, tappable outside a word to toggle the chrome. */
private const val READING_SURFACE = "Reading surface"

/** How many pages to lay out between yields, so the first page is shown at once. */
private const val PAGE_YIELD_EVERY = 8

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
 * A tap on the reading surface outside a word toggles the chrome: while reading,
 * the title bar and page bar give way to the text, and a tap brings them back.
 * The page is a plain (non-lazy) [SelectionContainer] so every visible word is
 * composed and selectable; pagination is incremental, so a long book shows its
 * first page without laying out the whole book.
 *
 * [ReaderActions.onPositionChange] is called with the character offset of each
 * page the reader turns to, so the caller can persist the reading position.
 */
@Composable
fun ReaderScreen(document: ReaderDocument, modifier: Modifier = Modifier, actions: ReaderActions = ReaderActions()) {
    val tokens = rememberReaderTokens(document, actions.onWordTap)
    var chromeVisible by remember(tokens) { mutableStateOf(true) }
    Box(modifier.fillMaxSize()) {
        // The reading surface sits behind the text: a tap on it, but not on a
        // word (which the word layer consumes), recedes or returns the chrome.
        Box(
            Modifier
                .matchParentSize()
                .clickable { chromeVisible = !chromeVisible }
                .semantics { contentDescription = READING_SURFACE },
        )
        ReaderViewport(document, tokens, actions, chromeVisible)
    }
}

/** Builds the word layer once per chapter and renderer, reading the tap callback live. */
@Composable
private fun rememberReaderTokens(document: ReaderDocument, onWordTap: (WordToken) -> Unit): ReaderTokens {
    val latestTap = rememberUpdatedState(onWordTap)
    val renderer = document.renderer
    return remember(document.chapter, renderer.segmenter, renderer.mastery, renderer.styles) {
        buildReaderTokens(document.chapter, renderer) { word -> latestTap.value(word) }
    }
}

/** The viewport-sized page, its chrome, and the anchor the reader navigates by. */
@Composable
private fun ReaderViewport(
    document: ReaderDocument,
    tokens: ReaderTokens,
    actions: ReaderActions,
    chromeVisible: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pages = rememberReaderPages(tokens, document.renderer.styles.body)
        val anchor = remember(tokens) { mutableStateOf(document.initialOffset.coerceIn(0, tokens.text.length)) }
        val paging = readerPaging(pages, anchor.value, tokens.text.length)
        Column(Modifier.fillMaxSize()) {
            if (chromeVisible) ReaderTitle(document.chapter.title, actions.onBack)
            ReaderPageView(tokens.text, paging.page, document.renderer.styles.body)
            if (chromeVisible && paging.page != null) {
                ReaderPageBar(
                    page = paging.index,
                    total = paging.total,
                    onPrevious = { navigate(pages, paging.index - 1, anchor, actions.onPositionChange) },
                    onNext = { navigate(pages, paging.index + 1, anchor, actions.onPositionChange) },
                )
            }
        }
    }
}

/** What the viewport shows: the current [page] (null until the anchor is measured), its [index], and the [total]. */
private data class ReaderPaging(val index: Int, val total: Int, val page: ReaderPage?)

/**
 * Resolves the page holding [anchor]. Until the page measuring it exists, [ReaderPaging.page]
 * is null, so a resumed session never flashes through the pages before its place.
 */
private fun readerPaging(pages: List<ReaderPage>, anchor: Int, textLength: Int): ReaderPaging {
    val end = pages.lastOrNull()?.end
    if (end == null || (anchor >= end && end < textLength)) {
        return ReaderPaging(index = 0, total = pages.size, page = null)
    }
    val index = pageIndexFor(pages, anchor)
    return ReaderPaging(index = index, total = pages.size, page = pages.getOrNull(index))
}

/**
 * Pages the chapter incrementally: a fresh list is filled by a coroutine in this
 * composable's scope, so the first page appears before the last is measured. The
 * list's snapshot reads drive recomposition as pages arrive.
 */
@Composable
private fun BoxWithConstraintsScope.rememberReaderPages(tokens: ReaderTokens, style: TextStyle): List<ReaderPage> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val contentWidth = with(density) { (maxWidth - HorizontalMargin * 2).roundToPx() }.coerceAtLeast(0)
    val contentHeight = with(density) { (maxHeight - ChromeHeight - PageBarHeight).roundToPx() }.coerceAtLeast(0)
    val pages = remember(tokens, contentWidth, contentHeight, density.density, density.fontScale) {
        mutableStateListOf<ReaderPage>()
    }
    val layout = remember(measurer, style, contentWidth, contentHeight) {
        ReaderLayout(measurer, style, contentWidth, contentHeight)
    }
    LaunchedEffect(layout) { fillPages(tokens.text, layout, pages) }
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

/** Moves [anchor] to the start of page [target] and reports the new reading position. */
private fun navigate(pages: List<ReaderPage>, target: Int, anchor: MutableState<Int>, onPositionChange: (Int) -> Unit) {
    if (pages.isEmpty()) return
    val bounded = target.coerceIn(0, pages.lastIndex)
    anchor.value = pages[bounded].start
    onPositionChange(anchor.value)
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

/** The visible page: one non-lazy [Box], selectable, holding the sliced text. */
@Composable
private fun ColumnScope.ReaderPageView(text: AnnotatedString, page: ReaderPage?, style: TextStyle) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = HorizontalMargin)) {
        if (page != null) {
            SelectionContainer {
                Text(text = text.subSequence(page.start, page.end), style = style, modifier = Modifier.fillMaxSize())
            }
        }
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
