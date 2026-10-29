@file:Suppress("TooManyFunctions")
// One screen: its header, reload, section, rows, pickers and states, each a small composable.

package app.lekto.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.CefrLevel
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.text.baseLanguage
import app.lekto.dictionary.DictionaryUiState
import app.lekto.difficulty.DifficultyUiState
import app.lekto.settings.FormRow
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.ScreenHeader
import app.lekto.ui.TextAction
import app.lekto.ui.handCursor
import app.lekto.ui.icons.ChevronRight
import app.lekto.ui.icons.LektoIcons

/** The test tag on the **Reload list** action, so a UI test can click exactly it. */
const val LANGUAGES_RELOAD_TAG: String = "languages-reload"

/** The test tag on the way back to Settings, so a UI test can click exactly it. */
internal const val LANGUAGES_BACK_TAG: String = "languages-back"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar. */
internal const val LANGUAGES_TITLE_TAG: String = "languages-title"

/** The test tag on one **Reading language**'s row, so a UI test can read its state and action together. */
internal fun languageRowTag(language: String): String = "languages-row-$language"

/** The test tag on a language's level picker on the **Languages** surface. */
internal fun languageLevelPickerTag(language: String): String = "languages-level-$language"

/** The test tag on a CEFR option in a language's level picker. */
internal fun languageLevelOptionTag(language: String, level: CefrLevel): String =
    "languages-level-$language-${level.name}"

/** The test tag on a language's **not set** option, which clears the level. */
internal fun languageLevelClearTag(language: String): String = "languages-level-$language-none"

/** The test tag on the way to the attribution screen, so a UI test can click exactly it. */
internal const val LANGUAGES_ATTRIBUTION_TAG: String = "languages-attribution"

/** The test tag on the **Translation language** picker, so a UI test can open exactly it. */
internal const val TRANSLATION_LANGUAGE_PICKER_TAG: String = "languages-translation-language"

/** The test tag on a **Translation language** option in the picker. */
internal fun translationLanguageOptionTag(language: String): String = "languages-translation-language-$language"

/**
 * The actions the **Languages** surface raises: refetch the manifest, set or
 * clear a language's **Reader level** (issue #147), download or dismiss the
 * failure of a language's **Difficulty table** (issue #148), download or dismiss
 * the failure of a pair's **Dictionary pack** (issue #149), choose the
 * **Translation language** (ADR-0034), open the attribution screen, or leave.
 */
data class LanguagesActions(
    val onReload: () -> Unit = {},
    val onSetLevel: (String, CefrLevel) -> Unit = { _, _ -> },
    val onClearLevel: (String) -> Unit = {},
    val onDownloadDifficulty: (String, String) -> Unit = { _, _ -> },
    val onDismissDifficultyError: (String) -> Unit = {},
    val onDownloadDictionary: (String, String, String) -> Unit = { _, _, _ -> },
    val onDismissDictionaryError: (String) -> Unit = {},
    val onSetTranslationLanguage: (String) -> Unit = {},
    val onOpenAttribution: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The **Languages** surface (issues #146, #147, #148, #149, #215; ADR-0034): the
 * **Translation language** as a printed-form row, then one row per offered
 * **Reading language** in the reader's priority order. A row carries the
 * language's name, its **Reader level** picker, and each published **Derived
 * asset**'s state in the muted ink with its download as a quiet text action —
 * never a full-width filled button per language (docs/design.md §7.6).
 *
 * **Reload list** stays in the header, so a reader can refetch at any time — after
 * a failed fetch, or when the pipeline has published a language since the cached
 * copy. The attribution link sits at the foot of the list, in the muted ink. A
 * language the manifest publishes no table — or no dictionary — says so honestly
 * and offers no download, because a button that could only fail is worse than
 * none.
 *
 * The screen renders [state], [levels], [difficulty] and [dictionaries] and
 * raises the [actions]; it owns no state, so the whole surface is proved through
 * semantics.
 */
@Composable
@Suppress("LongParameterList", "LongMethod") // The surface's inputs, and its composition reads in one order.
fun LanguagesScreen(
    state: LanguagesUiState,
    levels: Map<String, CefrLevel> = emptyMap(),
    difficulty: Map<String, DifficultyUiState> = emptyMap(),
    dictionaries: Map<String, DictionaryUiState> = emptyMap(),
    translationLanguage: String? = null,
    translationLanguages: List<String> = emptyList(),
    actions: LanguagesActions = LanguagesActions(),
    modifier: Modifier = Modifier,
) {
    val texts = LocalAppTexts.current
    Column(
        modifier.fillMaxSize()
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
    ) {
        ScreenHeader(
            title = texts[TextKey.LANGUAGES],
            backTag = LANGUAGES_BACK_TAG,
            titleTag = LANGUAGES_TITLE_TAG,
            onBack = actions.onBack,
            trailing = { ReloadAction(onReload = actions.onReload, reloading = state.reloading) },
        )
        if (state.reloading) Reloading()
        state.error?.let { message -> Error(texts.message(message)) }

        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = LektoSpacing.ScreenEdge)) {
            if (state.languages.isEmpty()) {
                item { EmptyState(available = state.available) }
            } else {
                if (translationLanguages.isNotEmpty()) {
                    item { TranslationLanguageField(translationLanguage, translationLanguages, actions) }
                }
                item { LanguagesSection(afterTranslationLanguage = translationLanguages.isNotEmpty()) }
                itemsIndexed(state.languages, key = { _, offered -> offered.language }) { index, offered ->
                    // The level and the table are keyed by the language's base tag,
                    // as the ordering and naming already reduce the offered tag
                    // (CONTEXT.md, "Reading language"); a dictionary is keyed by the
                    // pair's manifest artifact name.
                    LanguageRow(
                        offered = offered,
                        level = levels[baseLanguage(offered.language)],
                        difficulty = difficulty[baseLanguage(offered.language)],
                        dictionaries = dictionaries,
                        actions = actions,
                        last = index == state.languages.lastIndex,
                    )
                }
            }
            item { AttributionLink(actions.onOpenAttribution) }
        }
    }
}

