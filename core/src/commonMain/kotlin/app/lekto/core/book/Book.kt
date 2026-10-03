package app.lekto.core.book

import app.lekto.core.text.StructuredText
import kotlinx.serialization.Serializable

/**
 * An imported piece of reading content (CONTEXT.md, "Book"): the metadata the
 * library lists. The original file is the book's **vault attachment** and the
 * text Lekto renders is its **derived asset**, so neither is duplicated here
 * (ADR-0005, ADR-0016).
 *
 * [id] is both the book's identity and its vault record id; [fileName] keeps the
 * imported file's name for display and error messages.
 */
@Serializable
data class Book(
    val id: String,
    val title: String,
    val language: String? = null,
    val format: BookFormat,
    val fileName: String,
)

/**
 * A book opened for reading: the [book] and the [text] the reader renders. The
 * reader consumes this and never learns where either came from.
 *
 * [position] is the saved reading position, or `null` when the book has never
 * been opened; the reader may restore it to open where the user left off.
 */
data class ReadingSession(val book: Book, val text: StructuredText, val position: ReadingPosition? = null)
