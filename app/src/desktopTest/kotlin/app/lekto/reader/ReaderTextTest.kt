package app.lekto.reader

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BlockKind
import app.lekto.core.text.InlineStyle
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.text.WordToken
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * The word-token layer, tested on the [ReaderTokens] it produces rather than
 * through a frame: "every word is coloured by mastery and tappable" is a
 * property of the [androidx.compose.ui.text.AnnotatedString], and asserting it
 * here is exact and fast. The rendered screen has its own semantics test.
 */
class ReaderTextTest {

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { word, _ ->
            when (word) {
                "lantern" -> MasteryLevel.UNKNOWN
                "glows" -> MasteryLevel.FAMILIAR
                else -> MasteryLevel.KNOWN
            }
        },
    )

    private val chapter = ReaderChapter(
        title = "Chapter",
        language = "en",
        blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("the lantern glows")))),
    )

    @Test
    fun rendersTheBlocksAsOneText() {
        val tokens = buildReaderTokens(chapter, renderer)

        assertEquals("the lantern glows", tokens.text.text)
        assertEquals(listOf("the", "lantern", "glows"), tokens.words.map { word -> word.surface })
    }

    @Test
    fun wordOffsetsSliceBackToTheWord() {
        val tokens = buildReaderTokens(chapter, renderer)

        tokens.words.forEach { word ->
            assertEquals(word.surface, tokens.text.text.substring(word.start, word.end))
        }
    }

    @Test
    fun highlightedLevelsAreDistinctAndKnownIsNotHighlighted() {
        val tokens = buildReaderTokens(chapter, renderer)

        val unknown = styleOf(tokens, "lantern").color
        val familiar = styleOf(tokens, "glows").color
        val known = styleOf(tokens, "the").color

        assertNotEquals(unknown, familiar, "two mastery levels must not share a colour")
        assertNotEquals(unknown, known, "a highlighted word must stand out from normal text")
        assertNotEquals(familiar, known, "a highlighted word must stand out from normal text")
        assertEquals(ReaderStyles.Reading.body.color, known)
    }

    @Test
    fun highlightedWordsAreUnderlinedAndKnownWordsAreNot() {
        val tokens = buildReaderTokens(chapter, renderer)

        assertEquals(TextDecoration.Underline, styleOf(tokens, "lantern").textDecoration)
        assertEquals(TextDecoration.None, styleOf(tokens, "the").textDecoration)
    }

    @Test
    fun everyWordIsTappable() {
        val tapped = mutableListOf<WordToken>()
        val tokens = buildReaderTokens(chapter, renderer, onWordTap = { word -> tapped += word })

        tokens.words.forEach { word -> tap(tokens, word) }

        assertEquals(tokens.words, tapped)
    }

    @Test
    fun inlineEmphasisSurvivesIntoTheText() {
        val emphasised = ReaderChapter(
            title = "Chapter",
            language = "en",
            blocks = listOf(
                TextBlock(
                    BlockKind.PARAGRAPH,
                    listOf(
                        TextRun("plain "),
                        TextRun("loud", setOf(InlineStyle.STRONG)),
                        TextRun(" and "),
                        TextRun("soft", setOf(InlineStyle.EMPHASIS)),
                    ),
                ),
            ),
        )
        val tokens = buildReaderTokens(emphasised, renderer)

        assertEquals("plain loud and soft", tokens.text.text)
        assertEquals(FontWeight.Bold, styleOf(tokens, "loud").fontWeight)
        assertEquals(FontStyle.Italic, styleOf(tokens, "soft").fontStyle)
    }

    @Test
    fun aHeadingKeepsItsOwnStyle() {
        val heading = ReaderChapter(
            title = "Chapter",
            language = "en",
            blocks = listOf(
                TextBlock(BlockKind.HEADING, listOf(TextRun("A Title"))),
                TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("body"))),
            ),
        )
        val tokens = buildReaderTokens(heading, renderer)

        assertEquals("A Title\n\nbody", tokens.text.text)
        val titleSpan = tokens.text.spanStyles.first { span -> span.start == 0 && span.end == 7 }
        assertEquals(ReaderStyles.Reading.heading.fontSize, titleSpan.item.fontSize)
    }

    private fun tap(tokens: ReaderTokens, word: WordToken) {
        val range = tokens.text.getLinkAnnotations(word.start, word.end).firstOrNull()
        assertNotNull(range, "word '${word.surface}' has no link")
        val clickable = range.item as LinkAnnotation.Clickable
        clickable.linkInteractionListener?.onClick(clickable)
    }

    private fun styleOf(tokens: ReaderTokens, surface: String): SpanStyle {
        val word = tokens.words.first { it.surface == surface }
        return tokens.text.spanStyles.first { span -> span.start == word.start && span.end == word.end }.item
    }
}
