---
status: accepted
---

# A book's chapter starts are indexed beside its parsed text

The Library can now name the **Chapter** a **Reading position** sits in
(issue #260). The chapter list lives inside the parsed `StructuredText` that
ADR-0037 caches, so the data is already on the device. But finding the chapter
needs the character offset of each chapter's start, and that offset is computed
from every block before it. Reading it from the text means decoding the whole
book: a shelf of three positioned books would decode three books on every
refresh, which is exactly what issue #196 kept the listing from doing.

**The chapter list stays in the parsed text; a small index sits beside it.** The
text cache writes `book-text/<bookId>.chapters`: the chapter titles flattened to
their rendered start offsets. It is a **Derived asset** (ADR-0005), written and
removed with the text, never in the **Vault**, never exported, never synced. The
index cannot be authored and the text cannot disagree with it for long: both
carry the same `FORMAT_VERSION`, so a text shape that shifts an offset
invalidates the index with it, and a book opened without an index rebuilds one
from the cached text. The Library then names the chapter with the last start at
or before the position — the same rule the reader's chapter list uses.

**This is not ADR-0037's rejected separate asset.** That alternative put the
chapter list in a file of its own, where a re-parse could leave structure and
text describing different books. The parsed text still carries the chapter
list; what is new is a read model of its starts, derived from it and versioned
with it, so a shelf can read one small file instead of a book. The residual
drift is a hand-deleted text file, and the next open rewrites both.

**Unknown means no chapter.** A missing or stale index, a position before the
first chapter, and a book with one chapter all name none. A one-chapter book has
no chapter list (ADR-0037), and the reader already keeps its position a label
(ADR-0039); the Library follows the same rule rather than repeating the book's
own title.

**Rejected alternatives**: decoding the parsed text at listing time, whose cost
grows with the book and is paid on every shelf read; storing only the current
chapter at position-save time, which a **Sync**ed position would leave naming the
wrong chapter, and which would duplicate the derived write issue #258 already
owns; a **Vault** record, against ADR-0005 and the fact that every device can
derive the same index from the same original.

**Consequences**: `BookTextCache` owns a third path and the chapter index's
envelope; `FORMAT_VERSION` invalidates the text and the index together; the
Library's listing carries a chapter title per book; the resume block (issue #288)
is the surface that reads it, and the row's own line stays the percentage's.
