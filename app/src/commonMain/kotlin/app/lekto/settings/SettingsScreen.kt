package app.lekto.settings

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import app.lekto.APP_VERSION
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor
import app.lekto.ui.plainClickable
import app.lekto.ui.rowHover
import kotlin.time.Clock
import kotlin.time.Instant

/** The test tag on the way to the **Languages** surface, so a UI test can click exactly it. */
internal const val LANGUAGES_LINK_TAG: String = "settings-languages"

/** The test tag on the way to the **Appearance** screen, so a UI test can click exactly it. */
internal const val APPEARANCE_LINK_TAG: String = "settings-appearance"

/** The test tag on the way to the **App language** screen, so a UI test can click exactly it. */
internal const val APP_LANGUAGE_LINK_TAG: String = "settings-app-language"

/** The test tag on the way to the **LLM provider** screen, so a UI test can click exactly it. */
internal const val PROVIDER_LINK_TAG: String = "settings-provider"

/** The test tag on the way to the **Vault and sync** screen, so a UI test can click exactly it. */
internal const val VAULT_LINK_TAG: String = "settings-vault"

/** The test tag on the way to the attribution screen, so a UI test can click exactly it. */
internal const val ATTRIBUTION_LINK_TAG: String = "settings-attribution"

/** The test tag on the way to the **About** screen, so a UI test can click exactly it. */
internal const val ABOUT_LINK_TAG: String = "settings-about"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar (issue #185). */
internal const val SETTINGS_TITLE_TAG: String = "settings-title"

/**
 * The settings screen as the index `docs/design.md` §7.5 asks for (issue #214):
 * a row per area, its current value under it, grouped in the sentence-case labels
 * **Reading**, **Data** and **Application**.
 *
 * The rows carry no controls. Every value that needs changing lives a screen
 * away — **Appearance** (ADR-0036), **App language** (ADR-0034), **Languages**,
 * the **LLM provider** and **Vault and sync** screens (issue #213), the
 * attribution screen and **About** — so a reader changes a setting where it is
 * explained, and the index stays a map.
 *
 * [now] is the clock the **Vault and sync** row's "Synced 2 minutes ago" is read
 * against; it is a parameter because a hard-coded clock cannot be driven by a test
 * (docs/testing.md, "Deterministic seams").
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions = SettingsActions(),
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenBlocks),
    ) {
        Header()
        ReadingGroup(state, actions)
        DataGroup(state, actions, now)
        ApplicationGroup(actions)
    }
}

/** The **Reading** group: how the app looks, the interface language, and what the reader reads. */
@Composable
private fun ReadingGroup(state: SettingsUiState, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    SettingsGroup(
        label = texts[TextKey.SETTINGS_GROUP_READING],
        rows = listOf(
            IndexEntry(
                tag = APPEARANCE_LINK_TAG,
                title = texts[TextKey.SECTION_APPEARANCE],
                value = texts.themeName(state.appTheme),
                onClick = actions.onOpenAppearance,
            ),
            IndexEntry(
                tag = APP_LANGUAGE_LINK_TAG,
                title = texts[TextKey.SECTION_APP_LANGUAGE],
                value = texts.languageName(state.appLanguage),
                onClick = actions.onOpenAppLanguage,
            ),
            IndexEntry(
                tag = LANGUAGES_LINK_TAG,
                title = texts[TextKey.SETTINGS_READING_LANGUAGES],
                value = readingLanguagesValue(state),
                onClick = actions.onOpenLanguages,
            ),
        ),
    )
}

/** The **Data** group: what leaves this device, through the provider or through sync. */
@Composable
private fun DataGroup(state: SettingsUiState, actions: SettingsActions, now: Instant) {
    val texts = LocalAppTexts.current
    SettingsGroup(
        label = texts[TextKey.SETTINGS_GROUP_DATA],
        rows = listOf(
            IndexEntry(
                tag = PROVIDER_LINK_TAG,
                title = texts[TextKey.SECTION_LLM_PROVIDER],
                value = providerValue(state.provider),
                onClick = actions.onOpenProvider,
            ),
            IndexEntry(
                tag = VAULT_LINK_TAG,
                title = texts[TextKey.VAULT_AND_SYNC],
                value = syncValue(state.sync, now),
                onClick = actions.onOpenVault,
            ),
        ),
    )
}

/** The **Application** group: the licences and the build the reader is holding. */
@Composable
private fun ApplicationGroup(actions: SettingsActions) {
    val texts = LocalAppTexts.current
    SettingsGroup(
        label = texts[TextKey.SETTINGS_GROUP_APPLICATION],
        rows = listOf(
            IndexEntry(
                tag = ATTRIBUTION_LINK_TAG,
                title = texts[TextKey.ATTRIBUTION],
                value = null,
                onClick = actions.onOpenAttribution,
            ),
            IndexEntry(
                tag = ABOUT_LINK_TAG,
                title = texts[TextKey.ABOUT],
                value = texts.message(AppMessage.of(TextKey.ABOUT_VERSION, APP_VERSION)),
                onClick = actions.onOpenAbout,
            ),
        ),
    )
}

/** One row of the index: its area, its current value when it has one, and what it opens. */
private data class IndexEntry(val tag: String, val title: String, val value: String?, val onClick: () -> Unit)

/**
 * One group of the index: its small muted label, then its rows parted by a
 * hairline — never one after the last, which would close the group against the
 * next label for no reason (docs/design.md §7.5).
 */
@Composable
private fun SettingsGroup(label: String, rows: List<IndexEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
        FormGroupLabel(label)
        Column {
            rows.forEachIndexed { index, row ->
                IndexRow(row)
                if (index != rows.lastIndex) FormHairline()
            }
        }
    }
}

