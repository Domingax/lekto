package app.lekto.core.speech

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The desktop engine's per-OS voice listings (issue #21): each platform's
 * listing is parsed into [SpeechVoice]s, so a new OS or a changed listing format
 * fails here rather than silently finding no voice. The samples are the real
 * listings' shapes.
 */
class SpeechHostTest :
    FunSpec({

        test("it parses an espeak-ng voice table into one voice per language") {
            val listing = """
                Pty Language Age/Gender VoiceName          File                 Other Languages
                 5  en             --/M      English              (en)
                 5  en-us          --/M      English (America)    (en-us)
                 2  fr             --/M      French               (fr)
            """.trimIndent()

            parseEspeakVoices(listing) shouldBe listOf(
                SpeechVoice("en", "en"),
                SpeechVoice("en-us", "en-us"),
                SpeechVoice("fr", "fr"),
            )
        }

        test("it parses a macOS say listing, voice names with spaces included") {
            val listing = """
                Alex                en_US    # Most people recognize me by my voice.
                Eddy (English (UK)) en-GB    # Hello, my name is Eddy.
                Amelie              fr-CA    # Bonjour, je m'appelle Amélie.
            """.trimIndent()

            parseSayVoices(listing) shouldBe listOf(
                SpeechVoice("Alex", "en_US"),
                SpeechVoice("Eddy (English (UK))", "en-GB"),
                SpeechVoice("Amelie", "fr-CA"),
            )
        }

        test("it parses a Windows voice listing") {
            val listing = """
                Microsoft David Desktop|en-US
                Microsoft Hortense Desktop|fr-FR
            """.trimIndent()

            parseWindowsVoices(listing) shouldBe listOf(
                SpeechVoice("Microsoft David Desktop", "en-US"),
                SpeechVoice("Microsoft Hortense Desktop", "fr-FR"),
            )
        }
    })
