package app.lekto.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.core.reading.ReadingFace
import app.lekto.core.reading.ReadingMargin
import app.lekto.core.reading.ReadingPreferences
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.ui.handCursor
import app.lekto.ui.swallowTaps

/** The test tag on the reader's **Aa** control, so a UI test can open the panel exactly. */
const val READING_PREFERENCES_TAG: String = "reader-reading-preferences"

/** The test tag on the panel's **Close** action, so a UI test can dismiss the panel exactly. */
const val READING_PREFERENCES_CLOSE_TAG: String = "reader-reading-preferences-close"

/** The test tag on the size controls. */
const val READING_SMALLER_TAG: String = "reader-reading-smaller"
const val READING_LARGER_TAG: String = "reader-reading-larger"

/** The test tag on a **Reading preference** face option. */
internal fun readerFaceTag(face: ReadingFace): String = "reader-face-${face.name}"

/** The test tag on a **Reading preference** margin option. */
internal fun readerMarginTag(margin: ReadingMargin): String = "reader-margin-${margin.name}"

/**
 * The reader's **Aa** panel (ADR-0035): the face, the size and the margin of the
 * **Reading preference**, shown in the reader's own chrome so the effect is
 * previewed as it is changed. It owns no state — it renders [preferences] and
 * raises the whole next value through [onChange] — so the reader re-typesets and
 * repaginates as each control moves, and the panel is proved through semantics.
 *
 * [onDismiss] is the panel's own **Close**, and the reader also dismisses on a tap
 * outside it, so the panel is never a trap. The panel swallows taps that land on
 * it, so only a tap truly outside it dismisses.
 *
 * The leading is deliberately absent: it follows the size, so the reader has one
 * control for it rather than two that fight each other (ADR-0035).
 */
@Composable
internal fun ReadingPreferencesPanel(
    preferences: ReadingPreferences,
    onChange: (ReadingPreferences) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().swallowTaps(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CloseRow(onDismiss)
            FaceRow(preferences, onChange)
            SizeRow(preferences, onChange)
            MarginRow(preferences, onChange)
        }
    }
}

/** The panel's own way out, for a reader who does not think to tap outside it. */
@Composable
private fun CloseRow(onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.handCursor().testTag(READING_PREFERENCES_CLOSE_TAG),
        ) {
            Text(LocalAppTexts.current[TextKey.CLOSE])
        }
    }
}

/** The face choice: one chip per platform face, the current one selected. */
@Composable
private fun FaceRow(preferences: ReadingPreferences, onChange: (ReadingPreferences) -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        ControlRow,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texts[TextKey.READING_FACE], Modifier.weight(1f))
        ReadingFace.entries.forEach { face ->
            ChoiceChip(
                label = texts[face.label()],
                selected = face == preferences.face,
                onClick = { onChange(preferences.copy(face = face)) },
                modifier = Modifier.testTag(readerFaceTag(face)),
            )
        }
    }
}

/** The size choice: a step down, the current size, a step up; the leading follows it (ADR-0035). */
@Composable
private fun SizeRow(preferences: ReadingPreferences, onChange: (ReadingPreferences) -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        ControlRow,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texts[TextKey.READING_SIZE], Modifier.weight(1f))
        TextButton(
            onClick = { onChange(preferences.smaller()) },
            enabled = preferences.size > ReadingPreferences.MIN_SIZE,
            modifier = Modifier.handCursor(preferences.size > ReadingPreferences.MIN_SIZE)
                .testTag(READING_SMALLER_TAG),
        ) { Text(texts[TextKey.READING_SMALLER]) }
        Text(preferences.size.toInt().toString())
        TextButton(
            onClick = { onChange(preferences.larger()) },
            enabled = preferences.size < ReadingPreferences.MAX_SIZE,
            modifier = Modifier.handCursor(preferences.size < ReadingPreferences.MAX_SIZE)
                .testTag(READING_LARGER_TAG),
        ) { Text(texts[TextKey.READING_LARGER]) }
    }
}

/** The margin choice: the three steps, the current one selected. */
@Composable
private fun MarginRow(preferences: ReadingPreferences, onChange: (ReadingPreferences) -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        ControlRow,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texts[TextKey.READING_MARGINS], Modifier.weight(1f))
        ReadingMargin.entries.forEach { margin ->
            ChoiceChip(
                label = texts[margin.label()],
                selected = margin == preferences.margin,
                onClick = { onChange(preferences.copy(margin = margin)) },
                modifier = Modifier.testTag(readerMarginTag(margin)),
            )
        }
    }
}

/** The shared geometry of the panel's three control rows. */
private val ControlRow: Modifier = Modifier.fillMaxWidth()

/** The readable name of a face, in the reader's **App language**. */
private fun ReadingFace.label(): TextKey = when (this) {
    ReadingFace.SERIF -> TextKey.READING_FACE_SERIF
    ReadingFace.SANS_SERIF -> TextKey.READING_FACE_SANS_SERIF
}

/** The readable name of a margin step, in the reader's **App language**. */
private fun ReadingMargin.label(): TextKey = when (this) {
    ReadingMargin.NARROW -> TextKey.READING_MARGIN_NARROW
    ReadingMargin.COMFORTABLE -> TextKey.READING_MARGIN_COMFORTABLE
    ReadingMargin.WIDE -> TextKey.READING_MARGIN_WIDE
}

/** One option chip: filled when [selected], ringed otherwise, and it announces the choice. */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val marked = modifier.semantics { this.selected = selected }
    if (selected) {
        Button(onClick = onClick, modifier = marked.handCursor()) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            modifier = marked.handCursor(),
        ) { Text(label) }
    }
}
