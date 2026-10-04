package app.lekto.core.speech

import app.lekto.core.text.baseLanguage

/**
 * One voice the operating system's synthesizer offers: its [id] — the value the
 * engine's command takes — and the [language] it speaks.
 */
data class SpeechVoice(val id: String, val language: String)

/** A finished process: its [exitCode] and what it wrote to standard output. */
data class ProcessResult(val exitCode: Int, val output: String)

/** Runs a command to completion; the process seam a test injects. */
fun interface ProcessRunner {

    /** Runs [command] to completion and returns its [exitCode] and standard output. */
    fun run(command: List<String>): ProcessResult
}

/** The production [ProcessRunner]: starts the command and drains its output before waiting. */
object SystemProcessRunner : ProcessRunner {

    override fun run(command: List<String>): ProcessResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        // Drain the output before waiting, or a chatty engine fills the pipe and deadlocks.
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return ProcessResult(process.waitFor(), output)
    }
}

/**
 * The desktop [Pronouncer] (issue #21): a JVM binding to the operating system's
 * speech synthesizer, driven as a short-lived process (`say` on macOS,
 * `spd-say` on Linux with an `espeak-ng` fallback, `System.Speech` on Windows).
 * Lekto ships no engine and bundles no voices (ADR-0019): the OS owns synthesis,
 * so a platform with no engine is [SpeechResult.Unavailable] and a language with
 * no installed voice is [SpeechResult.NoVoice] — never a crash.
 *
 * The platform's synthesizer is resolved once, lazily, at the first utterance,
 * and its voices are read then and reused. [runner] and [host] are the seams a
 * test injects; production uses the no-argument constructor. [speak] blocks while
 * the process runs, so the application runs it off the UI thread.
 */
class JvmPronouncer internal constructor(
    private val runner: ProcessRunner,
    private val host: () -> SpeechHost? = { SpeechHost.current(runner) },
) : Pronouncer {

    /** The production wiring: the current OS's synthesizer, run as a process. */
    constructor() : this(SystemProcessRunner)

    private val resolved: SpeechHost? by lazy { host() }

    private val installedVoices: List<SpeechVoice> by lazy { resolved?.voices().orEmpty() }

    override fun speak(text: String, language: String?): SpeechResult {
        val word = text.trim()
        val code = language?.let(::baseLanguage)
        return when {
            word.isEmpty() -> SpeechResult.Spoken(text)
            code == null -> SpeechResult.Unavailable(Pronouncer.UNKNOWN_LANGUAGE)
            resolved == null -> SpeechResult.Unavailable(Pronouncer.NO_ENGINE)
            else -> pronounce(word, text, language, code)
        }
    }

    /** Speaks the trimmed [word] with a voice for [code], or reports the language has no voice. */
    private fun pronounce(word: String, text: String, language: String?, code: String): SpeechResult {
        val voice = installedVoices.firstOrNull { baseLanguage(it.language) == code }
            ?: return SpeechResult.NoVoice(language)
        return run(resolved?.command(voice, word).orEmpty(), text)
    }

    /** Runs [command], reporting [text] as spoken on success and an honest failure otherwise. */
    private fun run(command: List<String>, text: String): SpeechResult {
        if (command.isEmpty()) return SpeechResult.Unavailable(Pronouncer.NO_ENGINE)
        val exit = runCatching { runner.run(command).exitCode }.getOrDefault(MISSING_ENGINE_EXIT)
        return if (exit == 0) SpeechResult.Spoken(text) else SpeechResult.Unavailable(Pronouncer.FAILED)
    }

    private companion object {
        /** The exit a command that never started is treated as having: a failure, not a voice. */
        const val MISSING_ENGINE_EXIT: Int = -1
    }
}
