package app.lekto.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.text.BlockKind
import app.lekto.core.text.InlineStyle
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.text.WordKey
import app.lekto.testkit.InMemoryLemmaLookup
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The word-token layer, tested on the [ReaderTokens] it produces rather than
 * through a frame: "every word is coloured by mastery and tappable" is a
 * property of the [androidx.compose.ui.text.AnnotatedString], and asserting it
 * here is exact and fast. The rendered screen has its own semantics test.
 */
@Suppress("TooManyFunctions") // One layer, one test per property; splitting the class hides the layer's surface.
class ReaderTextTest {

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { key ->
            when (key.key) {
                "lantern" -> MasteryLevel.UNKNOWN
                "glows" -> MasteryLevel.RECOGNIZED
                else -> MasteryLevel.KNOWN
            }
        },
    )

    private val styles = ReaderStyles.of(ReadingPreferences.DEFAULT, mastery = MasteryPalette.Light)

    private val chapter = ReaderText(
        title = "Chapter",
        language = "en",
        blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("the lantern glows")))),
    )

    @Test
    fun rendersTheBlocksAsOneText() {
        val tokens = buildReaderTokens(chapter, renderer, styles)

        assertEquals("the lantern glows", tokens.text.text)
        assertEquals(listOf("the", "lantern", "glows"), tokens.words.map { word -> word.surface })
    }

    @Test
    fun theReadingProgressDenominatorIsExactlyTheRenderedChapterText() {
        // Reading progress divides the position by the length of the text the
        // reader lays out, so core's formula and this chapter text must agree on
        // a multi-block book (issue #196).
        val text = StructuredText(
            title = "Lantern",
            language = "en",
            blocks = listOf(
                TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("the lantern glows"))),
                TextBlock(BlockKind.HEADING, listOf(TextRun("A heading"))),
                TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("and the keeper waits"))),
            ),
        )
        val book = ReaderText(text.title.orEmpty(), text.language, text.blocks)

        assertEquals(text.renderedLength(), buildChapterText(book, styles).length)
    }

    @Test
    fun wordOffsetsSliceBackToTheWord() {
        val tokens = buildReaderTokens(chapter, renderer, styles)

        tokens.words.forEach { word ->
            assertEquals(word.surface, tokens.text.text.substring(word.start, word.end))
        }
    }

    @Test
    fun highlightedLevelsAreDistinctAndKnownIsNotHighlighted() {
        val tokens = buildReaderTokens(chapter, renderer, styles)

        val unknown = styleOf(tokens, "lantern").color
        val recognized = styleOf(tokens, "glows").color
        val known = styleOf(tokens, "the").color

        assertNotEquals(unknown, recognized, "two mastery levels must not share a colour")
        assertNotEquals(unknown, known, "a highlighted word must stand out from normal text")
        assertNotEquals(recognized, known, "a highlighted word must stand out from normal text")
        assertEquals(styles.body.color, known)
    }

    @Test
    fun highlightedWordsAreUnderlinedAndKnownWordsAreNot() {
        val tokens = buildReaderTokens(chapter, renderer, styles)

        assertEquals(TextDecoration.Underline, styleOf(tokens, "lantern").textDecoration)
        assertEquals(TextDecoration.None, styleOf(tokens, "the").textDecoration)
    }

    @Test
    fun aWordTapCarriesItsTokenAndItsContextSentence() {
        val tokens = buildReaderTokens(chapter, renderer, styles)
        val lantern = tokens.words.first { word -> word.surface == "lantern" }

        val tap = wordTapOf(lantern, pageStart = 0, chapterText = tokens.text.text)

        assertEquals(lantern, tap.token)
        assertTrue(
            assertNotNull(tap.contextSentence).contains("lantern"),
            "the sentence the word sits in: ${tap.contextSentence}",
        )
    }

    @Test
    fun inlineEmphasisSurvivesIntoTheText() {
        val emphasised = ReaderText(
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
        val tokens = buildReaderTokens(emphasised, renderer, styles)

        assertEquals("plain loud and soft", tokens.text.text)
        assertEquals(FontWeight.Bold, styleOf(tokens, "loud").fontWeight)
        assertEquals(FontStyle.Italic, styleOf(tokens, "soft").fontStyle)
    }

    @Test
    fun aHeadingKeepsItsOwnStyle() {
        val heading = ReaderText(
            title = "Chapter",
            language = "en",
            blocks = listOf(
                TextBlock(BlockKind.HEADING, listOf(TextRun("A Title"))),
                TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("body"))),
            ),
        )
        val tokens = buildReaderTokens(heading, renderer, styles)

        assertEquals("A Title\n\nbody", tokens.text.text)
        val titleSpan = tokens.text.spanStyles.first { span -> span.start == 0 && span.end == 7 }
        assertEquals(styles.heading.fontSize, titleSpan.item.fontSize)
    }

    @Test
    fun theChapterTextHasNoWordLayer() {
        val chapterText = buildChapterText(chapter, styles)

        assertEquals("the lantern glows", chapterText.text)
        assertTrue(chapterText.getLinkAnnotations(0, chapterText.length).isEmpty(), "chapter text must carry no links")
    }

    @Test
    fun aPageCarriesItsStylesAndItsWordsOnly() {
        val chapterText = buildChapterText(chapter, styles)
        val page = ReaderPage(0, chapterText.length)

        val tokens = buildPageTokens(chapterText, page, renderer, chapter, styles)

        assertEquals("the lantern glows", tokens.text.text)
        assertEquals(listOf("the", "lantern", "glows"), tokens.words.map { word -> word.surface })
        assertEquals(TextDecoration.Underline, styleOf(tokens, "lantern").textDecoration)
        assertTrue(
            tokens.text.getLinkAnnotations(0, tokens.text.length).isEmpty(),
            "a page carries no per-word links: the tap hit-tests the words instead (ADR-0007)",
        )
    }

    @Test
    fun aPageSliceKeepsHeadingAndEmphasisStyles() {
        val styled = ReaderText(
            title = "Chapter",
            language = "en",
            blocks = listOf(
                TextBlock(BlockKind.HEADING, listOf(TextRun("A Title"))),
                TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("plain "), TextRun("loud", setOf(InlineStyle.STRONG)))),
            ),
        )
        val chapterText = buildChapterText(styled, styles)
        val whole = ReaderPage(0, chapterText.length)

        val tokens = buildPageTokens(chapterText, whole, renderer, styled, styles)

        val titleSpan = tokens.text.spanStyles.first { span -> span.start == 0 && span.end == 7 }
        assertEquals(styles.heading.fontSize, titleSpan.item.fontSize)
        assertEquals(FontWeight.Bold, styleOf(tokens, "loud").fontWeight)
    }

    @Test
    fun theSelectedWordCarriesTheSelectionHighlightAndItsNeighboursDoNot() {
        val chapterText = buildChapterText(chapter, styles)
        val page = ReaderPage(0, chapterText.length)
        val lantern = buildPageTokens(chapterText, page, renderer, chapter, styles)
            .words.first { it.surface == "lantern" }

        val tokens = buildPageTokens(
            chapterText,
            page,
            renderer,
            chapter,
            styles,
            selected = lantern.start..lantern.end,
        )

        assertEquals(selectionHighlight(styles.body.color), styleOf(tokens, "lantern").background)
        assertEquals(Color.Unspecified, styleOf(tokens, "the").background)
    }

    @Test
    fun aSelectedPhraseWashesEveryWordItCoversAndNoWordOutsideIt() {
        val chapterText = buildChapterText(chapter, styles)
        val page = ReaderPage(0, chapterText.length)
        val words = buildPageTokens(chapterText, page, renderer, chapter, styles).words
        val phrase = words.first().start..words[1].end

        val tokens = buildPageTokens(chapterText, page, renderer, chapter, styles, selected = phrase)

        assertEquals(selectionHighlight(styles.body.color), styleOf(tokens, "the").background)
        assertEquals(selectionHighlight(styles.body.color), styleOf(tokens, "lantern").background)
        assertEquals(Color.Unspecified, styleOf(tokens, "glows").background)
    }

    @Test
    fun aTokenCarriesTheLemmaKeyWhenTheLemmaLookupKnowsIt() {
        val lemmas = InMemoryLemmaLookup(mapOf("lanterns" to "lantern"))
        val lemmaRenderer = ReaderRenderer(WhitespaceTextSegmenter(), renderer.mastery, lemmas)
        val plural = ReaderText(
            title = "Chapter",
            language = "en",
            blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("the lanterns glow")))),
        )

        val tokens = buildReaderTokens(plural, lemmaRenderer, styles)

        assertEquals(WordKey("en", "lantern"), tokens.words.first { it.surface == "lanterns" }.key)
    }

    @Test
    fun aTapCarriesTheSentenceTheWordWasFoundIn() {
        val chapterText = buildChapterText(chapter, styles)
        val page = ReaderPage(0, chapterText.length)

        val tokens = buildPageTokens(chapterText, page, renderer, chapter, styles)
        val tap = wordTapOf(tokens.words.first { it.surface == "lantern" }, page.start, chapterText.text)

        assertEquals("the lantern glows", tap.contextSentence)
    }
}

/** The span style applied to [surface]'s range. */
private fun styleOf(tokens: ReaderTokens, surface: String): SpanStyle {
    val word = tokens.words.first { it.surface == surface }
    return tokens.text.spanStyles.first { span -> span.start == word.start && span.end == word.end }.item
}