/** The **Reading languages** row's value: the reader's languages, most-read first. */
@Composable
private fun readingLanguagesValue(state: SettingsUiState): String {
    val texts = LocalAppTexts.current
    return if (state.readingLanguages.isEmpty()) {
        texts[TextKey.SETTINGS_NO_READING_LANGUAGES]
    } else {
        state.readingLanguages.joinToString(", ") { language -> texts.languageName(language) }
    }
}

/** The **LLM provider** row's value: the chosen preset, marked when a key is stored. */
@Composable
private fun providerValue(state: ProviderUiState): String {
    val texts = LocalAppTexts.current
    val chosen = state.selectedProvider
    return when {
        !state.available -> texts[TextKey.SETTINGS_NOT_AVAILABLE]
        chosen == null -> texts[TextKey.CHOOSE_A_PROVIDER]
        state.hasStoredKey -> texts.message(AppMessage.of(TextKey.PROVIDER_CONFIGURED, chosen.label))
        else -> chosen.label
    }
}

/** The **Vault and sync** row's value: when it last ran, or the status at rest. */
@Composable
private fun syncValue(state: SyncUiState, now: Instant): String {
    val texts = LocalAppTexts.current
    if (!state.available) return texts[TextKey.SETTINGS_NOT_AVAILABLE]
    // A failed run is not a sync that happened, so the row keeps the status and
    // leaves the reason to the screen (issue #214).
    val lastRunAt = state.lastRunAt?.takeUnless { state.lastSync is SyncResult.Failure }
    return if (lastRunAt == null) texts.message(state.status) else texts.message(lastSyncLabel(now, lastRunAt))
}

/**
 * One row of the index (docs/design.md §7.5): the area's name, its current value
 * in the muted ink, and the chevron that says a screen opens.
 *
 * The row merges its children's semantics, so a screen reader announces the name
 * and the value as one button, and a test can assert both on the row's own tag.
 * The chevron carries no description: the row's name already names it (§9).
 */
@Composable
private fun IndexRow(row: IndexEntry) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LektoSpacing.TouchTarget)
            .rowHover(hovered, MaterialTheme.colorScheme.onBackground)
            .handCursor()
            .hoverable(interaction)
            .plainClickable(role = Role.Button, onClick = row.onClick)
            .semantics(mergeDescendants = true) {}
            .testTag(row.tag)
            .padding(vertical = LektoSpacing.BetweenRows),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        ) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            if (row.value != null) {
                Text(
                    row.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Chevron()
    }
}

/** The screen's title alone: the shell's bar and the back stack replace its old way back (issue #185). */
@Composable
private fun Header() {
    Text(
        text = LocalAppTexts.current[TextKey.SETTINGS],
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.testTag(SETTINGS_TITLE_TAG),
    )
}
