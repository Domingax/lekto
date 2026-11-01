package app.lekto.reader

import androidx.compose.ui.unit.sp
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.text.BlockKind
import app.lekto.core.text.IMAGE_PLACEHOLDER
import app.lekto.core.text.ImageRef
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An image's rectangle, before any byte is decoded: the layout depends on the
 * viewport and the header's ratio alone, which is what lets pagination measure a
 * picture it has not loaded (issue #263).
 */
@Suppress("MagicNumber") // The sizes are the point of the test.
class ReaderImageLayoutTest {

    private val layout = ImageLayout(width = 300.sp, height = 400.sp)

    private val styles = ReaderStyles.of(ReadingPreferences.DEFAULT, MasteryPalette.Light)

    @Test
    fun aWideImageTakesTheColumnAndItsOwnHeight() {
        val placeholder = ImageRef("a.png", ratio = 2f).placeholderIn(layout)

        assertEquals(300f, placeholder.width.value)
        assertEquals(150f, placeholder.height.value)
    }

    @Test
    fun anImageTallerThanThePageIsScaledDownToHalfOfIt() {
        val placeholder = ImageRef("a.png", ratio = 0.25f).placeholderIn(layout)

        assertEquals(300f, placeholder.width.value)
        assertEquals(200f, placeholder.height.value)
    }

    @Test
    fun aCorruptRatioDoesNotDivideByZero() {
        val placeholder = ImageRef("a.png", ratio = 0f).placeholderIn(layout)

        // Square, then held to the same half-page ceiling as any other picture.
        assertEquals(300f, placeholder.width.value)
        assertEquals(200f, placeholder.height.value)
    }

    @Test
    fun aPictureCostsOneCharacterOfRenderedTextSoProgressStaysExact() {
        val chapter = ReaderText(
            title = "Pictures",
            language = "en",
            blocks = listOf(
                TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Before"))),
                imageBlock("a.png"),
            ),
        )

        val chapterText = buildChapterText(chapter, styles)
        val book = StructuredText(title = chapter.title, language = chapter.language, blocks = chapter.blocks)

        assertEquals(IMAGE_PLACEHOLDER, chapterText.text.substring("Before\n\n".length))
        assertEquals(book.renderedLength(), chapterText.length)
    }

    @Test
    fun aPictureLineIsAsTallAsItsRectangle() {
        // A body style carries a fixed line height, and a picture's line obeys it
        // unless the block overrides it: the text after the picture would then be
        // laid out *behind* it, invisible, while the page still held it (issue
        // #263).
        val chapter = ReaderText("Pictures", "en", listOf(imageBlock("a.png")))

        val chapterText = buildChapterText(chapter, styles, layout)
        val pictureLine = chapterText.paragraphStyles.single { range -> range.start == 0 }

        assertEquals(150f, pictureLine.item.lineHeight.value)
    }

    @Test
    fun aPlaceholderSitsWhereItsBlockStarts() {
        val chapter = ReaderText("Pictures", "en", listOf(imageBlock("a.png")))

        val placeholders = chapter.imagePlaceholders(layout)

        assertEquals(1, placeholders.size)
        assertEquals(0, placeholders.single().start)
        assertEquals(IMAGE_PLACEHOLDER.length, placeholders.single().end)
    }

    @Test
    fun aPageShowsThePicturesItsRangeCovers() {
        val chapter = ReaderText(
            title = "Pictures",
            language = "en",
            blocks = listOf(
                TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Before"))),
                imageBlock("a.png"),
                imageBlock("b.png"),
            ),
        )
        val afterFirstPicture = "Before\n\n".length + IMAGE_PLACEHOLDER.length

        val firstPage = chapter.imagesIn(ReaderPage(0, afterFirstPicture))
        val secondPage = chapter.imagesIn(ReaderPage(afterFirstPicture, chapter.blocks.size * 4))

        assertEquals(listOf("a.png"), firstPage.map { image -> image.name })
        assertEquals(listOf("b.png"), secondPage.map { image -> image.name })
        assertTrue(chapter.imagesIn(ReaderPage(0, 1)).isEmpty())
    }

    private fun imageBlock(name: String) = TextBlock(
        kind = BlockKind.IMAGE,
        runs = listOf(TextRun(IMAGE_PLACEHOLDER)),
        image = ImageRef(name, ratio = 2f, alt = "A bar chart"),
    )
}
