package app.lekto.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The reader's typography: a serif body and a heading style, pinned here rather
 * than taken from the (UI) Material theme so pagination measures exactly what
 * the page renders. The defaults follow the UX spec: 18px serif at 1.7 line
 * height.
 */
data class ReaderStyles(val body: TextStyle, val heading: TextStyle) {
    companion object {
        /** Near-black on the light reading theme. */
        private val Ink = Color(0xFF1A1A1A)

        val Reading = ReaderStyles(
            body = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                lineHeight = 31.sp,
                color = Ink,
            ),
            heading = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
            ),
        )
    }
}
