package app.lekto.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import app.lekto.reader.MasteryPalette
import app.lekto.reader.ReaderDocument
import app.lekto.reader.ReaderPage
import app.lekto.reader.ReaderStyles
import app.lekto.reader.WordTap
import app.lekto.reader.buildChapterText
import app.lekto.reader.buildPageTokens
import app.lekto.reader.readerTapInput
import app.lekto.reader.wordAt
import app.lekto.reader.wordTapOf

/**
 * The guide's embedded page (issue #277's feedback): the sample's first paragraph
 * drawn with the reader's own word layer — [buildPageTokens] and its mastery
 * spans, set in the same reading margin — inside the guide's own frame. It
 * carries none of the reader's chrome: no page turns, no **Aa** panel, no
 * chapter bar, no page counter, and no trap at the end of the text, so the guide
 * cannot leave the reader in a book they did not open.
 *
 * A tap is hit-tested against the retained layout ([wordAt]) and [onWordTap] gets
 * the word under it; a tap that misses every word does nothing. The guide decides
 * which tapped words the sample can answer, so a tap on an unglossed word is as
 * inert as a tap on empty space (issue #277's feedback, item 8).
 */
@Composable
internal fun GuidePageBlock(
    document: ReaderDocument,
    modifier: Modifier = Modifier,
    selectedRange: IntRange? = document.selectedRange,
    onWordTap: (WordTap) -> Unit = {},
) {
    val theme = MaterialTheme.colorScheme
    val styles = remember(document.preferences, theme) {
        ReaderStyles.of(document.preferences, MasteryPalette.of(theme), theme.onBackground)
    }
    val chapterText = remember(document.text, styles) { buildChapterText(document.text, styles) }
    val page = remember(chapterText) { ReaderPage(0, chapterText.length) }
    val tokens = remember(document, chapterText, styles, selectedRange) {
        buildPageTokens(chapterText, page, document.renderer, document.text, styles, selectedRange)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val marginPx = with(LocalDensity.current) { styles.horizontalMargin.roundToPx() }
    val tap = rememberUpdatedState<(Offset) -> Unit> { position ->
        wordAt(position, marginPx, tokens.words, layout)?.let { word ->
            onWordTap(wordTapOf(word, pageStart = 0, chapterText = chapterText.text))
        }
    }
    Box(
        modifier
            .clipToBounds()
            .fillMaxSize()
            .testTag(GUIDE_PAGE_TAG)
            .readerTapInput(tap)
            .padding(horizontal = styles.horizontalMargin),
    ) {
        Text(
            text = tokens.text,
            style = styles.body,
            modifier = Modifier.fillMaxSize().testTag(GUIDE_PAGE_TEXT_TAG),
            onTextLayout = { result -> layout = result },
        )
    }
}
