package app.lekto.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The secret input (issue #24, acceptance criterion 4): a value is masked by
 * default — the field's own semantics carry the bullets, not the secret — and
 * the reveal toggle shows it. The same screen is re-proved on the runtime
 * Android uses by the same-named `app/androidUnitTest` twin.
 */
@OptIn(ExperimentalTestApi::class)
class SecretFieldSemanticsTest {

    @Test
    fun aSecretIsMaskedByDefaultAndRevealedOnTheToggle() = runComposeUiTest {
        var value by mutableStateOf("")
        setContent {
            MaterialTheme {
                SecretField(value = value, onValueChange = { typed -> value = typed }, label = "API key")
            }
        }

        onNode(hasSetTextAction()).performTextInput("sk-live-1234")

        // Masked by default: one bullet per character, never the key itself.
        onNode(hasSetTextAction()).assertTextContains("••••••••••••")
        onNodeWithText(SHOW).assertIsDisplayed()

        onNodeWithText(SHOW).performClick()

        // Revealed: the value itself, and the toggle now offers to hide it.
        onNode(hasSetTextAction()).assertTextContains("sk-live-1234")
        onNodeWithText(HIDE).assertIsDisplayed()
    }

    @Test
    fun theTypedSecretIsReportedUnchanged() = runComposeUiTest {
        var value by mutableStateOf("")
        setContent {
            MaterialTheme {
                SecretField(value = value, onValueChange = { typed -> value = typed }, label = "API key")
            }
        }

        onNode(hasSetTextAction()).performTextInput("sk-live-1234")

        assertEquals("sk-live-1234", value)
    }
}