/**
 * The second section's label and explanation (issue #215): what the two
 * **Derived asset**s do, so a reader meeting word difficulty for the first time
 * knows why a language offers it and what the download changes.
 */
@Composable
private fun LanguagesSection(afterTranslationLanguage: Boolean) {
    val texts = LocalAppTexts.current
    val topGap = if (afterTranslationLanguage) LektoSpacing.BetweenBlocks else 0.dp
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = topGap),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Text(
            text = texts[TextKey.LANGUAGES_BY_LANGUAGE],
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = texts[TextKey.LANGUAGES_SECTION_DESCRIPTION],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * **Reload list**, the header's quiet action (issue #146): always visible so the
 * reader can refetch at any time, and muted while a fetch is in flight.
 */
@Composable
private fun ReloadAction(onReload: () -> Unit, reloading: Boolean) {
    val texts = LocalAppTexts.current
    TextAction(
        onClick = onReload,
        enabled = !reloading,
        modifier = Modifier.testTag(LANGUAGES_RELOAD_TAG),
    ) {
        Text(
            text = texts[TextKey.RELOAD_LIST],
            style = MaterialTheme.typography.bodyMedium,
            color = if (reloading) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
    }
}

/**
 * One offered **Reading language** (issues #147, #148, #149, #215): its name and
 * its **Reader level** picker on one line, then the state and download of its
 * **Difficulty table** and each published **Dictionary pack**, parted from the
 * next language by a hairline. The level is user-authored **Vault** data, so the
 * picker writes to the vault; **not set** clears it. The artifacts are **Derived
 * asset**s: their state and download show here, and one not published says so.
 */
@Suppress("LongParameterList") // The offered language and its three independent controls, plus the surface's actions.
@Composable
private fun LanguageRow(
    offered: OfferedLanguage,
    level: CefrLevel?,
    difficulty: DifficultyUiState?,
    dictionaries: Map<String, DictionaryUiState>,
    actions: LanguagesActions,
    last: Boolean,
) {
    Column(Modifier.fillMaxWidth().testTag(languageRowTag(offered.language))) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = LektoSpacing.BetweenRows),
            verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = offered.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                LevelPicker(offered.language, level, actions)
            }
            DifficultyControl(offered, difficulty, actions)
            DictionaryControls(offered, dictionaries, actions)
        }
        if (!last) {
            HorizontalDivider(thickness = HAIRLINE_THICKNESS, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * The **Translation language** picker (ADR-0034) as a printed-form row: the
 * label in the small muted type, the chosen language under it, a `chevron-right`
 * and the hairline. It offers only the languages the **Language manifest**
 * publishes a pack into, so a choice always has a pack to serve it, and a reader
 * who has not chosen one shows **Not set**.
 */
@Composable
private fun TranslationLanguageField(selected: String?, languages: List<String>, actions: LanguagesActions) {
    val texts = LocalAppTexts.current
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        FormRow(
            label = texts[TextKey.TRANSLATION_LANGUAGE],
            value = selected?.let(texts::languageName) ?: texts[TextKey.NOT_SET],
            tag = TRANSLATION_LANGUAGE_PICKER_TAG,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(texts.languageName(language)) },
                    onClick = {
                        expanded = false
                        actions.onSetTranslationLanguage(language)
                    },
                    modifier = Modifier.handCursor().testTag(translationLanguageOptionTag(language)),
                )
            }
        }
    }
}

