package app.lekto.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.ui.handCursor

/**
 * A secret input (issue #24, acceptance criterion 4; ADR-0021): the value is
 * masked by default, so a key cannot be shoulder-surfed, with a reveal toggle
 * for the moments the user needs to check what they pasted. It is the control
 * the LLM provider and the vault's sync screen use (issue #213).
 *
 * The mask is a [PasswordVisualTransformation], so the platform's own password
 * handling applies (no suggestions, a password keyboard); the toggle is a plain
 * text button, so the control needs no icon dependency and reads the same to a
 * screen reader as on screen. The reveal is remembered only for the current
 * composition — a configuration change re-masks the field — so it cannot come
 * back revealed after the screen is rebuilt.
 *
 * It is drawn as a printed-form field (docs/design.md §7.5): the label above the
 * value and a hairline under it, not an outlined box.
 */
@Composable
@Suppress("LongParameterList") // The field's value, its label, its layout and whether a stored secret exists.
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hasStoredSecret: Boolean = false,
) {
    var revealed by remember { mutableStateOf(false) }
    FormField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        // An empty field used to read as "nothing is configured", even though a key
        // or password was stored (issue #213). A masked placeholder says one is
        // there without ever showing it; typing replaces it.
        placeholder = if (hasStoredSecret) SECRET_PLACEHOLDER else null,
        trailing = {
            // The placeholder says a secret is stored without revealing it, so a
            // Show toggle would have nothing to show until the reader types.
            if (value.isNotEmpty() || !hasStoredSecret) {
                TextButton(onClick = { revealed = !revealed }, modifier = Modifier.handCursor()) {
                    Text(LocalAppTexts.current[if (revealed) TextKey.HIDE else TextKey.SHOW])
                }
            }
        },
    )
}

/** The masked dots that say a secret is already stored, never the secret itself. */
internal val SECRET_PLACEHOLDER: String = "\u2022".repeat(12)
