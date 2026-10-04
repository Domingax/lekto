package app.lekto.core.speech

/**
 * The honest outcomes of asking the platform to pronounce a word (issue #21).
 * Pronunciation is never stored — it is an action with a result — and a language
 * with no installed voice is a message, not a failure.
 */
sealed interface SpeechResult {

    /** The platform accepted [text] and is speaking it, or is about to. */
    data class Spoken(val text: String) : SpeechResult

    /** The platform has no voice for [language]; the reader is told, not shown an error. */
    data class NoVoice(val language: String?) : SpeechResult

    /** No speech engine is available, or the utterance could not start. */
    data class Unavailable(val message: String) : SpeechResult
}

/**
 * The speech seam (issue #21): speak [text] aloud in BCP-47 [language], reporting
 * an honest outcome when the platform has no engine or no voice for the language.
 *
 * Android backs it with `android.speech.tts.TextToSpeech`; desktop with a JVM
 * binding to the operating system's synthesizer (ADR-0019). The call is
 * **blocking** — on desktop it runs a process, on Android it may touch the
 * engine — so callers run it off the UI thread.
 */
fun interface Pronouncer {
    /** Speaks [text] in [language], or reports why it cannot. */
    fun speak(text: String, language: String?): SpeechResult

    companion object {
        /** Shown when the book carries no language, so no voice can be chosen. */
        const val UNKNOWN_LANGUAGE: String =
            "This book's language isn't known, so there's no voice to use."

        /** Shown when the device has no speech engine at all. */
        const val NO_ENGINE: String = "No speech engine is available on this device."

        /** Shown when the engine could not start the utterance. */
        const val FAILED: String = "The speech engine couldn't pronounce that word."

        /** Shown on Android while the engine is still starting up. */
        const val STARTING: String = "The speech engine is still starting up. Try again in a moment."
    }
}
