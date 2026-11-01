---
status: accepted
---

# The reader paginates one chapter at a time and shows progress, not page numbers

ADR-0037 made the reader lay out the whole book and show `Page X of Y`. On a
Pixel 6a, Moby Dick (1.2 M characters) took **14.6 s** to paginate 1570 pages
before the first page could be drawn, on every open and every chapter jump — and
a page number is a property of the screen and the font, not of the book, so it
changes when neither the book nor the reader did.

The reader now measures **only the chapter holding the reading position**: from
the chapter's start (a boundary ADR-0037 already guarantees, since every chapter
starts on a new page) to a few pages past the anchor. The cost of opening a book
is bounded by one chapter, ~110 ms, whatever the book's length. Pages already
measured stay while the reader is inside the chapter, so turning a page measures
the pages it reveals and `previous` never has to guess. The bottom bar shows the
**Chapter**'s own title (ADR-0039) and the **Reading progress** as a percentage of
the rendered text, derived from the **Reading position** — the character offset
ADR-0035 already defined.

This supersedes the whole-book pagination and the page count of ADR-0037, and the
`Page X of Y` line of ADR-0039. It keeps ADR-0037's chapter model, the page break
at every chapter start, and the immediate chapter jump.

## Consequences

- `ReaderState` keeps the anchor, not a page index: `total` is gone, and
  `previous`/`next` move by character offsets, so a reflow cannot strand them.
- A book with no chapter list is one chapter, so it still measures from the start
  of the book to the anchor. That is the remaining cost for TXT books, and the
  case a per-chapter text (ADR-0037's deferred decision) would remove.
- Reading progress is coarse — one page of a long book is a fraction of a
  percent — because it measures the book, not the screen.
