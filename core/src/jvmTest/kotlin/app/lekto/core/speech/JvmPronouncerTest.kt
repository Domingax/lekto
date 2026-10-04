package app.lekto.core.speech

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.IOException

/**
 * The desktop [Pronouncer] (issue #21): it picks a voice by the book's base
 * language and drives the OS synthesizer as a process. A platform with no engine
 * is [SpeechResult.Unavailable], a language with no installed voice is
 * [SpeechResult.NoVoice], and an engine that fails is an honest failure — never
 * an exception into the reading session.
 */
class JvmPronouncerTest :
    FunSpec({
        test("it speaks a word through a voice for the book's language") {
            val runner = RecordingRunner { ProcessResult(0, "") }
            val pronouncer = JvmPronouncer(runner = runner, host = { FakeHost(listOf(SpeechVoice("Agnes", "en_US"))) })

            pronouncer.speak("lantern", "en") shouldBe SpeechResult.Spoken("lantern")

            runner.commands.single() shouldBe listOf("say", "-v", "Agnes", "lantern")
        }

        test("it resolves a voice by base language, not the full BCP-47 tag") {
            val runner = RecordingRunner { ProcessResult(0, "") }
            val pronouncer = JvmPronouncer(runner = runner, host = { FakeHost(listOf(SpeechVoice("Daniel", "en_GB"))) })

            pronouncer.speak("lantern", "en-US") shouldBe SpeechResult.Spoken("lantern")

            runner.commands.single() shouldBe listOf("say", "-v", "Daniel", "lantern")
        }

        test("it reads the installed voices once, not on every utterance") {
            var listings = 0
            val host = object : SpeechHost {
                override fun voices(): List<SpeechVoice> {
                    listings++
                    return listOf(SpeechVoice("Agnes", "en_US"))
                }

                override fun command(voice: SpeechVoice, text: String): List<String> = listOf("say", voice.id, text)
            }
            val pronouncer = JvmPronouncer(runner = RecordingRunner { ProcessResult(0, "") }, host = { host })

            pronouncer.speak("lantern", "en")
            pronouncer.speak("harbour", "en")

            listings shouldBe 1
        }

        test("a language with no installed voice is a message, not a failure") {
            val runner = RecordingRunner { ProcessResult(0, "") }
            val pronouncer = JvmPronouncer(runner = runner, host = { FakeHost(listOf(SpeechVoice("Agnes", "en_US"))) })

            pronouncer.speak("lanterne", "fr") shouldBe SpeechResult.NoVoice("fr")

            runner.commands shouldBe emptyList()
        }

        test("a platform with no speech engine says so rather than faking a voice") {
            val runner = RecordingRunner { ProcessResult(0, "") }
            val pronouncer = JvmPronouncer(runner = runner, host = { null })

            pronouncer.speak("lantern", "en") shouldBe SpeechResult.Unavailable(Pronouncer.NO_ENGINE)

            runner.commands shouldBe emptyList()
        }

        test("an unknown book language is reported honestly") {
            val runner = RecordingRunner { ProcessResult(0, "") }
            val pronouncer = JvmPronouncer(runner = runner, host = { FakeHost(emptyList()) })

            pronouncer.speak("lantern", null) shouldBe SpeechResult.Unavailable(Pronouncer.UNKNOWN_LANGUAGE)

            runner.commands shouldBe emptyList()
        }

        test("an engine that exits non-zero is an honest failure") {
            val runner = RecordingRunner { ProcessResult(1, "no voice") }
            val pronouncer = JvmPronouncer(runner = runner, host = { FakeHost(listOf(SpeechVoice("Agnes", "en_US"))) })

            pronouncer.speak("lantern", "en") shouldBe SpeechResult.Unavailable(Pronouncer.FAILED)
        }

        test("an engine that cannot start never throws into the reader") {
            val runner = RecordingRunner { throw IOException("say: command not found") }
            val pronouncer = JvmPronouncer(runner = runner, host = { FakeHost(listOf(SpeechVoice("Agnes", "en_US"))) })

            pronouncer.speak("lantern", "en") shouldBe SpeechResult.Unavailable(Pronouncer.FAILED)
        }

        test("an empty word is a no-op, not a failure") {
            val runner = RecordingRunner { ProcessResult(0, "") }
            val pronouncer = JvmPronouncer(runner = runner, host = { FakeHost(emptyList()) })

            pronouncer.speak("   ", null) shouldBe SpeechResult.Spoken("   ")

            runner.commands shouldBe emptyList()
        }
    })

/** A scripted [SpeechHost]: the voices it reports and the command it would run. */
private class FakeHost(private val installed: List<SpeechVoice>) : SpeechHost {

    override fun voices(): List<SpeechVoice> = installed

    override fun command(voice: SpeechVoice, text: String): List<String> = listOf("say", "-v", voice.id, text)
}

/** A [ProcessRunner] that records every command and answers with a scripted result or throws. */
private class RecordingRunner(private val answer: (List<String>) -> ProcessResult) : ProcessRunner {

    val commands: MutableList<List<String>> = mutableListOf()

    override fun run(command: List<String>): ProcessResult {
        commands += command
        return answer(command)
    }
}
