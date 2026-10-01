package app.lekto.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.lekto.core.text.WordToken

private val HorizontalMargin = 24.dp
private val ChromeHeight = 48.dp
private val PageBarHeight = 64.dp

/**
 * The reader screen: a chapter paginated to the viewport, every word coloured by
 * mastery and tappable, with page navigation in both directions.
 *
 * The page is rendered in a plain (non-lazy) [Box], inside a [SelectionContainer],
 * so every visible word is composed and selectable. The [ReaderChapter] and
 * [ReaderRenderer] carry the parsed text and the word layer; the chrome is
 * deliberately fixed-height so the measured content extent matches the laid-out
 * one and pagination stays honest.
 */
@Composable
fun ReaderScreen(
    chapter: ReaderChapter,
    renderer: ReaderRenderer,
    modifier: Modifier = Modifier,
    onWordTap: (WordToken) -> Unit = {},
) {
    // The token layer is built once (its keys are the chapter and renderer), so
    // the word tap callback is read through a State instead of being captured:
    // `latestTap.value` always calls the current `onWordTap` without rebuilding
    // the text. Written as an explicit State (not `by`) because SonarCloud's
    // S1481 false-positives on a delegated local read inside a lambda.
    val latestTap = rememberUpdatedState(onWordTap)
    val tokens = remember(chapter, renderer.segmenter, renderer.mastery, renderer.styles) {
        buildReaderTokens(chapter, renderer) { word -> latestTap.value(word) }
    }
    var pageIndex by remember(tokens) { mutableStateOf(0) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val contentWidth = with(density) { (maxWidth - HorizontalMargin * 2).roundToPx() }.coerceAtLeast(0)
        val contentHeight = with(density) { (maxHeight - ChromeHeight - PageBarHeight).roundToPx() }.coerceAtLeast(0)
        // Keyed on density and font scale as well as the pixel extent: a font
        // -scale change moves the line heights without moving the pixel size.
        val pages = remember(tokens, contentWidth, contentHeight, density.density, density.fontScale) {
            paginateChapter(
                text = tokens.text,
                layout = ReaderLayout(measurer, renderer.styles.body, contentWidth, contentHeight),
            )
        }
        val index = pageIndex.coerceIn(0, pages.lastIndex)

        Column(Modifier.fillMaxSize()) {
            ReaderTitle(chapter.title)
            ReaderPageView(tokens.text, pages[index], renderer.styles.body)
            ReaderPageBar(
                page = index,
                total = pages.size,
                onPrevious = { pageIndex = (index - 1).coerceAtLeast(0) },
                onNext = { pageIndex = (index + 1).coerceAtMost(pages.lastIndex) },
            )
        }
    }
}

@Composable
private fun ReaderTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().height(ChromeHeight).padding(horizontal = HorizontalMargin),
    )
}

/** The visible page: one non-lazy [Box], selectable, holding the sliced text. */
@Composable
private fun ColumnScope.ReaderPageView(text: AnnotatedString, page: ReaderPage, style: TextStyle) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = HorizontalMargin)) {
        SelectionContainer {
            Text(text = text.subSequence(page.start, page.end), style = style, modifier = Modifier.fillMaxSize())
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
