package app.lekto.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.APP_VERSION
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.sync.SyncSettings
import app.lekto.core.theme.AppTheme
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.text.appTextsFor
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The Settings index through semantics (issue #214; docs/design.md §7.5): the
 * three named groups — **Reading**, **Data**, **Application** — a row per area,
 * each carrying its current value in its own semantics and opening its screen in
 * one tap, and no control left on the list itself.
 *
 * The forms' own suites prove the fields the two secret screens took with them
 * (issue #213), and the Appearance, App language and About suites prove the
 * screens the rows point at.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass") // One screen, one test per row; splitting hides the index.
class SettingsScreenSemanticsTest {

    @Test
    fun theIndexIsThreeNamedGroups() = runComposeUiTest {
        settings()

        onNodeWithText("Reading").performScrollTo().assertIsDisplayed()
        onNodeWithText("Data").performScrollTo().assertIsDisplayed()
        onNodeWithText("Application").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun everyAreaIsReachableByNameInOneTap() = runComposeUiTest {
        var appearance = false
        var appLanguage = false
        var languages = false
        var provider = false
        var vault = false
        var attribution = false
        var about = false
        settings(
            actions = SettingsActions(
                onOpenAppearance = { appearance = true },
                onOpenAppLanguage = { appLanguage = true },
                onOpenLanguages = { languages = true },
                onOpenProvider = { provider = true },
                onOpenVault = { vault = true },
                onOpenAttribution = { attribution = true },
                onOpenAbout = { about = true },
            ),
        )

        onNodeWithTag(APPEARANCE_LINK_TAG).performScrollTo().performClick()
        onNodeWithTag(APP_LANGUAGE_LINK_TAG).performScrollTo().performClick()
        onNodeWithTag(LANGUAGES_LINK_TAG).performScrollTo().performClick()
        onNodeWithTag(PROVIDER_LINK_TAG).performScrollTo().performClick()
        onNodeWithTag(VAULT_LINK_TAG).performScrollTo().performClick()
        onNodeWithTag(ATTRIBUTION_LINK_TAG).performScrollTo().performClick()
        onNodeWithTag(ABOUT_LINK_TAG).performScrollTo().performClick()

        assertTrue(appearance, "Appearance opens its screen")
        assertTrue(appLanguage, "App language opens its screen")
        assertTrue(languages, "Reading languages opens the Languages surface")
        assertTrue(provider, "LLM provider opens its screen")
        assertTrue(vault, "Vault and sync opens its screen")
        assertTrue(attribution, "Attribution opens its screen")
        assertTrue(about, "About opens its screen")
    }

    @Test
    fun eachRowCarriesItsCurrentValue() = runComposeUiTest {
        settings(
            appLanguage = "fr",
            appTheme = AppTheme.SEPIA,
            readingLanguages = listOf("en", "es"),
            provider = AnthropicConfigured,
            sync = syncedTwoMinutesAgo(),
        )

        onNodeWithTag(APPEARANCE_LINK_TAG).assertTextContains("Sepia")
        onNodeWithTag(APP_LANGUAGE_LINK_TAG).assertTextContains("French")
        onNodeWithTag(LANGUAGES_LINK_TAG).assertTextContains("English, Spanish")
        onNodeWithTag(PROVIDER_LINK_TAG).assertTextContains("Anthropic, configured")
        onNodeWithTag(VAULT_LINK_TAG).assertTextContains("Synced 2 minutes ago.")
        onNodeWithTag(ABOUT_LINK_TAG).assertTextContains("Version $APP_VERSION")
    }

    @Test
    fun aRowWithNothingToChangeShowsNoValue() = runComposeUiTest {
        settings()

        // Attribution is a screen of text, not a setting with a state.
        onNodeWithTag(ATTRIBUTION_LINK_TAG).assertTextEquals("Attribution")
    }

    @Test
    fun theIndexHoldsNoControlsOfItsOwn() = runComposeUiTest {
        settings()

        // Every control is a screen away: the row is the only affordance left
        // (docs/design.md §7.5, issue #214).
        onNodeWithTag(PROVIDER_PICKER_TAG).assertDoesNotExist()
        onNodeWithTag(SYNC_HOST_FIELD_TAG).assertDoesNotExist()
        onNodeWithText("Export vault").assertDoesNotExist()
        onNodeWithText("Import vault").assertDoesNotExist()
        onNodeWithText("Open").assertDoesNotExist()
    }

    @Test
    fun aFailedRunIsNotShownAsSynced() = runComposeUiTest {
        settings(
            sync = syncedTwoMinutesAgo().copy(
                lastSync = SyncResult.Failure(AppMessage.Text(TextKey.MESSAGE_SYNC_RUN_FAILED)),
            ),
        )

        // The time a run took is not the same as it succeeding: the row keeps the
        // status, and the screen one tap away carries the failure (issue #214).
        onNodeWithTag(VAULT_LINK_TAG).assertTextContains("Sync is on.")
    }

    @Test
    fun theReadingLanguagesRowSaysNoneYetBeforeAnyBook() = runComposeUiTest {
        settings(readingLanguages = emptyList())

        onNodeWithTag(LANGUAGES_LINK_TAG).assertTextContains("None yet")
    }

    @Test
    fun anUnwiredAreaSaysSoRatherThanNamingAFakeValue() = runComposeUiTest {
        settings(provider = ProviderUiState(), sync = SyncUiState())

        onNodeWithTag(PROVIDER_LINK_TAG).assertTextContains("Not available")
        onNodeWithTag(VAULT_LINK_TAG).assertTextContains("Not available")
    }

    @Test
    fun theWholeIndexRendersInTheAppLanguage() = runComposeUiTest {
        // The reader's **app language** drives every text on the screen (ADR-0034):
        // with French provided, the groups and the rows read in French.
        settings(appLanguage = "fr", appTheme = AppTheme.SEPIA, texts = appTextsFor("fr"))

        onNodeWithText("Réglages").performScrollTo().assertIsDisplayed()
        onNodeWithText("Lecture").performScrollTo().assertIsDisplayed()
        onNodeWithText("Données").performScrollTo().assertIsDisplayed()
        onNodeWithText("Application").performScrollTo().assertIsDisplayed()
        onNodeWithTag(APPEARANCE_LINK_TAG).assertTextContains("Sépia")
        onNodeWithTag(APP_LANGUAGE_LINK_TAG).assertTextContains("français")
    }

    @Test
    fun theStateCarriesTheReadingLanguagesTheBooksWereReadIn() = runComposeUiTest {
        settings(readingLanguages = listOf("en", "de"))

        // The state is the seam the composition root fills; the screen names each
        // language the way the rest of the app does.
        onNodeWithTag(LANGUAGES_LINK_TAG).assertTextContains("English, German")
    }

    /** Renders the settings index in the app theme, sized like a phone. */
    @Suppress("LongParameterList") // One test helper mirroring the screen's state bundles.
    private fun ComposeUiTest.settings(
        appLanguage: String = "en",
        appTheme: AppTheme = AppTheme.SYSTEM,
        readingLanguages: List<String> = emptyList(),
        provider: ProviderUiState = ProviderUiState(),
        sync: SyncUiState = SyncUiState(),
        actions: SettingsActions = SettingsActions(),
        texts: AppTexts? = null,
        now: Instant = Instant.fromEpochMilliseconds(TWO_MINUTES_MILLIS),
    ) {
        val screen: @Composable () -> Unit = {
            SettingsScreen(
                state = SettingsUiState(
                    appLanguage = appLanguage,
                    appTheme = appTheme,
                    readingLanguages = readingLanguages,
                    provider = provider,
                    sync = sync,
                ),
                actions = actions,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
                now = now,
            )
        }
        setContent {
            MaterialTheme {
                if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
            }
        }
    }
}

/** A provider with a stored key, so the row reads "Anthropic · configured". */
private val AnthropicConfigured = ProviderUiState(
    available = true,
    providers = LlmProvider.entries,
    config = LlmProviderConfig(provider = LlmProvider.ANTHROPIC),
    hasStoredKey = true,
    providerChosen = true,
)

/** A sync that last ran two minutes before the clock the test passes the screen. */
private fun syncedTwoMinutesAgo(): SyncUiState = SyncUiState(
    available = true,
    config = SyncSettings(serverUrl = "https://dav.example.org", username = "reader", enabled = true),
    hasStoredPassword = true,
    lastRunAt = TWO_MINUTES_MILLIS - 2.minutes.inWholeMilliseconds,
)

/** The test clock's reading, two minutes after the last run above. */
private const val TWO_MINUTES_MILLIS: Long = 1_700_000_000_000L
