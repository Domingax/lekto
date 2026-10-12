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

/**
 * The mandatory attribution screen (issues #18 and #64; ADR-0011, ADR-0029): the
 * dictionary pack and the word-difficulty table are CC BY-SA 4.0 works — the pack
 * derived from Wiktionary, the table from wordfreq — so each source, licence,
 * modification and ShareAlike obligation must be visible in the app.
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
        TextButton(onClick = onBack) { Text("Settings") }
        Text("Attribution", style = MaterialTheme.typography.headlineMedium)
        DictionaryAttribution(metadata)
        DifficultyAttribution(difficulty)
    }
}

/** The dictionary pack's block: the Wiktionary grant, and its metadata when a pack is installed. */
@Composable
private fun DictionaryAttribution(metadata: PackMetadata?) {
    Text(
        "Dictionary data is derived from Wiktionary contributors, licensed under CC BY-SA 4.0 " +
            "(and GFDL at your choice). Lekto is not affiliated with, nor endorsed by, the Wikimedia " +
            "Foundation or Wiktionary.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (metadata == null) {
        Text(
            "The offline dictionary isn't installed yet. Its full attribution appears here once it is.",
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
    Text(
        "Word-difficulty data is derived from wordfreq by Robyn Speer, licensed under CC BY-SA 4.0. " +
            "Lekto is not affiliated with, nor endorsed by, wordfreq.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (difficulty == null) {
        Text(
            "The word-difficulty data isn't installed yet. Its full attribution appears here once it is.",
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
    Field("Source", attribution)
    Field("Data", sourceData)
    Field("Licence", license(licenseName, licenseUrl))
    Field("Modifications", modifications)
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
