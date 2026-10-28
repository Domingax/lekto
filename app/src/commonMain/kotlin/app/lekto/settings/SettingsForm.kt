package app.lekto.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.theme.hoverWash
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.handCursor
import app.lekto.ui.icons.ArrowLeft
import app.lekto.ui.icons.ChevronRight
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.touchClient

/**
 * The header the two settings screens share (docs/design.md §7.5, §7.6): the
 * way back as the `arrow-left` icon, then the screen's title in the reading
 * serif. The shell keeps **Settings** current while the screen is open, and the
 * icon and the platform's Back both walk the one back stack.
 */
@Composable
internal fun SettingsFormHeader(title: String, backTag: String, titleTag: String, onBack: () -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.handCursor().testTag(backTag),
        ) {
            Icon(imageVector = LektoIcons.ArrowLeft, contentDescription = texts[TextKey.BACK])
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(titleTag),
        )
    }
}

/**
 * One field of a printed form (docs/design.md §7.5): the label in the small
 * muted style, its value under it, and a hairline — never a wall of outlined
 * boxes. [modifier] belongs to the text field itself, so a screen's test tag and
 * the model list's anchor sit on the control the reader types in.
 */
@Composable
@Suppress("LongParameterList") // One control's independent knobs: the field, its keyboard and its reveal.
internal fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    placeholder: String? = null,
    labelTrailing: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        FormLabel(label, labelTrailing)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = fieldHeight()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = theme.onBackground),
                cursorBrush = SolidColor(theme.primary),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                modifier = modifier.weight(1f),
                decorationBox = { field ->
                    Box {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = theme.onSurfaceVariant,
                            )
                        }
                        field()
                    }
                },
            )
            trailing?.invoke()
        }
        FormHairline()
    }
}

/**
 * A printed-form row whose value is chosen rather than typed — the provider
 * preset, and the model list's doorway: the label above, the current value, a
 * `chevron-right`, and the hairline.
 */
@Composable
@Suppress("LongParameterList") // One row's independent pieces: its label, value, tag and affordance.
internal fun FormRow(
    label: String,
    value: String,
    tag: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(Modifier.fillMaxWidth()) {
        FormLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = fieldHeight())
                .background(
                    if (hovered) hoverWash(MaterialTheme.colorScheme.onBackground) else Color.Transparent,
                )
                .handCursor()
                .hoverable(interaction)
                .clickable(interactionSource = null, indication = null, onClick = onClick)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
            } else {
                Chevron()
            }
        }
        FormHairline()
    }
}

/** The form's small muted label, with the 4 dp the label pair takes. */
@Composable
internal fun FormLabel(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
    ) {
        MutedLabel(text)
        trailing?.invoke()
    }
    Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
}

/** The small muted type every form label and group label shares (docs/design.md §3). */
@Composable
private fun MutedLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A desktop pointer gets the mockup's tighter field; a thumb keeps the 48 dp target. */
private fun fieldHeight() = if (touchClient) LektoSpacing.TouchTarget else COMPACT_FIELD_HEIGHT

/** The gap under a value before the hairline that closes the field. */
private val HAIRLINE_GAP = 2.dp

/** The field's height under a pointer, closer to the mockup than the thumb's 48 dp. */
private val COMPACT_FIELD_HEIGHT = 32.dp

/** The hairline that closes one field and parts it from the next. */
@Composable
internal fun FormHairline() {
    HorizontalDivider(
        thickness = HAIRLINE_THICKNESS,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = HAIRLINE_GAP),
    )
}

/** The affordance a row's value opens something: `chevron-right`, decorative beside the value. */
@Composable
private fun Chevron() {
    Icon(
        imageVector = LektoIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The group label above a form's block of rows, in the muted label style. */
@Composable
internal fun FormGroupLabel(text: String) {
    MutedLabel(text)
}

/**
 * A red text action for something that cannot be undone — `Remove key`,
 * `Disconnect` (docs/design.md §2.4, §7.5). Red belongs to the act, never to a
 * row at rest.
 */
@Composable
internal fun IrreversibleAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // A text action, not a button: the pointer underlines it rather than painting the
    // grey pill a TextButton shows on hover (issue #213).
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .heightIn(min = if (touchClient) LektoSpacing.TouchTarget else COMPACT_FIELD_HEIGHT)
            .handCursor(enabled)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // A disabled action reads muted, never as a live red invitation.
        Text(
            text = text,
            color = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (hovered && enabled) TextDecoration.Underline else null,
        )
    }
}
