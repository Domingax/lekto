package app.lekto.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import app.lekto.core.text.baseLanguage
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * The Android [Pronouncer] (issue #21): the platform's `TextToSpeech` engine,
 * feature-detected per language so a book whose language has no installed voice
 * gets an honest message rather than silence or a crash. It is the Android twin
 * of the desktop [JvmPronouncer]; both answer through the same [Pronouncer] seam.
 *
 * The engine starts asynchronously, so [speak] reports [Pronouncer.STARTING]
 * until the platform has answered — the reader taps a word seconds after the app
 * opens, by which point it is ready. An engine that never starts is
 * [Pronouncer.NO_ENGINE], not a spinner forever. [shutdown] releases it when the
 * host's lifecycle ends.
 */
class AndroidPronouncer(context: Context) : Pronouncer {

    private val initStatus = AtomicReference<Int?>(null)

    private val engine = TextToSpeech(context.applicationContext) { status -> initStatus.set(status) }

    override fun speak(text: String, language: String?): SpeechResult {
        val word = text.trim()
        val code = language?.let(::baseLanguage)
        val status = initStatus.get()
        return when {
            word.isEmpty() -> SpeechResult.Spoken(text)
            code == null -> SpeechResult.Unavailable(Pronouncer.UNKNOWN_LANGUAGE)
            status == null -> SpeechResult.Unavailable(Pronouncer.STARTING)
            status != TextToSpeech.SUCCESS -> SpeechResult.Unavailable(Pronouncer.NO_ENGINE)
            else -> pronounce(word, language, code)
        }
    }

    /** Speaks [word] in [code]'s locale, or reports that language has no installed voice. */
    private fun pronounce(word: String, language: String?, code: String): SpeechResult {
        val locale = Locale.forLanguageTag(code)
        if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) {
            return SpeechResult.NoVoice(language)
        }
        engine.language = locale
        val spoken = engine.speak(word, TextToSpeech.QUEUE_FLUSH, null, word)
        return if (spoken == TextToSpeech.SUCCESS) {
            SpeechResult.Spoken(word)
        } else {
            SpeechResult.Unavailable(Pronouncer.FAILED)
        }
    }

    /** Stops the engine and releases its resources; the host calls this when it closes. */
    fun shutdown() {
        engine.shutdown()
    }
}
