package app.lekto.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.core.reading.ReadingFace
import app.lekto.core.reading.ReadingMargin
import app.lekto.core.reading.ReadingPreferences
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
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

/** The 1 dp hairline a surface draws around itself (docs/design.md §4: no shadows). */
private val Hairline = 1.dp

/**
 * The reader's **Aa** panel (ADR-0035; docs/design.md §7.2): a bottom sheet of
 * printed-form rows — `Text face`, `Text size`, `Margins` — whose values are
 * chips. It owns no state: it renders [preferences] and raises the whole next
 * value through [onChange], so the page above re-typesets and repaginates as each
 * chip moves. **There is no button to confirm**: the page above is the preview.
 *
 * The App theme is not here — it recolours the app, not the book, and lives in
 * Settings ▸ Appearance (ADR-0035, ADR-0036). [onDismiss] is the sheet's own
 * **Close**, and the reader also dismisses on a tap outside it, so the panel is
 * never a trap. The panel swallows taps that land on it, so only a tap truly
 * outside it dismisses.
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
    val theme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().swallowTaps(),
        color = theme.surface,
        shape = LektoRadii.SheetTop,
        border = BorderStroke(Hairline, theme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.InsidePanel),
            verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows),
        ) {
            SheetHeader(onDismiss)
            FaceRow(preferences, onChange)
            SizeRow(preferences, onChange)
            MarginRow(preferences, onChange)
        }
    }
}

/** The sheet's own name and its way out, for a reader who does not think to tap outside it. */
@Composable
private fun SheetHeader(onDismiss: () -> Unit) {
    val texts = LocalAppTexts.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = texts[TextKey.READING_TEXT],
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.handCursor().testTag(READING_PREFERENCES_CLOSE_TAG),
        ) {
            Text(texts[TextKey.CLOSE])
        }
    }
}

/** The face choice: one chip per platform face, the current one selected. */
@Composable
private fun FaceRow(preferences: ReadingPreferences, onChange: (ReadingPreferences) -> Unit) {
    val texts = LocalAppTexts.current
    ControlRow(texts[TextKey.READING_FACE]) {
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
    ControlRow(texts[TextKey.READING_SIZE]) {
        StepChip(
            label = "−",
            description = texts[TextKey.READING_SMALLER],
            enabled = preferences.size > ReadingPreferences.MIN_SIZE,
            onClick = { onChange(preferences.smaller()) },
            modifier = Modifier.testTag(READING_SMALLER_TAG),
        )
        Text(
            text = preferences.size.toInt().toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StepChip(
            label = "+",
            description = texts[TextKey.READING_LARGER],
            enabled = preferences.size < ReadingPreferences.MAX_SIZE,
            onClick = { onChange(preferences.larger()) },
            modifier = Modifier.testTag(READING_LARGER_TAG),
        )
    }
}

/** The margin choice: the three steps, the current one selected. */
@Composable
private fun MarginRow(preferences: ReadingPreferences, onChange: (ReadingPreferences) -> Unit) {
    val texts = LocalAppTexts.current
    ControlRow(texts[TextKey.READING_MARGINS]) {
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

/**
 * One printed-form row: its label in small muted type on the left, its value chips
 * on the right. The label keeps the row legible when the chips take the width, and
 * the chips are laid out after it rather than under it because a phone has the
 * room in these three rows.
 */
@Composable
private fun ControlRow(label: String, values: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        values()
    }
}

/**
 * One option chip: filled with the ink when [selected], ringed with the hairline
 * otherwise, and it announces the choice. The visible shape stays compact while
 * the touch target keeps the 48 dp floor (docs/design.md §9).
 */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = LektoSpacing.TouchTarget)
            .handCursor()
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ChipFrame(selected = selected) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) theme.onPrimary else theme.onSurface,
            )
        }
    }
}

/**
 * A size step: a chip with a sign rather than a word, named for a screen reader by
 * [description]. At the bounds it is disabled and keeps the arrow cursor, so a
 * control that promises nothing shows nothing.
 */
@Suppress("LongParameterList") // The sign, its spoken name and its state are independent inputs.
@Composable
private fun StepChip(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = LektoSpacing.TouchTarget)
            .handCursor(enabled)
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        ChipFrame(selected = false) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) theme.onSurface else theme.onSurfaceVariant,
            )
        }
    }
}

/** A chip's visible shape inside its 48 dp touch target: ink when selected, ringed otherwise. */
@Composable
private fun ChipFrame(selected: Boolean, content: @Composable () -> Unit) {
    val theme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .background(if (selected) theme.primary else Color.Transparent, LektoRadii.Control)
            .border(Hairline, if (selected) theme.primary else theme.outline, LektoRadii.Control)
            .padding(horizontal = LektoSpacing.BetweenRows, vertical = LektoSpacing.InsideLabelPair),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

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
