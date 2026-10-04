package app.lekto

import app.lekto.core.speech.Pronouncer
import app.lekto.core.speech.SpeechResult
import app.lekto.testkit.FakePronouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reader's pronunciation handler (issue #21): it speaks the word off the UI
 * thread and carries the honest [SpeechResult] back, or reports that no engine
 * is wired, so the panel can show the message rather than nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpeakHandlerTest {

    @Test
    fun `without a pronouncer it reports no engine rather than doing nothing`() {
        var result: SpeechResult? = null

        val speak = speakHandler(null, CoroutineScope(UnconfinedTestDispatcher()), UnconfinedTestDispatcher()) {
            result = it
        }

        speak("lantern", "en")

        assertEquals(SpeechResult.Unavailable(Pronouncer.NO_ENGINE), result)
    }

    @Test
    fun `with a pronouncer it speaks off the UI thread and carries the result back`() {
        val pronouncer = FakePronouncer(SpeechResult.NoVoice("fr"))
        var result: SpeechResult? = null

        val speak = speakHandler(
            pronouncer,
            CoroutineScope(UnconfinedTestDispatcher()),
            UnconfinedTestDispatcher(),
        ) { result = it }

        speak("lanterne", "fr")

        assertEquals(SpeechResult.NoVoice("fr"), result)
        assertEquals(listOf<Pair<String, String?>>("lanterne" to "fr"), pronouncer.utterances)
    }
}
