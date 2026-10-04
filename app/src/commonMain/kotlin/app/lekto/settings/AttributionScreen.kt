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

/**
 * The mandatory attribution screen (issue #18; ADR-0011): the dictionary pack is
 * a CC BY-SA 4.0 work derived from Wiktionary, so the source, the licence, the
 * modifications and the ShareAlike obligation must be visible in the app.
 *
 * When a pack is installed the metadata comes from inside it; when none is, the
 * screen still states the licence the pack will carry, so the obligation is
 * never hidden behind a download.
 */
@Composable
fun AttributionScreen(metadata: PackMetadata?, onBack: () -> Unit = {}, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("Settings") }
        Text("Attribution", style = MaterialTheme.typography.headlineMedium)

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
            Field("Source", metadata.attribution)
            Field("Data", metadata.sourceData)
            Field("Licence", license(metadata))
            Field("Modifications", metadata.modifications)
            metadata.notice?.let { notice -> Text(notice, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun license(metadata: PackMetadata): String =
    listOfNotNull(metadata.license, metadata.licenseUrl).joinToString(" — ")

@Composable
private fun Field(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
