package app.lekto.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.lekto.APP_VERSION
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The **About** screen on a **simulated Android runtime** (issue #214): the
 * same-named twin of `app/desktopTest`'s `AboutScreenSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AboutScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun itNamesTheVersion() {
        render()

        compose.onNodeWithTag(ABOUT_VERSION_TAG).assertTextEquals("Version $APP_VERSION")
    }

    @Test
    fun theVersionIsTheOneItIsGiven() {
        render(version = "9.8.7")

        compose.onNodeWithTag(ABOUT_VERSION_TAG).assertTextEquals("Version 9.8.7")
    }

    @Test
    fun itStatesTheLicence() {
        render()

        compose.onNodeWithText("GNU Affero General Public License", substring = true).assertIsDisplayed()
    }

    @Test
    fun theSourceOpensItsLink() {
        var opened = false
        render(actions = AboutActions(onOpenSource = { opened = true }))

        compose.onNodeWithTag(ABOUT_SOURCE_TAG).performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun theWayBackRaisesItsAction() {
        var back = false
        render(actions = AboutActions(onBack = { back = true }))

        compose.onNodeWithTag(ABOUT_BACK_TAG).performClick()

        assertTrue(back)
    }

    @Test
    fun theWholeScreenRendersInTheAppLanguage() {
        render(texts = appTextsFor("fr"))

        compose.onNodeWithText("À propos").assertIsDisplayed()
        compose.onNodeWithText("Version $APP_VERSION").assertIsDisplayed()
        compose.onNodeWithText("Code source").assertIsDisplayed()
    }

    @Test
    fun theHeaderReachesTheEdgeWhileTheTextKeepsItsMargin() {
        render()

        // The back control sits where a bar's does — 8 dp from the window edge —
        // while the screen's text keeps the 24 dp content margin, so the two are
        // 16 dp apart (issue #245).
        val back = compose.onNodeWithTag(ABOUT_BACK_TAG).getUnclippedBoundsInRoot()
        val version = compose.onNodeWithTag(ABOUT_VERSION_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            "the header's control sits ${version.left - back.left} from the content edge",
            abs((version.left - back.left).value - 16f) <= 0.5f,
        )
    }

    /** Renders the screen in the app theme, sized like a phone. */
    private fun render(actions: AboutActions = AboutActions(), texts: AppTexts? = null, version: String = APP_VERSION) {
        val screen: @Composable () -> Unit = {
            AboutScreen(
                actions = actions,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
                version = version,
            )
        }
        compose.setContent {
            MaterialTheme {
                if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
            }
        }
    }
}
