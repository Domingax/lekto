package app.lekto.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.core.language.AppLanguages
import app.lekto.core.language.readingLanguageName

// The app's own language section of the settings screen (ADR-0034), split from
// the screen itself so each stays within the rules.

/** The test tag on the **App language** picker, so a UI test can open exactly it. */
internal const val APP_LANGUAGE_PICKER_TAG: String = "settings-app-language"

/** The test tag on an **App language** option in the picker. */
internal fun appLanguageOptionTag(language: String): String = "settings-app-language-$language"

/**
 * The **App language** section (ADR-0034): the one language the reader chooses —
 * the language of the app's own texts and the language every translation resolves
 * into. Only the languages the app ships are offered, so a choice always controls
 * both the texts and the translations.
 */
@Composable
internal fun AppLanguageSection(language: String, onSelect: (String) -> Unit) {
    Text(
        "The language Lekto uses for its own texts, and the language it translates into.",
        style = MaterialTheme.typography.bodyMedium,
    )
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag(APP_LANGUAGE_PICKER_TAG)) {
            Text(readingLanguageName(language))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguages.SUPPORTED.forEach { option ->
                DropdownMenuItem(
                    text = { Text(readingLanguageName(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.testTag(appLanguageOptionTag(option)),
                )
            }
        }
    }
}
