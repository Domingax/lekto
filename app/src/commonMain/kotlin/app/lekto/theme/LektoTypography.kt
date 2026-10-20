package app.lekto.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A screen's title: "Library", "Vocabulary", "Settings". */
private val ScreenTitle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
)

/** A section or panel title, at the lower end of the 19–24 sp band. */
private val SectionTitle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
)

/** A vocabulary word, as the book printed it. */
private val VocabularyWord = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
)

/** A list row's title. */
private val RowTitle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 22.sp,
)

/** Body copy and panel prose, at the upper end of the 13–15 sp band. */
private val BodyLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
)

/** The app's default body size. */
private val BodyMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

/** The value under a row title. */
private val ValueUnderRowTitle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/** A navigation label; the current one is announced, not only weighted. */
private val NavigationLabel = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
)

/** A field or group label; the screen paints it in the theme's muted ink. */
private val FieldLabel = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 16.sp,
)

/**
 * The two voices of `docs/design.md` §3, and the scale the app actually uses.
 *
 * Interface text is the platform's sans-serif; a screen title, a section title
 * and a vocabulary word borrow the reading serif, so the app's own headings
 * sound like a book's. No face is bundled: the platform already has both.
 *
 * The roles here are the design's, not Material 3's fifteen: an unused display
 * size aliases the screen title rather than introducing a size the identity does
 * not name, and a control label uses the body size rather than a new one.
 */
val LektoTypography: Typography = Typography(
    displayLarge = ScreenTitle,
    displayMedium = ScreenTitle,
    displaySmall = ScreenTitle,
    headlineLarge = ScreenTitle,
    headlineMedium = ScreenTitle,
    headlineSmall = ScreenTitle,
    titleLarge = SectionTitle,
    titleMedium = VocabularyWord,
    titleSmall = RowTitle,
    bodyLarge = BodyLarge,
    bodyMedium = BodyMedium,
    bodySmall = ValueUnderRowTitle,
    labelLarge = BodyMedium,
    labelMedium = NavigationLabel,
    labelSmall = FieldLabel,
)
