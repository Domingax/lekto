package app.lekto.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

/**
 * The Android [Pronouncer] on a **simulated Android runtime** (issue #21): the
 * platform `TextToSpeech` is asked whether it has a voice for the book's
 * language, and the answer decides between speaking and an honest message. A
 * language with no installed voice is [SpeechResult.NoVoice], an engine that
 * will not start is [SpeechResult.Unavailable], and neither is a crash.
 *
 * The engine starts asynchronously, so the test completes the init callback the
 * way the platform would; the shadow records the last utterance for the speak
 * assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AndroidPronouncerHostTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a language with an installed voice speaks the word`() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.ENGLISH)
        val pronouncer = AndroidPronouncer(context)
        start()

        val result = pronouncer.speak("lantern", "en")

        assertEquals(SpeechResult.Spoken("lantern"), result)
        assertEquals("lantern", shadowEngine().lastSpokenText)
    }

    @Test
    fun `a language with no installed voice is an honest message`() {
        val pronouncer = AndroidPronouncer(context)
        start()

        assertEquals(SpeechResult.NoVoice("fr"), pronouncer.speak("lanterne", "fr"))
    }

    @Test
    fun `an unknown book language is reported honestly`() {
        val pronouncer = AndroidPronouncer(context)
        start()

        assertEquals(SpeechResult.Unavailable(Pronouncer.UNKNOWN_LANGUAGE), pronouncer.speak("lantern", null))
    }

    @Test
    fun `before the engine starts, speaking says so instead of failing`() {
        val pronouncer = AndroidPronouncer(context)

        assertEquals(SpeechResult.Unavailable(Pronouncer.STARTING), pronouncer.speak("lantern", "en"))
    }

    @Test
    fun `an engine that fails to start reports no engine, not a spinner forever`() {
        val pronouncer = AndroidPronouncer(context)
        start(TextToSpeech.ERROR)

        assertEquals(SpeechResult.Unavailable(Pronouncer.NO_ENGINE), pronouncer.speak("lantern", "en"))
    }

    /** Completes the engine's asynchronous init, which the shadow leaves to the test. */
    private fun start(status: Int = TextToSpeech.SUCCESS) {
        val engine = ShadowTextToSpeech.getLastTextToSpeechInstance()
        Shadow.extract<ShadowTextToSpeech>(engine).onInitListener.onInit(status)
    }

    private fun shadowEngine(): ShadowTextToSpeech = Shadow.extract(ShadowTextToSpeech.getLastTextToSpeechInstance())
}
