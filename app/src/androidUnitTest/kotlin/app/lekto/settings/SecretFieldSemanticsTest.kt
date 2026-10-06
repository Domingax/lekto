package app.lekto.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The secret input on a **simulated Android runtime** (issue #24, acceptance
 * criterion 4): the same-named twin of `app/desktopTest`'s
 * `SecretFieldSemanticsTest`, so the parity rule (issue #73) sees the two lanes
 * together and the masked field is proved on the runtime the app ships on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class SecretFieldSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aSecretIsMaskedByDefaultAndRevealedOnTheToggle() {
        val value = mutableStateOf("")
        render(value)

        compose.onNode(hasSetTextAction()).performTextInput("sk-live-1234")
        compose.onNode(hasSetTextAction()).assertTextContains("••••••••••••")
        compose.onNodeWithText(SHOW).assertIsDisplayed()

        compose.onNodeWithText(SHOW).performClick()

        compose.onNode(hasSetTextAction()).assertTextContains("sk-live-1234")
        compose.onNodeWithText(HIDE).assertIsDisplayed()
    }

    @Test
    fun theTypedSecretIsReportedUnchanged() {
        val value = mutableStateOf("")
        render(value)

        compose.onNode(hasSetTextAction()).performTextInput("sk-live-1234")

        assertEquals("sk-live-1234", value.value)
    }

    /** Renders the field over [value], so the test can read what the field reported back. */
    private fun render(value: MutableState<String>) {
        compose.setContent {
            MaterialTheme {
                SecretField(value = value.value, onValueChange = { typed -> value.value = typed }, label = "API key")
            }
        }
    }
}
