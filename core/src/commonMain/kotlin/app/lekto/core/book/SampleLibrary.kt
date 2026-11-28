package app.lekto.core.book

import app.lekto.core.sample.Sample
import app.lekto.core.sample.SampleCatalogContent
import app.lekto.core.text.BlockKind
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.vault.DerivedAssetStore
import kotlin.time.Clock

/**
 * The one **Sample book** the shelf shows (CONTEXT.md, "Sample book"; issue
 * #278): the book the reader opens — carrying the stable identity
 * `sample:<language>` — and where they are in it.
 *
 * A sample is never a **Vault** record, so this is a projection beside the
 * vault's books rather than one of them: the shelf's book list and the
 * **Languages** counts read the vault, and the shelf shows this above it.
 */
data class SampleBookRow(val book: Book, val progress: BookProgress, val state: ReadingState)

/**
 * The materialised **Sample book** (issue #278): the catalogue entry chosen at
 * the first launch, written to the derived store under `sample:<language>` and
 * opened as a normal [ReadingSession] — the same text the guide's demonstration
 * shows, so one source serves both.
 *
 * One sample, forever: [materialise] writes only while the device holds none,
 * and once [dismiss] has run it writes nothing, whatever the next build ships,
 * because the dismissal is a document of its own that the guide's progress
 * cannot corrupt (#277). Reading the sample is a normal session — page turns,
 * lookup, pronunciation and a real vocabulary save all work — but the position
 * and the finished declaration are kept beside the sample, never in the vault:
 * removing the sample loses nothing the reader authored.
 *
 * A write that fails throws to the caller, which reports it and leaves the shelf
 * in its empty state; the sample is written atomically, so a failure leaves no
 * row pointing at missing files.
 *
 * @param catalogue the **Sample catalogue** this build ships; a language it does
 *   not carry materialises nothing.
 * @param clock the clock the reader's finished declaration is stamped with; a
 *   deterministic seam in tests (docs/development.md, "Deterministic seams").
 */
class SampleLibrary(
    private val catalogue: SampleCatalogContent,
    derived: DerivedAssetStore,
    private val dismissed: SampleDismissedStore,
    private val clock: Clock = Clock.System,
) {

    private val cache = SampleCache(derived)

    /**
     * Materialises [language]'s entry as this device's one sample, replacing any
     * entry it already holds so the shelf follows an explicit replay's answer, or
     * returns `null` when there is nothing to write: the reader dismissed the
     * sample, or [language] is not in the catalogue. It is still exactly one
     * sample at any moment — a reader who answers the guide again gets one row,
     * never a second.
     */
    fun materialise(language: String): SampleBookRow? = when {
        dismissed.load().isDismissed -> null

        else -> catalogue.sampleOf(language)?.let { sample ->
            cache.put(sample, textOf(sample))
            row()
        }
    }

    /** The shelf's projection of the stored sample, or `null` when there is none or it was dismissed. */
    fun row(): SampleBookRow? {
        if (dismissed.load().isDismissed) return null
        return cache.storedLanguage()?.let(::rowOf)
    }

    /**
     * The catalogue's [language] entry, whether or not this device has materialised
     * it (issue #277's guide): the demonstration reads the sample's own colours and
     * glossary before the shelf holds a row, so the page and the sheet work on the
     * first frame. `null` when this build ships no such sample.
     */
    fun sample(language: String): Sample? = catalogue.sampleOf(language)

    /** The reading session for the sample with [id], or `null` when this device holds no such sample. */
    fun open(id: String): ReadingSession? {
        val document = documentFor(id) ?: return null
        return ReadingSession(
            book = bookOf(document.sample),
            text = document.text,
            position = document.position?.let { offset -> ReadingPosition(id, offset) },
            finished = document.finishedAt?.let { at -> FinishedBook(id, at) },
        )
    }

    /**
     * Keeps [position] on the sample it belongs to, and returns whether the
     * sample owned it. A vault book's position is `false`, so the caller writes
     * it to the vault instead.
     */
    fun savePosition(position: ReadingPosition): Boolean {
        val document = documentFor(position.bookId) ?: return false
        cache.update(document.copy(position = position.offset))
        return true
    }

    /**
     * Declares or undoes [bookId]'s **Finished** fact on the sample, and returns
     * whether the sample owned it. It is a derived fact beside the sample, never
     * a vault declaration: removing the sample removes it too.
     */
    fun markFinished(bookId: String, finished: Boolean): Boolean {
        val document = documentFor(bookId) ?: return false
        cache.update(document.copy(finishedAt = if (finished) clock.now() else null))
        return true
    }

    /**
     * Removes the sample with [bookId]: its dismissal is written **first**, so a
     * failure part-way leaves the row hidden rather than resurrected on the next
     * launch, and its files are deleted after. The identity `sample:<language>`
     * stays reserved; a later build that ships the sample again materialises
     * nothing. The reader's books and vocabulary are never touched.
     */
    fun dismiss(bookId: String): Boolean {
        val document = documentFor(bookId) ?: return false
        dismissed.save(SampleDismissed(document.sample.language))
        cache.remove(document.sample.language)
        return true
    }

    /** The stored document whose identity is [id], or `null` when this device holds no such sample. */
    private fun documentFor(id: String): MaterialisedSample? = cache.storedLanguage()
        ?.let(cache::document)
        ?.takeIf { stored -> stored.sample.identity == id }

    /** The row the shelf shows for [language]'s stored sample, or `null` when it cannot be read. */
    private fun rowOf(language: String): SampleBookRow? {
        val document = cache.document(language) ?: return null
        val length = document.text.renderedLength()
        val position = document.position
        return SampleBookRow(
            book = bookOf(document.sample),
            progress = position
                ?.let { offset -> BookProgress.Measured(ReadingProgress(offset, length).percent) }
                ?: BookProgress.NotStarted,
            state = ReadingState.of(hasPosition = position != null, finished = document.finishedAt != null),
        )
    }

    private fun bookOf(sample: Sample): Book = Book(
        id = sample.identity,
        title = sample.title,
        language = sample.language,
        format = BookFormat.TXT,
        fileName = "sample-${sample.language}.txt",
        author = sample.author,
    )

    private fun textOf(sample: Sample): StructuredText = StructuredText(
        title = sample.title,
        language = sample.language,
        author = sample.author,
        blocks = sample.paragraphs.map { paragraph ->
            TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(paragraph)))
        },
    )
}
