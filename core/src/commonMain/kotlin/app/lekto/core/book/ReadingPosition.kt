package app.lekto.core.book

import kotlinx.serialization.Serializable

/**
 * Where the user left off in a [Book]: a character offset into the text the
 * reader renders for that book (issue #16).
 *
 * It is deliberately a character offset, not a page number. Pages are a
 * function of the viewport — the same book is a different number of pages on a
 * phone than on a desktop, or after a font-size change — so a page number would
 * not survive the next reflow. The offset names the same place in the reading
 * text on every device (ADR-0007, decision D7: our own reading-position format,
 * not an EPUB CFI).
 *
 * A reading position is something the user authored, so it lives in the vault
 * and travels with it (CONTEXT.md, "Vault"; ADR-0005). How one is stored is
 * [ReadingPositionRecord]'s concern.
 */
@Serializable
data class ReadingPosition(val bookId: String, val offset: Int)
