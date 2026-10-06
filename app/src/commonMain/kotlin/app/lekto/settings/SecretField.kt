package app.lekto.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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

/**
 * A secret input (issue #24, acceptance criterion 4; ADR-0021): the value is
 * masked by default, so a key cannot be shoulder-surfed, with a reveal toggle
 * for the moments the user needs to check what they pasted. It is the control
 * the AI settings section (#25) uses for an API key.
 *
 * The mask is a [PasswordVisualTransformation], so the platform's own password
 * handling applies (no suggestions, a password keyboard); the toggle is a plain
 * text button, so the control needs no icon dependency and reads the same to a
 * screen reader as on screen. The reveal is remembered only for the current
 * composition — a configuration change re-masks the field — so it cannot come
 * back revealed after the screen is rebuilt.
 */
@Composable
fun SecretField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { revealed = !revealed }) {
                Text(if (revealed) HIDE else SHOW)
            }
        },
        modifier = modifier,
    )
}

/** The toggle's labels, so the control and its tests name the same words. */
internal const val SHOW: String = "Show"
internal const val HIDE: String = "Hide"