/**
 * The **Reader level** picker (issue #147) as a quiet text action on the row:
 * the word `Level`, the current band — or **Not set** — and a chevron that says
 * it opens. The six CEFR bands A1–C2 and **not set**, which clears the level,
 * stay in the dropdown, so the feature survives the planche's summary row.
 */
@Composable
private fun LevelPicker(language: String, level: CefrLevel?, actions: LanguagesActions) {
    val texts = LocalAppTexts.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextAction(
            onClick = { expanded = true },
            onClickLabel = texts[TextKey.SET_READER_LEVEL],
            modifier = Modifier.testTag(languageLevelPickerTag(language)),
        ) {
            Text(
                text = texts[TextKey.READER_LEVEL],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = level?.name ?: texts[TextKey.NOT_SET],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            ChevronDown()
        }
        LevelMenu(language, expanded, onDismiss = { expanded = false }, actions = actions)
    }
}

/** The six CEFR bands and **not set**, in the dropdown the level picker opens. */
@Composable
private fun LevelMenu(language: String, expanded: Boolean, onDismiss: () -> Unit, actions: LanguagesActions) {
    val texts = LocalAppTexts.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(texts[TextKey.NOT_SET]) },
            onClick = {
                onDismiss()
                actions.onClearLevel(language)
            },
            modifier = Modifier.handCursor().testTag(languageLevelClearTag(language)),
        )
        CefrLevel.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.name) },
                onClick = {
                    onDismiss()
                    actions.onSetLevel(language, option)
                },
                modifier = Modifier.handCursor().testTag(languageLevelOptionTag(language, option)),
            )
        }
    }
}

/** The small chevron that says a picker opens; decorative beside the level it names (§9). */
@Composable
private fun ChevronDown() {
    Icon(
        imageVector = LektoIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(CHEVRON_SIZE).rotate(CHEVRON_ROTATION),
    )
}

/** The chevron's frame and the quarter turn that points a `chevron-right` down. */
private val CHEVRON_SIZE = 16.dp
private const val CHEVRON_ROTATION = 90f

/** The way to the attribution screen, at the foot of the list and in the muted ink (issue #215). */
@Composable
private fun AttributionLink(onOpenAttribution: () -> Unit) {
    TextAction(
        onClick = onOpenAttribution,
        modifier = Modifier.testTag(LANGUAGES_ATTRIBUTION_TAG).padding(vertical = LektoSpacing.BetweenRows),
    ) {
        Text(
            text = LocalAppTexts.current[TextKey.ATTRIBUTION],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The honest empty state: no offered languages are known. */
@Composable
private fun EmptyState(available: Boolean) {
    val texts = LocalAppTexts.current
    Text(
        text = if (available) {
            texts[TextKey.LANGUAGES_EMPTY_AVAILABLE]
        } else {
            texts[TextKey.LANGUAGES_EMPTY_UNAVAILABLE]
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = LektoSpacing.BetweenRows),
    )
}

/** The inline fetch progress, so the surface never blocks on the network. */
@Composable
private fun Reloading() {
    Column(Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRelatedLines)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            text = LocalAppTexts.current[TextKey.LOADING_LANGUAGES],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The inline fetch failure, with no dismiss: the **Reload list** action is the way out. */
@Composable
private fun Error(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRelatedLines),
    )
}
