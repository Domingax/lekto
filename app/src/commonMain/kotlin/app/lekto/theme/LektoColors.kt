package app.lekto.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The five colours an **App theme** names (`docs/design.md` §2.1, §2.4): a
 * paper, a surface for cards and panels, an ink, a muted ink, the hairline that
 * separates two surfaces, and the red reserved for irreversible actions and
 * failures.
 */
private data class LektoPaper(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val separator: Color,
    val error: Color,
)

private val LightPaper = LektoPaper(
    paper = Color(0xFFFAF7F2),
    surface = Color(0xFFF3EEE6),
    ink = Color(0xFF1C1A17),
    muted = Color(0xFF6B6357),
    separator = Color(0xFFD6CCBC),
    error = Color(0xFFB3261E),
)

private val DarkPaper = LektoPaper(
    paper = Color(0xFF15130F),
    surface = Color(0xFF1F1C17),
    ink = Color(0xFFE8E2D8),
    muted = Color(0xFF9A9083),
    separator = Color(0xFF3A342B),
    error = Color(0xFFF2B8B5),
)

private val SepiaPaper = LektoPaper(
    paper = Color(0xFFF6ECD8),
    surface = Color(0xFFEFE3C9),
    ink = Color(0xFF3B2F1E),
    muted = Color(0xFF6B5B41),
    separator = Color(0xFFD8C7A6),
    error = Color(0xFFB3261E),
)

/**
 * The three papers an **App theme** can put on screen (`docs/design.md` §2.1;
 * ADR-0036).
 *
 * The app has no accent colour: [ColorScheme.primary] is the ink and
 * [ColorScheme.onPrimary] is the paper, so a filled button is an ink rectangle.
 * The mastery ramps are deliberately absent — they are their own measured table
 * per paper (issue #183).
 *
 * Paper, ink and a hairline: the daylight theme.
 */
val Light: ColorScheme = lektoColorScheme(LightPaper)

/** Warm night: the same identity read after dark. */
val Dark: ColorScheme = lektoColorScheme(DarkPaper)

/** Rag paper: the identity's warmth in daylight, chosen by hand. */
val Sepia: ColorScheme = lektoColorScheme(SepiaPaper)

/**
 * The relative luminance below which a paper is the dark one, so a hue that
 * reads on a daylight paper is lifted before it is put on night's
 * (`docs/design.md` §2.2, §2.4; ADR-0036). Sepia is above it: it is a daylight
 * paper, and its hues are the light ones.
 */
internal const val DARK_PAPER_LUMINANCE: Float = 0.18f

/** Whether [paper] is dark enough to need lifted hues. */
internal fun isDarkPaper(paper: Color): Boolean = paper.luminance() < DARK_PAPER_LUMINANCE

/**
 * One scheme from the five colours the identity names. Every Material slot is
 * filled from them: an unspecified slot would fall back to Material 3's purple
 * baseline and put an accent into a product that has none.
 *
 * The constructor is used directly rather than Material 3's `lightColorScheme` or
 * `darkColorScheme`, because the app fills every role itself and the paper — not
 * the factory — decides whether the palette is light or dark.
 */
@Suppress("LongMethod") // One declarative mapping onto every Material slot.
private fun lektoColorScheme(paper: LektoPaper): ColorScheme = ColorScheme(
    primary = paper.ink,
    onPrimary = paper.paper,
    primaryContainer = paper.surface,
    onPrimaryContainer = paper.ink,
    inversePrimary = paper.paper,
    secondary = paper.ink,
    onSecondary = paper.paper,
    // A selected navigation or filter item is a filled control: ink behind paper.
    secondaryContainer = paper.ink,
    onSecondaryContainer = paper.paper,
    tertiary = paper.muted,
    onTertiary = paper.paper,
    tertiaryContainer = paper.surface,
    onTertiaryContainer = paper.ink,
    background = paper.paper,
    onBackground = paper.ink,
    surface = paper.surface,
    onSurface = paper.ink,
    surfaceVariant = paper.surface,
    onSurfaceVariant = paper.muted,
    // No tonal lift: a surface is separated by its tone and a hairline, not a tint.
    surfaceTint = paper.surface,
    inverseSurface = paper.ink,
    inverseOnSurface = paper.paper,
    error = paper.error,
    onError = paper.paper,
    errorContainer = paper.surface,
    onErrorContainer = paper.error,
    outline = paper.separator,
    outlineVariant = paper.separator,
    scrim = paper.ink,
    surfaceBright = paper.paper,
    surfaceDim = paper.surface,
    surfaceContainer = paper.surface,
    surfaceContainerHigh = paper.surface,
    surfaceContainerHighest = paper.surface,
    surfaceContainerLow = paper.surface,
    surfaceContainerLowest = paper.paper,
    primaryFixed = paper.ink,
    primaryFixedDim = paper.ink,
    onPrimaryFixed = paper.paper,
    onPrimaryFixedVariant = paper.muted,
    secondaryFixed = paper.ink,
    secondaryFixedDim = paper.ink,
    onSecondaryFixed = paper.paper,
    onSecondaryFixedVariant = paper.muted,
    tertiaryFixed = paper.muted,
    tertiaryFixedDim = paper.muted,
    onTertiaryFixed = paper.paper,
    onTertiaryFixedVariant = paper.ink,
)
