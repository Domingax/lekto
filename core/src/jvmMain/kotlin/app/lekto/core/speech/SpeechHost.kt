package app.lekto.core.speech

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

        /** Linux prefers `espeak-ng` and falls back to the older `espeak`. */
        private fun linuxHost(runner: ProcessRunner): SpeechHost? =
            listOf("espeak-ng", "espeak").firstNotNullOfOrNull { binary ->
                runOrNull(runner, listOf(binary, "--voices"))?.let { LinuxSpeechHost(runner, binary) }
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

/** Linux `espeak-ng` (or `espeak`), whose `--voices` table is parsed into one voice per language. */
private class LinuxSpeechHost(private val runner: ProcessRunner, private val binary: String) : SpeechHost {

    override fun voices(): List<SpeechVoice> =
        runOrNull(runner, listOf(binary, "--voices"))?.let(::parseEspeakVoices).orEmpty()

    override fun command(voice: SpeechVoice, text: String): List<String> = listOf(binary, "-v", voice.id, text)
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
