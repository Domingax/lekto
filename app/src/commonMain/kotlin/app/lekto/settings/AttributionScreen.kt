package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.PackMetadata
import app.lekto.core.difficulty.DifficultyMetadata
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey

/**
 * The mandatory attribution screen (issues #18, #64 and #149; ADR-0011,
 * ADR-0029): the dictionary pack and the word-difficulty table are CC BY-SA 4.0
 * works — the pack derived from Wiktionary, the table from wordfreq — so each
 * source, licence, modification and ShareAlike obligation must be visible in the
 * app. It is reached from the **Languages** surface, which owns the data it
 * describes.
 *
 * When an artifact is installed its metadata comes from inside it; when none is,
 * the screen still states the licence it will carry, so the obligation is never
 * hidden behind a download.
 */
@Composable
fun AttributionScreen(
    metadata: PackMetadata?,
    difficulty: DifficultyMetadata? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(LocalAppTexts.current[TextKey.BACK]) }
        Text(LocalAppTexts.current[TextKey.ATTRIBUTION], style = MaterialTheme.typography.headlineMedium)
        DictionaryAttribution(metadata)
        DifficultyAttribution(difficulty)
    }
}

/** The dictionary pack's block: the Wiktionary grant, and its metadata when a pack is installed. */
@Composable
private fun DictionaryAttribution(metadata: PackMetadata?) {
    val texts = LocalAppTexts.current
    Text(
        texts[TextKey.ATTRIBUTION_DICTIONARY_GRANT],
        style = MaterialTheme.typography.bodyMedium,
    )
    if (metadata == null) {
        Text(
            texts[TextKey.ATTRIBUTION_DICTIONARY_NOT_INSTALLED],
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Fields(
            metadata.attribution,
            metadata.sourceData,
            metadata.license,
            metadata.licenseUrl,
            metadata.modifications,
            metadata.notice,
        )
    }
}

/** The difficulty table's block: the wordfreq grant, and its metadata when a table is installed. */
@Composable
private fun DifficultyAttribution(difficulty: DifficultyMetadata?) {
    val texts = LocalAppTexts.current
    Text(
        texts[TextKey.ATTRIBUTION_DIFFICULTY_GRANT],
        style = MaterialTheme.typography.bodyMedium,
    )
    if (difficulty == null) {
        Text(
            texts[TextKey.ATTRIBUTION_DIFFICULTY_NOT_INSTALLED],
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Fields(
            difficulty.attribution,
            difficulty.sourceData,
            difficulty.license,
            difficulty.licenseUrl,
            difficulty.modifications,
            difficulty.notice,
        )
    }
}

/** One artifact's source, data, licence and modifications, with its notice when it carries one. */
@Composable
@Suppress("LongParameterList") // The fields both a pack's and a difficulty table's metadata expose.
private fun Fields(
    attribution: String?,
    sourceData: String?,
    licenseName: String?,
    licenseUrl: String?,
    modifications: String?,
    notice: String?,
) {
    val texts = LocalAppTexts.current
    Field(texts[TextKey.FIELD_SOURCE], attribution)
    Field(texts[TextKey.FIELD_DATA], sourceData)
    Field(texts[TextKey.FIELD_LICENCE], license(licenseName, licenseUrl))
    Field(texts[TextKey.FIELD_MODIFICATIONS], modifications)
    notice?.let { text -> Text(text, style = MaterialTheme.typography.bodySmall) }
}

private fun license(name: String?, url: String?): String = listOfNotNull(name, url).joinToString(" — ")

@Composable
private fun Field(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
