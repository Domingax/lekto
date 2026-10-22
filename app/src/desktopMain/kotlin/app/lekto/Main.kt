package app.lekto

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.lekto.core.speech.JvmPronouncer
import app.lekto.core.text.IcuTextSegmenter
import app.lekto.text.appTextsFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.util.Locale

@Suppress("LongMethod") // The desktop composition root wires each independent seam once; splitting scatters it.
fun main() {
    // The device's language is read before composition and handed in, so the
    // first-launch default is a value rather than a locale read in a composable.
    val systemLanguage = Locale.getDefault().language
    // GTK's chooser must not bind its fonts to Skiko's bundled FreeType (issue #219).
    preloadSystemFreeType()
    application {
        val library = remember { desktopBookLibrary() }
        val vocabulary = remember { desktopVocabulary() }
        val readerLevels = remember { desktopReaderLevels() }
        val languageSettings = remember { desktopLanguageSettings() }
        val readingPreferences = remember { desktopReadingPreferences() }
        val appTheme = remember { desktopAppTheme() }
        val dictionary = remember { desktopDictionary() }
        val difficulty = remember { desktopDifficulty() }
        val languages = remember { desktopLanguage() }
        val pronouncer = remember { JvmPronouncer() }
        val secrets = remember { desktopSecretStore() }
        val llm = remember { desktopLlm() }
        val sync = remember { desktopSync() }
        // The desktop "the app came back" signal (issue #121): a shared flow the
        // window's focus listener raises, so sync runs when the user returns to the
        // app rather than only on its bounded timer.
        val resumes = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
        Window(onCloseRequest = ::exitApplication, title = "Lekto 1.0.3 test") {
            // The dialogs are owned by this window (issue #219), so they are modal
            // to the app, centred on it and cannot open behind it.
            val vaultTransfer = remember {
                desktopVaultTransfer(
                    save = { name, bytes ->
                        withContext(dialogDispatcher()) {
                            saveVaultToFile(window, appTextsFor(languageSettings.load().appLanguage), name, bytes)
                        }
                    },
                    open = {
                        withContext(dialogDispatcher()) {
                            pickVaultFileToImport(window, appTextsFor(languageSettings.load().appLanguage))
                        }
                    },
                )
            }
            DisposableEffect(window, resumes) {
                val listener = object : WindowFocusListener {
                    override fun windowGainedFocus(event: WindowEvent?) {
                        resumes.tryEmit(Unit)
                    }

                    override fun windowLostFocus(event: WindowEvent?) = Unit
                }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }
            App(
                AppEnvironment(
                    segmenter = IcuTextSegmenter(),
                    library = library,
                    vocabulary = vocabulary,
                    readerLevels = readerLevels,
                    languageSettings = languageSettings,
                    systemLanguage = systemLanguage,
                    readingPreferences = readingPreferences,
                    appTheme = appTheme,
                    pickFile = {
                        withContext(dialogDispatcher()) {
                            pickFileToImport(window, appTextsFor(languageSettings.load().appLanguage))
                        }
                    },
                    dictionary = dictionary,
                    difficulty = difficulty,
                    languages = languages,
                    pronouncer = pronouncer,
                    openUrl = ::openInBrowser,
                    vaultTransfer = vaultTransfer,
                    secrets = secrets,
                    llm = llm,
                    sync = sync,
                    onResume = resumes,
                ),
                onWindowBackgroundChange = { paper ->
                    window.background = java.awt.Color(paper.red, paper.green, paper.blue)
                },
            )
        }
    }
}

/**
 * The dispatcher the desktop **dialogs** block on (issue #192).
 *
 * A modal Swing dialog pumps its own nested event loop while the user answers.
 * Run on the composition's dispatcher — Compose's flush dispatcher, on the AWT
 * event thread — that loop re-enters the flush while the picker's continuation
 * is mid-resume, and the machinery resumes it twice: an intermittent
 * `CoroutineInternalError`, and a `ClassCastException` that drops the import.
 * A dialog blocks its thread, so it belongs on the I/O dispatcher.
 */
@Suppress("InjectDispatcher") // The composition root is where a real dispatcher belongs; tests inject their own.
private fun dialogDispatcher(): CoroutineDispatcher = Dispatchers.IO
