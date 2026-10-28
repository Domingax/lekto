package app.lekto.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The Settings index on a **simulated Android runtime** (issue #214): the
 * same-named twin of `app/desktopTest`'s `SettingsScreenSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. The three named groups, a
 * row per area, each carrying its current value in its own semantics, each
 * opening its screen in one tap, and no control left on the list.
 *
 * It is a deliberate mirror, not a shared body: the desktop lane drives
 * `runComposeUiTest` and this one a Robolectric `createAndroidComposeRule`, and
 * `app` has no test source set both lanes compile, so the two files are kept in
 * step by hand (issue #77).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
@Suppress("TooManyFunctions", "LargeClass") // One screen, one test per row; splitting hides the index.
class SettingsScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theIndexIsThreeNamedGroups() {
        render()

        compose.onNodeWithText("Reading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Data").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Application").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun everyAreaIsReachableByNameInOneTap() {
        var appearance = false
        var appLanguage = false
        var languages = false
        var provider = false
        var vault = false
        var attribution = false
        var about = false
        render(
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

        compose.onNodeWithTag(APPEARANCE_LINK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(APP_LANGUAGE_LINK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(LANGUAGES_LINK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(PROVIDER_LINK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(VAULT_LINK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(ATTRIBUTION_LINK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(ABOUT_LINK_TAG).performScrollTo().performClick()

        assertTrue("Appearance opens its screen", appearance)
        assertTrue("App language opens its screen", appLanguage)
        assertTrue("Reading languages opens the Languages surface", languages)
        assertTrue("LLM provider opens its screen", provider)
        assertTrue("Vault and sync opens its screen", vault)
        assertTrue("Attribution opens its screen", attribution)
        assertTrue("About opens its screen", about)
    }

    @Test
    fun eachRowCarriesItsCurrentValue() {
        render(
            appLanguage = "fr",
            appTheme = AppTheme.SEPIA,
            readingLanguages = listOf("en", "es"),
            provider = AnthropicConfigured,
            sync = syncedTwoMinutesAgo(),
        )

        compose.onNodeWithTag(APPEARANCE_LINK_TAG).assertTextContains("Sepia")
        compose.onNodeWithTag(APP_LANGUAGE_LINK_TAG).assertTextContains("French")
        compose.onNodeWithTag(LANGUAGES_LINK_TAG).assertTextContains("English, Spanish")
        compose.onNodeWithTag(PROVIDER_LINK_TAG).assertTextContains("Anthropic, configured")
        compose.onNodeWithTag(VAULT_LINK_TAG).assertTextContains("Synced 2 minutes ago.")
        compose.onNodeWithTag(ABOUT_LINK_TAG).assertTextContains("Version $APP_VERSION")
    }

    @Test
    fun aRowWithNothingToChangeShowsNoValue() {
        render()

        // Attribution is a screen of text, not a setting with a state.
        compose.onNodeWithTag(ATTRIBUTION_LINK_TAG).assertTextEquals("Attribution")
    }

    @Test
    fun theIndexHoldsNoControlsOfItsOwn() {
        render()

        // Every control is a screen away: the row is the only affordance left
        // (docs/design.md §7.5, issue #214).
        compose.onNodeWithTag(PROVIDER_PICKER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SYNC_HOST_FIELD_TAG).assertDoesNotExist()
        compose.onNodeWithText("Export vault").assertDoesNotExist()
        compose.onNodeWithText("Import vault").assertDoesNotExist()
        compose.onNodeWithText("Open").assertDoesNotExist()
    }

    @Test
    fun aFailedRunIsNotShownAsSynced() {
        render(
            sync = syncedTwoMinutesAgo().copy(
                lastSync = SyncResult.Failure(AppMessage.Text(TextKey.MESSAGE_SYNC_RUN_FAILED)),
            ),
        )

        // The time a run took is not the same as it succeeding: the row keeps the
        // status, and the screen one tap away carries the failure (issue #214).
        compose.onNodeWithTag(VAULT_LINK_TAG).assertTextContains("Sync is on.")
    }

    @Test
    fun theReadingLanguagesRowSaysNoneYetBeforeAnyBook() {
        render(readingLanguages = emptyList())

        compose.onNodeWithTag(LANGUAGES_LINK_TAG).assertTextContains("None yet")
    }

    @Test
    fun anUnwiredAreaSaysSoRatherThanNamingAFakeValue() {
        render(provider = ProviderUiState(), sync = SyncUiState())

        compose.onNodeWithTag(PROVIDER_LINK_TAG).assertTextContains("Not available")
        compose.onNodeWithTag(VAULT_LINK_TAG).assertTextContains("Not available")
    }

    @Test
    fun theWholeIndexRendersInTheAppLanguage() {
        // The reader's **app language** drives every text on the screen (ADR-0034):
        // with French provided, the groups and the rows read in French.
        render(appLanguage = "fr", appTheme = AppTheme.SEPIA, texts = appTextsFor("fr"))

        compose.onNodeWithText("Réglages").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lecture").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Données").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Application").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(APPEARANCE_LINK_TAG).assertTextContains("Sépia")
        compose.onNodeWithTag(APP_LANGUAGE_LINK_TAG).assertTextContains("français")
    }

    @Test
    fun theStateCarriesTheReadingLanguagesTheBooksWereReadIn() {
        render(readingLanguages = listOf("en", "de"))

        // The state is the seam the composition root fills; the screen names each
        // language the way the rest of the app does.
        compose.onNodeWithTag(LANGUAGES_LINK_TAG).assertTextContains("English, German")
    }

    /** Renders the settings index in the app theme, sized like a phone. */
    @Suppress("LongParameterList") // One test helper mirroring the screen's state bundles.
    private fun render(
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
        compose.setContent {
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
