package app.lekto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import app.lekto.core.book.BookLibrary
import app.lekto.core.secret.AndroidSecretStore
import app.lekto.core.speech.AndroidPronouncer
import app.lekto.core.text.IcuTextSegmenter
import app.lekto.core.vocabulary.Vocabulary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var pronouncer: AndroidPronouncer? = null

    /** The paper already painted on the window, so a recomposition is not a repaint. */
    private var windowPaper: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // The App theme's paper becomes the window background (ADR-0036) before the
        // first frame, so a dark or sepia choice never flashes a light window. The
        // choice is read before super.onCreate because the window theme must be in
        // place before the activity's content is created.
        setTheme(androidWindowTheme(androidAppTheme(this).load(), resources.configuration))
        super.onCreate(savedInstanceState)
        val library = androidBookLibrary(this)
        val vocabulary = androidVocabulary(this)
        pronouncer = AndroidPronouncer(this)
        // The device's language is read before composition and handed in, so the
        // first-launch default is a value rather than a locale read in a composable.
        val systemLanguage = Locale.getDefault().language
        setContent { LektoApp(library, vocabulary, pronouncer, systemLanguage, ::applyWindowPaper) }
    }

    /**
     * The window's own background and bar icons follow the theme as it changes
     * (ADR-0036): the Compose surface covers the paper, but the strip outside it
     * and the status bar's icons are the platform's. Skipped when the paper has not
     * moved, so a recomposition is not a repaint.
     */
    private fun applyWindowPaper(paper: Color) {
        val argb = paper.toArgb()
        if (argb == windowPaper) return
        windowPaper = argb
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(argb))
        val lightIcons = ColorUtils.calculateLuminance(argb) > LIGHT_PAPER_LUMINANCE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightIcons
            isAppearanceLightNavigationBars = lightIcons
        }
    }

    override fun onDestroy() {
        pronouncer?.shutdown()
        super.onDestroy()
    }
}

/** The luminance above which a paper wants dark status-bar icons rather than light ones. */
private const val LIGHT_PAPER_LUMINANCE: Double = 0.5

/**
 * The Android composition root: the platform pieces the [MainActivity] supplies
 * to the shared [App], wired where the Activity's `Context` is in scope.
 */
@Suppress("LongMethod", "LongParameterList") // The composition root wires each seam once; splitting scatters it.
@Composable
private fun LektoApp(
    library: BookLibrary,
    vocabulary: Vocabulary,
    speech: AndroidPronouncer?,
    systemLanguage: String,
    onWindowBackgroundChange: (Color) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val segmenter = remember { IcuTextSegmenter() }
    val picker = remember { AndroidFilePicker() }
    val dictionary = remember { androidDictionary(context) }
    val difficulty = remember { androidDifficulty(context) }
    val languages = remember { androidLanguage(context) }
    val readerLevels = remember { androidReaderLevels(context) }
    val languageSettings = remember { androidLanguageSettings(context) }
    val readingPreferences = remember { androidReadingPreferences(context) }
    val appTheme = remember { androidAppTheme(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            picker.resolve(null)
        } else {
            scope.launch {
                picker.resolve(withContext(ioDispatcher()) { readPickedFile(context, uri) })
            }
        }
    }
    val vaultTransfer = rememberAndroidVaultTransfer(context, scope)
    val sync = remember { androidSync(context) }
    App(
        AppEnvironment(
            segmenter = segmenter,
            library = library,
            vocabulary = vocabulary,
            readerLevels = readerLevels,
            languageSettings = languageSettings,
            systemLanguage = systemLanguage,
            readingPreferences = readingPreferences,
            bookImages = androidBookImages(context),
            appTheme = appTheme,
            pickFile = {
                // Arm the picker before launching, then park the import until
                // it answers — the order closes the resolve-before-await race.
                picker.begin()
                launcher.launch(picker.mimeTypes)
                picker.await()
            },
            dictionary = dictionary,
            difficulty = difficulty,
            languages = languages,
            pronouncer = speech,
            openUrl = { url -> openInBrowser(context, url) },
            vaultTransfer = vaultTransfer,
            secrets = AndroidSecretStore(context),
            llm = androidLlm(context),
            sync = sync,
        ),
        onWindowBackgroundChange = onWindowBackgroundChange,
    )
}

/**
 * The dispatcher the platform entry point reads picked files on. Kept as a
 * function so the entry point has one seam for its I/O dispatcher; a hard-coded
 * `Dispatchers.IO` inside the coroutine would be untestable.
 */
@Suppress("InjectDispatcher") // The composition root is where a real dispatcher belongs; tests inject their own.
internal fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
