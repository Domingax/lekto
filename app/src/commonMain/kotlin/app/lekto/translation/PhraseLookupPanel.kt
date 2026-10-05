package app.lekto.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.TranslationShortcut

/** The test tag on the translation shortcut, so a UI test can click exactly it. */
internal const val TRANSLATION_SHORTCUT_TAG: String = "lookup-translation-shortcut"

/**
 * The phrase panel's actions (issue #87): [onOpenShortcut] hands the service's
 * prefilled page to the platform browser, and [onDismiss] closes the panel.
 */
data class PhraseLookupActions(val onOpenShortcut: (TranslationShortcut) -> Unit, val onDismiss: () -> Unit)

/**
 * The lookup panel in **phrase mode** (issue #87): it opens on the phrase the
 * reader selected and offers the zero-configuration **Translation shortcut** —
 * the service's prefilled page, handed to the platform browser. It needs no
 * **LLM provider** and no API key, so a reader gets phrase translation before
 * any provider is connected (ADR-0022). Like a Dictionary shortcut, Lekto never
 * embeds or scrapes the service: the shortcut is a plain outbound link, so the
 * reading position behind the panel is untouched and closing returns to it.
 */
@Composable
fun PhraseLookupPanel(
    phrase: String,
    shortcut: TranslationShortcut?,
    actions: PhraseLookupActions,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().padding(8.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(phrase, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = actions.onDismiss) { Text("Close") }
            }
            if (shortcut == null) {
                Text(
                    "No translation service is available for this phrase.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text("Translate online", style = MaterialTheme.typography.labelLarge)
                TextButton(
                    onClick = { actions.onOpenShortcut(shortcut) },
                    modifier = Modifier.testTag(TRANSLATION_SHORTCUT_TAG),
                ) {
                    Text(shortcut.source.label)
                }
            }
        }
    }
}
