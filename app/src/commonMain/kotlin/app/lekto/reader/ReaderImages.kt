package app.lekto.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.lekto.core.text.IMAGE_PLACEHOLDER
import app.lekto.core.text.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The decoded picture named [name], or `null` when it cannot be read.
 *
 * The reader never knows where a book's images live: the platform resolves the
 * name against the derived store and decodes it (ADR-0017, ADR-0005). A loader
 * that answers `null` is the honest "this build shows the alt instead".
 */
fun interface BookImageLoader {
    fun load(name: String): ImageBitmap?

    companion object {
        /** The loader of a reader that shows no picture: every image falls back to its alt. */
        val None: BookImageLoader = BookImageLoader { null }
    }
}

/**
 * The room an image may take: the text column's width and the height one page
 * leaves. Both come from the viewport the page is measured against, so the
 * placeholder is exact before the picture is decoded.
 */
data class ImageLayout(val width: TextUnit, val height: TextUnit)

/**
 * What the page needs to draw the pictures it slices: the [layout] their
 * placeholders are sized by, the [loader] that decodes one, and the [refs] the
 * book's image blocks name, so a page can resolve an id back to its alt.
 */
data class ReaderImages(val layout: ImageLayout, val loader: BookImageLoader, val refs: Map<String, ImageRef>)

/**
 * The rectangle an image reserves: the column's width, its own ratio preserved,
 * and the page's height as a ceiling. An image taller than the page is scaled
 * down rather than cut — half a plate is unreadable (issue #263).
 */
fun ImageRef.placeholderIn(layout: ImageLayout): Placeholder {
    val width = layout.width.value.coerceAtLeast(0f)
    val natural = width / if (ratio > 0f) ratio else SQUARE
    val capped = if (layout.height.value > 0f) natural.coerceAtMost(layout.height.value) else natural
    return Placeholder(
        width = width.sp,
        height = (if (capped > 0f) capped else width).sp,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
    )
}

/**
 * The placeholder ranges a chapter's images occupy, in the rendered text: one
 * character each, at the block's own offset, which is what lets pagination
 * measure around a picture without decoding it.
 */
fun ReaderText.imagePlaceholders(layout: ImageLayout): List<AnnotatedString.Range<Placeholder>> {
    val starts = blockOffsets(blocks)
    return blocks.mapIndexedNotNull { index, block ->
        block.image?.let { image ->
            AnnotatedString.Range(
                item = image.placeholderIn(layout),
                start = starts[index],
                end = starts[index] + IMAGE_PLACEHOLDER.length,
            )
        }
    }
}

/**
 * The image blocks a page shows: the pictures whose placeholder falls inside the
 * page's character range. The page draws exactly these, so the inline content it
 * hands the text can never name a picture the slice does not carry.
 */
fun ReaderText.imagesIn(page: ReaderPage): List<ImageRef> {
    val starts = blockOffsets(blocks)
    return blocks.mapIndexedNotNull { index, block ->
        val image = block.image ?: return@mapIndexedNotNull null
        val start = starts[index]
        if (start < page.end && start + IMAGE_PLACEHOLDER.length > page.start) image else null
    }
}

/** The ratio a corrupt or missing header falls back to: square, never a division by zero. */
private const val SQUARE = 1f

/**
 * The page's pictures, ready to be placed in the text: one [InlineTextContent] per
 * image the slice carries, sized by the same placeholder the paginator measured.
 *
 * A picture is decoded off the main thread, so a page turn never waits on a JPEG;
 * until it arrives the alt text stands in its rectangle, which is also what a
 * reader sees when the bytes are missing or unreadable (issue #263).
 */
@Composable
fun rememberInlineImages(
    refs: List<ImageRef>,
    images: ReaderImages,
    styles: ReaderStyles,
): Map<String, InlineTextContent> {
    val inline = mutableMapOf<String, InlineTextContent>()
    refs.forEach { ref -> inline[ref.name] = inlineImage(ref.name, ref, images, styles) }
    return inline
}

/** One image as inline content: the decoded picture, or its alt while it loads or when it cannot be read. */
@Suppress("InjectDispatcher") // A picture decodes off the main thread; the reader's UI test drives the real one.
@Composable
private fun inlineImage(
    name: String,
    ref: ImageRef,
    images: ReaderImages,
    styles: ReaderStyles,
): InlineTextContent {
    val placeholder = ref.placeholderIn(images.layout)
    val picture by produceState<ImageBitmap?>(initialValue = null, name, images.loader) {
        value = withContext(Dispatchers.Default) { images.loader.load(name) }
    }
    return InlineTextContent(placeholder) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val decoded = picture
            when {
                decoded != null -> Image(
                    bitmap = decoded,
                    contentDescription = ref.alt.ifEmpty { null },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

                ref.alt.isNotEmpty() -> Text(
                    text = ref.alt,
                    style = styles.body.copy(color = styles.body.color.copy(alpha = ALT_INK)),
                )
            }
        }
    }
}

/** How strongly an alt stands in for a missing picture: visible, and plainly not body text. */
private const val ALT_INK = 0.6f
