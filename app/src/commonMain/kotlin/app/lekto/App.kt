package app.lekto

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.lekto.core.MasteryLookup
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.ReadingSession
import app.lekto.core.text.TextSegmenter
import app.lekto.library.LibraryActions
import app.lekto.library.LibraryController
import app.lekto.library.LibraryScreen
import app.lekto.reader.ReaderActions
import app.lekto.reader.ReaderChapter
import app.lekto.reader.ReaderRenderer
import app.lekto.reader.ReaderScreen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The pieces a platform entry point supplies to the [App]: the [segmenter], the
 * vault-backed [library], the wording palette's [mastery], the file [pickFile]
 * and the [dispatcher] blocking work runs on. Bundled so the root composable's
 * signature stays small and grows in one named place.
 */
data class AppEnvironment(
    val segmenter: TextSegmenter,
    val library: BookLibrary,
    val mastery: MasteryLookup,
    val pickFile: (suspend () -> PickedFile?)? = null,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
)

/**
 * The application root and the composition root for the reading loop: it shows
 * the library, drives an import through the environment's library, and opens the
 * chosen book in the reader (issue #15).
 *
 * The host supplies the file picker as [AppEnvironment.pickFile], because
 * choosing a file is a platform action. When it is absent the Import button does
 * nothing, so the app still builds and stays usable where no picker is wired.
 *
 * Parsing and opening are blocking, so they run on [AppEnvironment.dispatcher]
 * rather than the composition thread; it is injected because a hard-coded
 * dispatcher cannot be driven by virtual time (docs/testing.md, "Deterministic
 * seams").
 */
@Composable
fun App(environment: AppEnvironment) {
    val controller = remember(environment.library, environment.dispatcher) {
        LibraryController(environment.library, environment.dispatcher)
    }
    val state by controller.state.collectAsState()
    var reading by remember { mutableStateOf<ReadingSession?>(null) }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        val session = reading
        if (session != null) {
            ReaderSession(session, environment.segmenter, environment.mastery) { reading = null }
        } else {
            LibraryScreen(
                state = state,
                actions = LibraryActions(
                    onImport = {
                        environment.pickFile?.let { pick ->
                            scope.launch {
                                val file = pick() ?: return@launch
                                controller.import(file.name, file.bytes)
                            }
                        }
                    },
                    onOpen = { book ->
                        scope.launch {
                            withContext(environment.dispatcher) { environment.library.open(book.id) }
                                ?.let { opened -> reading = opened }
                        }
                    },
                    onDismissError = controller::dismissError,
                ),
            )
        }
    }
}

/** The reader over an opened [session], with a way back to the library. */
@Composable
private fun ReaderSession(
    session: ReadingSession,
    segmenter: TextSegmenter,
    mastery: MasteryLookup,
    onBack: () -> Unit,
) {
    // The reader shows one chapter at a time; this build renders a book's blocks
    // as a single chapter and the chrome carries the book's title.
    val chapter = ReaderChapter(
        title = session.book.title,
        language = session.book.language,
        blocks = session.text.blocks,
    )
    ReaderScreen(
        chapter = chapter,
        renderer = ReaderRenderer(segmenter = segmenter, mastery = mastery),
        actions = ReaderActions(onBack = onBack),
    )
}

/** A platform-picked file: its name and bytes, read before it reaches the domain. */
class PickedFile(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PickedFile && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}
