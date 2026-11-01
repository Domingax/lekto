package app.lekto.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.APP_VERSION
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The **About** screen through semantics (issue #214): the version an installed
 * copy reports, the licence it is released under (ADR-0011), the way to the
 * source and the way back, in the reader's **app language**.
 */
@OptIn(ExperimentalTestApi::class)
class AboutScreenSemanticsTest {

    @Test
    fun itNamesTheVersion() = runComposeUiTest {
        about()

        onNodeWithTag(ABOUT_VERSION_TAG).assertTextEquals("Version $APP_VERSION")
    }

    @Test
    fun theVersionIsTheOneItIsGiven() = runComposeUiTest {
        about(version = "9.8.7")

        onNodeWithTag(ABOUT_VERSION_TAG).assertTextEquals("Version 9.8.7")
    }

    @Test
    fun itStatesTheLicence() = runComposeUiTest {
        about()

        onNodeWithText("GNU Affero General Public License", substring = true).assertIsDisplayed()
    }

    @Test
    fun theSourceOpensItsLink() = runComposeUiTest {
        var opened = false
        about(actions = AboutActions(onOpenSource = { opened = true }))

        onNodeWithTag(ABOUT_SOURCE_TAG).performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun theWayBackRaisesItsAction() = runComposeUiTest {
        var back = false
        about(actions = AboutActions(onBack = { back = true }))

        onNodeWithTag(ABOUT_BACK_TAG).performClick()

        assertTrue(back)
    }

    @Test
    fun theWholeScreenRendersInTheAppLanguage() = runComposeUiTest {
        about(texts = appTextsFor("fr"))

        onNodeWithText("À propos").assertIsDisplayed()
        onNodeWithText("Version $APP_VERSION").assertIsDisplayed()
        onNodeWithText("Code source").assertIsDisplayed()
    }

    @Test
    @Suppress("MagicNumber") // The 16 dp gap and the half-dp tolerance are the assertion's own.
    fun theHeaderReachesTheEdgeWhileTheTextKeepsItsMargin() = runComposeUiTest {
        about()

        // The back control sits where a bar's does — 8 dp from the window edge —
        // while the screen's text keeps the 24 dp content margin, so the two are
        // 16 dp apart (issue #245).
        val back = onNodeWithTag(ABOUT_BACK_TAG).getUnclippedBoundsInRoot()
        val version = onNodeWithTag(ABOUT_VERSION_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            abs((version.left - back.left).value - 16f) <= 0.5f,
            "the header's control sits ${version.left - back.left} from the content edge",
        )
    }

    /** Renders the screen in the app theme, sized like a phone. */
    private fun ComposeUiTest.about(
        actions: AboutActions = AboutActions(),
        texts: AppTexts? = null,
        version: String = APP_VERSION,
    ) {
        val screen: @Composable () -> Unit = {
            AboutScreen(
                actions = actions,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
                version = version,
            )
        }
        setContent {
            MaterialTheme {
                if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
            }
        }
    }
}
