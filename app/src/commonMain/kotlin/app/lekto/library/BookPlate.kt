package app.lekto.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.text.baseLanguage
import app.lekto.theme.LanguageHues
import app.lekto.theme.LektoRadii
import app.lekto.theme.contentColorOn

/** The language vignette's frame: 44 × 60 dp, the fallback plate where a cover may go (§7.1). */
internal val LANGUAGE_VIGNETTE_WIDTH = 44.dp
internal val LANGUAGE_VIGNETTE_HEIGHT = 60.dp

/**
 * The book's cover, decoded off the main thread, or `null` while it loads or when
 * the book has none. A cover is a **Derived asset** the app decodes with its own
 * platform (ADR-0005, ADR-0011), never a dependency the reader has to wait for.
 */
@Composable
internal fun rememberCover(bookId: String, covers: BookCoverLoader): ImageBitmap? =
    rememberDerivedAsset(bookId, covers) { covers.load(bookId) }.value

/**
 * The book's plate (docs/design.md §7.1; issue #259): its cover when one decoded,
 * the language vignette otherwise. The frame is the same 44 × 60 either way, so a
 * shelf's columns stay aligned and a book is never a hole.
 */
@Composable
internal fun BookPlate(language: String?, cover: ImageBitmap?, modifier: Modifier = Modifier) {
    if (cover != null) {
        Image(
            bitmap = cover,
            contentDescription = languageCode(language).ifEmpty { null },
            contentScale = ContentScale.Crop,
            modifier = modifier
                .width(LANGUAGE_VIGNETTE_WIDTH)
                .height(LANGUAGE_VIGNETTE_HEIGHT)
                .clip(LektoRadii.Control),
        )
    } else {
        LanguageVignette(language, modifier = modifier)
    }
}

/**
 * The language vignette (docs/design.md §2.4, §7.1; issue #208): a control-shaped
 * tile in the **Reading language**'s hue, carrying its two-letter code. The code
 * is the signal — a reader who cannot tell the hues apart reads the letters — so
 * the hue never has to carry a meaning on its own.
 *
 * The size is a parameter because the resume block's plate is larger than a
 * row's (issue #288); the hue, the code and the radius are the same.
 */
@Composable
internal fun LanguageVignette(
    language: String?,
    width: Dp = LANGUAGE_VIGNETTE_WIDTH,
    height: Dp = LANGUAGE_VIGNETTE_HEIGHT,
    modifier: Modifier = Modifier,
) {
    val fill = LanguageHues.fill(language, MaterialTheme.colorScheme)
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(fill, LektoRadii.Control),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = languageCode(language),
            style = MaterialTheme.typography.labelMedium,
            // The hue is dark on the daylight papers and lifted on night's, so the
            // code reads whichever way the theme went.
            color = contentColorOn(fill),
        )
    }
}

/** A **Reading language**'s two-letter code: `en-GB` reads `EN`. */
internal fun languageCode(language: String?): String = baseLanguage(language.orEmpty()).take(2).uppercase()
