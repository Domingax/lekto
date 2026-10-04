package app.lekto.testkit

import app.lekto.core.speech.Pronouncer
import app.lekto.core.speech.SpeechResult

/**
 * An in-memory [Pronouncer] for tests (issue #21): it records what it was asked
 * to speak and returns a scripted [result], so a UI test can prove the lookup
 * panel reached the seam without a real engine installed.
 */
class FakePronouncer(private val result: SpeechResult = SpeechResult.Spoken("")) : Pronouncer {

    /** The `(text, language)` pairs [speak] was called with, in order. */
    val utterances: MutableList<Pair<String, String?>> = mutableListOf()

    override fun speak(text: String, language: String?): SpeechResult {
        utterances += text to language
        return result
    }
}
