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
import app.lekto.core.book.ReadingPosition
import app.lekto.core.book.ReadingSession
import app.lekto.core.text.TextSegmenter
import app.lekto.library.LibraryActions
import app.lekto.library.LibraryController
import app.lekto.library.LibraryScreen
import app.lekto.reader.ReaderActions
import app.lekto.reader.ReaderChapter
import app.lekto.reader.ReaderDocument
import app.lekto.reader.ReaderRenderer
import app.lekto.reader.ReaderScreen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
            ReaderSession(
                session = session,
                environment = environment,
                onBack = { reading = null },
                onPositionChange = { offset -> persistPosition(scope, environment, session.book.id, offset) },
            )
        } else {
            LibraryScreen(state, libraryActions(environment, controller, scope) { opened -> reading = opened })
        }
    }
}

/** Writes [offset] as a book's reading position, off the composition thread. */
private fun persistPosition(scope: CoroutineScope, environment: AppEnvironment, bookId: String, offset: Int) {
    scope.launch {
        withContext(environment.dispatcher) {
            environment.library.savePosition(ReadingPosition(bookId, offset))
        }
    }
}

/** The library's actions: import through the platform picker, open a book, dismiss a failure. */
private fun libraryActions(
    environment: AppEnvironment,
    controller: LibraryController,
    scope: CoroutineScope,
    onOpen: (ReadingSession) -> Unit,
): LibraryActions = LibraryActions(
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
            withContext(environment.dispatcher) { environment.library.open(book.id) }?.let(onOpen)
        }
    },
    onDismissError = controller::dismissError,
)

/** The reader over an opened [session], with a way back to the library. */
@Composable
private fun ReaderSession(
    session: ReadingSession,
    environment: AppEnvironment,
    onBack: () -> Unit,
    onPositionChange: (Int) -> Unit,
) {
    // The reader shows one chapter at a time; this build renders a book's blocks
    // as a single chapter and the chrome carries the book's title. It opens at
    // the saved reading position, or the start for a book never opened.
    val chapter = ReaderChapter(
        title = session.book.title,
        language = session.book.language,
        blocks = session.text.blocks,
    )
    ReaderScreen(
        document = ReaderDocument(
            chapter = chapter,
            renderer = ReaderRenderer(segmenter = environment.segmenter, mastery = environment.mastery),
            initialOffset = session.position?.offset ?: 0,
        ),
        actions = ReaderActions(onBack = onBack, onPositionChange = onPositionChange),
    )
}

/** A platform-picked file: its name and bytes, read before it reaches the domain. */
class PickedFile(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PickedFile && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}
