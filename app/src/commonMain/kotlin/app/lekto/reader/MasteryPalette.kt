package app.lekto.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import app.lekto.core.MasteryLevel

/**
 * The mastery palette, the most important colour system in the product
 * (`docs/ux-design-specification.md` §Color System). It lives in the
 * presentation module, not the domain: `MasteryLevel` is the domain fact, the
 * colour is how the reader paints it.
 */
private val Unknown = Color(0xFF2563EB) // Blue, strong highlight.
private val Familiar = Color(0xFFFACC15) // Yellow.
private val Recognized = Color(0xFFFB923C) // Light orange.
private val Mastered = Color(0xFF22C55E) // Green.

/** The colour a word at [level] is painted with; `Unspecified` means normal text. */
fun MasteryLevel.readerColor(): Color = when (this) {
    MasteryLevel.UNKNOWN -> Unknown
    MasteryLevel.FAMILIAR -> Familiar
    MasteryLevel.RECOGNIZED -> Recognized
    MasteryLevel.MASTERED -> Mastered
    MasteryLevel.KNOWN -> Color.Unspecified
}

/**
 * [readerColor], or [normal] when the level has no colour of its own — the
 * single place "a known word is normal text" is expressed, so the renderer and
 * the golden preview cannot drift apart.
 */
fun MasteryLevel.readerColorOr(normal: Color): Color =
    readerColor().takeIf { colour -> colour != Color.Unspecified } ?: normal

/**
 * The non-colour indicator for [this] level, for colour-blind readers (UX spec
 * §Accessibility). Compose's [TextDecoration] offers only none/underline/
 * line-through, so the spike uses a single underline: highlighted words carry
 * one, known words do not. A distinct shape per level needs a custom
 * `TextDecoration` renderer and is recorded as follow-up in the spike report.
 */
fun MasteryLevel.readerDecoration(): TextDecoration = if (highlighted) TextDecoration.Underline else TextDecoration.None
