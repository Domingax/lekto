# Pronunciation is a platform seam; the desktop binds the operating system's synthesizer

Hearing a selected word (issue #21) must work on Android through the platform engine
and on desktop through a JVM binding, and a language with no installed voice must be an
honest message rather than a crash. Android answers this first-party:
`android.speech.tts.TextToSpeech` is the OS's own engine, with voices the user installs
(ADR-0007, "zero third-party dependencies"). Desktop has no equivalent in the JDK, and
there is no permissively-licensed, per-language, pure-JVM speech engine to bundle:
FreeTTS is English-only, MaryTTS is LGPL, and the leading KMP library marks desktop as
experimental (`docs/research/android-first-stack.md` §2).

We decided that pronunciation is a **platform seam**, `Pronouncer`, in `core`'s shared
sources: `speak(text, language)` returns an honest `SpeechResult` — `Spoken`, `NoVoice`
or `Unavailable` — and never throws into the reading session. Android backs it with
`TextToSpeech` in `core`'s `androidMain`; desktop backs it with `JvmPronouncer` in
`core`'s `jvmMain`, a **JVM binding that drives the operating system's synthesizer as a
short-lived process** — `say` on macOS, `spd-say` on Linux with `espeak-ng` as the
fallback, `System.Speech` via
PowerShell on Windows. The engine is resolved per OS and its installed voices are read
once, lazily; the voice is matched by the book's base language, so `en-US` finds an
`en` voice and a language with no voice is `NoVoice`. Lekto bundles no engine, no voice
and no audio; the OS owns synthesis, exactly as it owns the browser the reference
shortcuts open (issue #19).

We rejected bundling a Java speech engine (no licence-clean, per-language one exists),
adding a KMP TTS dependency (desktop is experimental and it is a production dependency
for a platform that is second), and degrading silently (a language with no voice would
look like success). We also rejected reading a `WordToken`'s audio regardless of
language: voices are per-language, and the honest outcome is the point.

**Consequences**: desktop pronunciation needs an OS synthesizer and a language voice
installed; on a machine without one, `JvmPronouncer` reports `Unavailable` rather than
pretending. On Linux the voice is listed through `espeak-ng` even when speech-dispatcher
speaks it, so a machine with only `spd-say` installed and no espeak reports `NoVoice`
rather than speaking an unlisted voice. `speak` is **blocking** — it runs a process on
desktop and may wait on the
Android engine — so the application calls it off the UI thread, and `SpeechResult` is
carried back to the lookup panel as state. Android's `TextToSpeech` is discovered across
package boundaries, so the app declares the `android.intent.action.TTS_SERVICE`
`<queries>` intent (required from Android 11). The seam is synchronous for now; a future
platform that must stream audio adds a method beside `speak`, not a new module.
