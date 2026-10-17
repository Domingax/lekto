package app.lekto.translation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import app.lekto.AppEnvironment
import app.lekto.language.LanguageSettingsController
import app.lekto.reader.PhraseSelection
import kotlinx.coroutines.CoroutineScope

/**
 * The phrase-translation state holder the reader owns, wired from the
 * [AppEnvironment]'s provider services, or a no-op one when none is wired
 * (issue #89). Kept beside the controller so the reader destination only names
 * the bundle rather than rebuilding it. The [languageSettings]' **Translation
 * language** (ADR-0034) is read at the moment of a request, so the prompt asks for
 * the language the reader wants.
 */
@Composable
internal fun rememberTranslation(
    environment: AppEnvironment,
    scope: CoroutineScope,
    languageSettings: LanguageSettingsController?,
): Translation {
    val controller = remember(environment.llm, environment.secrets, environment.dispatcher, scope) {
        val llm = environment.llm
        val secrets = environment.secrets
        if (llm != null && secrets != null) {
            PhraseTranslationController(llm.settings, secrets, llm.client, environment.dispatcher, scope)
        } else {
            null
        }
    }
    val state = controller?.state?.collectAsState()?.value ?: PhraseTranslationUiState()
    return remember(controller, state, languageSettings) { Translation(controller, state, languageSettings) }
}

/**
 * The phrase panel's translation state and the two transitions it drives (issue
 * #89), bundled so the reader's lookup value carries one field rather than a
 * controller, its state and two callbacks.
 */
internal class Translation(
    private val controller: PhraseTranslationController?,
    val state: PhraseTranslationUiState,
    private val languageSettings: LanguageSettingsController?,
) {

    /** Starts translating [phrase] against the connected provider, into the reader's translation language. */
    fun start(phrase: PhraseSelection) {
        controller?.translate(phrase.text, phrase.sentence, languageSettings?.translationLanguage)
    }

    /** Stops the stream and returns the panel to its resting state. */
    fun dismiss() {
        controller?.dismiss()
    }
}
