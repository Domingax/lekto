package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.PackMetadata
import app.lekto.core.difficulty.DifficultyMetadata
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing

/** The test tag on the way back, so a UI test can click exactly it. */
internal const val ATTRIBUTION_BACK_TAG: String = "attribution-back"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar. */
internal const val ATTRIBUTION_TITLE_TAG: String = "attribution-title"

/**
 * The mandatory attribution screen (issues #18, #64, #149 and #177; ADR-0011,
 * ADR-0029): the dictionary pack and the word-difficulty table are CC BY-SA 4.0
 * works — the pack derived from Wiktionary, the table from wordfreq — so each
 * source, licence, modification and ShareAlike obligation must be visible in the
 * app. It is reached from the **Languages** surface, which owns the data it
 * describes.
 *
 * It also names the licences of what the app draws: the Lucide icon set it ships
 * as code, and the Noto Serif glyph the launcher mark's letter is an outline of.
 * Their full notices travel in `THIRD-PARTY-NOTICES.md`; this screen is what a
 * reader of an installed copy can see, and an installed copy carries no root
 * files.
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
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows),
    ) {
        SettingsFormHeader(
            title = LocalAppTexts.current[TextKey.ATTRIBUTION],
            backTag = ATTRIBUTION_BACK_TAG,
            titleTag = ATTRIBUTION_TITLE_TAG,
            onBack = onBack,
        )
        DictionaryAttribution(metadata)
        DifficultyAttribution(difficulty)
        IconAttribution()
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

/**
 * The icons' block (issue #177): Lucide's ISC grant, the Feather icons inside it,
 * and the Noto Serif outline the launcher mark's letter is. It has no
 * installed-or-not state, because the icons ship with the app itself.
 */
@Composable
private fun IconAttribution() {
    Text(
        LocalAppTexts.current[TextKey.ATTRIBUTION_ICONS_GRANT],
        style = MaterialTheme.typography.bodyMedium,
    )
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
