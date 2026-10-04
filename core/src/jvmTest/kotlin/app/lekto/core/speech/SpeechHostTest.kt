package app.lekto.core.speech

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The desktop engine's per-OS host (issue #21): each platform's listing is
 * parsed into [SpeechVoice]s, and the Linux host builds the right command for
 * the synthesizer it resolved — speech-dispatcher's `spd-say` when installed,
 * `espeak-ng` otherwise, so a new OS or a changed listing or command shape fails
 * here rather than silently finding no voice. The samples are the real listings'
 * shapes.
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

        test("a dispatcher-preferring Linux host emits the spd-say command") {
            val host = LinuxSpeechHost(noRunner, espeakBinary = "espeak-ng", preferDispatcher = true)

            host.command(SpeechVoice("en", "en"), "lantern") shouldBe
                listOf("spd-say", "-l", "en", "-w", "lantern")
        }

        test("the spd-say command asks for the base language, not the full tag") {
            val host = LinuxSpeechHost(noRunner, espeakBinary = "espeak-ng", preferDispatcher = true)

            host.command(SpeechVoice("en-us", "en-us"), "lantern") shouldBe
                listOf("spd-say", "-l", "en", "-w", "lantern")
        }

        test("a Linux host without the dispatcher emits the espeak command") {
            val host = LinuxSpeechHost(noRunner, espeakBinary = "espeak", preferDispatcher = false)

            host.command(SpeechVoice("fr", "fr"), "lanterne") shouldBe listOf("espeak", "-v", "fr", "lanterne")
        }

        test("the dispatcher-preferring Linux host still lists voices through espeak") {
            val host =
                LinuxSpeechHost(listingRunner(ESPEAK_LISTING), espeakBinary = "espeak-ng", preferDispatcher = true)

            host.voices() shouldBe listOf(SpeechVoice("en", "en"), SpeechVoice("fr", "fr"))
        }

        test("a Linux host with no espeak binary lists no voices") {
            val host = LinuxSpeechHost(noRunner, espeakBinary = null, preferDispatcher = true)

            host.voices() shouldBe emptyList()
        }

        test("linuxHost prefers spd-say when both synthesizers are installed") {
            val host = checkNotNull(SpeechHost.linuxHost(synthesizers(espeak = true, dispatcher = true)))

            host.command(SpeechVoice("fr", "fr"), "lanterne") shouldBe
                listOf("spd-say", "-l", "fr", "-w", "lanterne")
        }

        test("linuxHost falls back to espeak when speech-dispatcher is absent") {
            val host = checkNotNull(SpeechHost.linuxHost(synthesizers(espeak = true, dispatcher = false)))

            host.command(SpeechVoice("fr", "fr"), "lanterne") shouldBe listOf("espeak-ng", "-v", "fr", "lanterne")
        }

        test("linuxHost resolves to no engine when neither synthesizer exists") {
            SpeechHost.linuxHost(synthesizers(espeak = false, dispatcher = false)) shouldBe null
        }

        test("linuxHost with only spd-say speaks but lists no espeak voices") {
            val host = checkNotNull(SpeechHost.linuxHost(synthesizers(espeak = false, dispatcher = true)))

            host.voices() shouldBe emptyList()
            host.command(SpeechVoice("fr", "fr"), "lanterne") shouldBe
                listOf("spd-say", "-l", "fr", "-w", "lanterne")
        }
    })

/** A shared no-op runnable: command building never runs the process it returns. */
private val noRunner: ProcessRunner = ProcessRunner { ProcessResult(0, "") }

/** Answers an `espeak-ng --voices` probe with [listing] so a host can list voices. */
private fun listingRunner(listing: String): ProcessRunner = ProcessRunner { command ->
    if (command.first() == "espeak-ng") ProcessResult(0, listing) else ProcessResult(1, "")
}

/** A machine state: which of the two Linux synthesizers exists, as probes would see. */
private fun synthesizers(espeak: Boolean, dispatcher: Boolean): ProcessRunner = ProcessRunner { command ->
    when {
        command.first() == "spd-say" -> if (dispatcher) ProcessResult(0, "spd-say 0.12.1") else ProcessResult(127, "")
        command.first() == "espeak-ng" -> if (espeak) ProcessResult(0, ESPEAK_LISTING) else ProcessResult(127, "")
        else -> ProcessResult(127, "")
    }
}

private val ESPEAK_LISTING = """
    Pty Language Age/Gender VoiceName          File                 Other Languages
     5  en             --/M      English              (en)
     2  fr             --/M      French               (fr)
""".trimIndent()
