package app.lekto.core.speech

import app.lekto.core.text.baseLanguage

/**
 * The operating system's synthesizer behind [JvmPronouncer]: it lists the
 * voices the platform has and builds the command that speaks with one. One host
 * per OS; a platform with no synthesizer resolves to none, so [JvmPronouncer]
 * reports "no engine" rather than crashing (issue #21).
 *
 * The host is internal because it is the desktop engine's implementation detail:
 * [JvmPronouncer] is the seam the application sees. The `internal` visibility
 * also lets `jvmTest` inject a host and a fake process runner, so the parsing and
 * command building are proven without a real engine installed.
 */
internal interface SpeechHost {

    /** The installed voices, or empty when they cannot be listed. */
    fun voices(): List<SpeechVoice>

    /** The command that speaks [text] with [voice]. */
    fun command(voice: SpeechVoice, text: String): List<String>

    companion object {

        /** The host for the current OS, or null when it has no recognisable synthesizer. */
        fun current(runner: ProcessRunner): SpeechHost? = when {
            "mac" in osName() -> MacSpeechHost(runner)
            "win" in osName() -> WindowsSpeechHost(runner)
            "linux" in osName() -> linuxHost(runner)
            else -> null
        }

        /**
         * Linux prefers speech-dispatcher's `spd-say` when installed, so the
         * user's configured synthesizer and default voice are used rather than
         * the `espeak-ng` voice chosen for them; a better installed voice such as
         * Piper is otherwise unreachable. `espeak-ng` (then the older `espeak`)
         * stays as the fallback and still supplies the voice listing (issue #21).
         * Null when neither exists, so the engine reports "no engine".
         */
        internal fun linuxHost(runner: ProcessRunner): SpeechHost? {
            val espeakBinary = listOf("espeak-ng", "espeak").firstOrNull { binary ->
                runOrNull(runner, listOf(binary, "--voices")) != null
            }
            val hasDispatcher = runOrNull(runner, listOf("spd-say", "--version")) != null
            return if (espeakBinary == null && !hasDispatcher) {
                null
            } else {
                LinuxSpeechHost(runner, espeakBinary, preferDispatcher = hasDispatcher)
            }
        }
    }
}

/** The current OS name, lower-cased; the only ambient input the engine reads. */
private fun osName(): String = System.getProperty("os.name").lowercase()

/** Runs [command] and returns its output, or null when it fails to start or exits non-zero. */
private fun runOrNull(runner: ProcessRunner, command: List<String>): String? =
    runCatching { runner.run(command) }.getOrNull()?.takeIf { it.exitCode == 0 }?.output

/** macOS `say`, whose voice names are its ids and whose locale lines end in a `#` comment. */
private class MacSpeechHost(private val runner: ProcessRunner) : SpeechHost {

    override fun voices(): List<SpeechVoice> =
        runOrNull(runner, listOf("say", "-v", "?"))?.let(::parseSayVoices).orEmpty()

    override fun command(voice: SpeechVoice, text: String): List<String> = listOf("say", "-v", voice.id, text)
}

/**
 * Linux's synthesizer, preferring the user's speech-dispatcher configuration
 * over a directly-driven `espeak-ng` (issue #21).
 *
 * [voices] stays `espeak`-based even when `spd-say` is preferred: espeak's
 * `--voices` table is the near-universal per-language catalogue that lets
 * [JvmPronouncer] tell a supported language from an unsupported one, whereas the
 * dispatcher's own list (`spd-say -L`) is synthesis voices, not that catalogue.
 * A machine with only `spd-say` installed therefore lists no voices and reports
 * "no voice"; that is an accepted edge, not a reason to rearchitect the seam.
 */
internal class LinuxSpeechHost(
    private val runner: ProcessRunner,
    private val espeakBinary: String?,
    private val preferDispatcher: Boolean,
) : SpeechHost {

    override fun voices(): List<SpeechVoice> = espeakBinary
        ?.let { binary -> runOrNull(runner, listOf(binary, "--voices"))?.let(::parseEspeakVoices) }
        .orEmpty()

    override fun command(voice: SpeechVoice, text: String): List<String> = if (preferDispatcher) {
        // spd-say speaks through the user's configured synthesizer and default
        // voice; -l sets the ISO language, -w defers the exit code until the
        // utterance finishes, as espeak's does.
        listOf("spd-say", "-l", baseLanguage(voice.language), "-w", text)
    } else {
        listOfNotNull(espeakBinary, "-v", voice.id, text)
    }
}

/** Windows PowerShell over `System.Speech`, the only synthesizer desktop Windows ships. */
private class WindowsSpeechHost(private val runner: ProcessRunner) : SpeechHost {

    override fun voices(): List<SpeechVoice> =
        runOrNull(runner, listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", LIST_VOICES))
            ?.let(::parseWindowsVoices)
            .orEmpty()

    override fun command(voice: SpeechVoice, text: String): List<String> =
        listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", speakScript(voice.id, text))

    private fun speakScript(voice: String, text: String): String = "Add-Type -AssemblyName System.Speech; " +
        "\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
        "\$s.SelectVoice('${voice.escapeForPowerShell()}'); " +
        "\$s.Speak('${text.escapeForPowerShell()}')"

    private fun String.escapeForPowerShell(): String = replace("'", "''")

    private companion object {
        const val LIST_VOICES: String =
            "Add-Type -AssemblyName System.Speech; " +
                "(New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices() | " +
                "ForEach-Object { \$_.VoiceInfo.Name + '|' + \$_.VoiceInfo.Culture.Name }"
    }
}

/** Parses an `espeak-ng --voices` table into one voice per language row. */
internal fun parseEspeakVoices(output: String): List<SpeechVoice> = output.lineSequence()
    .drop(1) // the "Pty Language Age/Gender …" header
    .mapNotNull { line -> line.trim().split(WHITESPACE).getOrNull(1)?.takeIf(String::isNotBlank) }
    .distinct()
    .map { language -> SpeechVoice(language, language) }
    .toList()

/** Parses a `say -v ?` listing: a voice name, its locale and a `#` comment. */
internal fun parseSayVoices(output: String): List<SpeechVoice> = output.lineSequence()
    .mapNotNull { line ->
        SAY_VOICE.find(line)?.let { match -> SpeechVoice(match.groupValues[1], match.groupValues[2]) }
    }
    .toList()

/** Parses the `Name|locale` lines the Windows listing prints. */
internal fun parseWindowsVoices(output: String): List<SpeechVoice> = output.lineSequence()
    .mapNotNull { line ->
        val parts = line.split('|').map(String::trim)
        if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
            SpeechVoice(parts[0], parts[1])
        } else {
            null
        }
    }
    .toList()

private val WHITESPACE = Regex("\\s+")

// A voice name (which may contain spaces), then a locale such as en_US or fr-FR, then a # comment.
private val SAY_VOICE = Regex("""^(.+?)\s+([A-Za-z]{2,3}(?:[_-][A-Za-z0-9]+)?)\s+#.*$""")
