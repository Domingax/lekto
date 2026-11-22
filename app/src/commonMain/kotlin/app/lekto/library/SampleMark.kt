package app.lekto.library

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.ui.HAIRLINE_THICKNESS

/** The test tag on the **Sample book**'s mark, so a UI test can tell it from a vault book (issue #278). */
internal fun librarySampleMarkTag(bookId: String): String = "library-sample-mark-$bookId"

/** The mark's padding around its word, so the outlined frame is a target-sized label rather than a hairline box. */
private val MARK_PADDING = 4.dp

/**
 * The **Sample book**'s mark (issue #278; docs/design.md §7.1): a small outlined
 * word beside the row's title, so the shelf says what the row is without a second
 * screen. It is decoration beside the title rather than a control, so a screen
 * reader reads the row as one item.
 */
@Composable
internal fun SampleMark(bookId: String) {
    val theme = MaterialTheme.colorScheme
    Text(
        text = LocalAppTexts.current[TextKey.SAMPLE_MARK],
        style = MaterialTheme.typography.labelMedium,
        color = theme.primary,
        modifier = Modifier
            .border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Control)
            .padding(horizontal = MARK_PADDING, vertical = MARK_PADDING)
            .testTag(librarySampleMarkTag(bookId)),
    )
}
