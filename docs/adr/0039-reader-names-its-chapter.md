---
status: accepted
superseded in part by ADR-0040
---

# The reader names its chapter, it does not number it

> The `Page X of Y` line beside the chapter title is superseded by
> [ADR-0040](0040-reader-paginates-one-chapter-and-shows-progress.md): the
> position is the chapter's title and **Reading progress**. Naming the chapter,
> rather than numbering it, still stands.

ADR-0037 made the bottom bar read `Chapter N · Page X of Y`, where N counted
**Reading units** in reading order. The rule was meant to keep a number stable
when a table of contents repeats or nests an entry, but a real book shows why it
fails: front matter ("Cover", a title page, an introduction) and a **Part** that
carries its own text are reading units too, so the twelfth printed chapter is
counted as unit fifteen. A book that numbers its chapters in Roman numerals
reads "XI" in its own list while the bar says "Chapter 15". The reader then has
two numbers for one place, and only one of them is the book's.

**The bar shows the chapter's title above the page, both lines centred.** It
reads `XI` over `Page 285 of 405`, `CHAPTER 13. Wheelbarrow.` over
`Page 99 of 357`, or whatever the table of contents declares. The title keeps
one line and is ellipsised, so a long publisher heading never pushes the page
off the bar. Before the first chapter, and in a book with one chapter, it reads
`Page X of Y` alone and no chapter is current, exactly as ADR-0037 decided. The
title is already the row the sheet highlights, so the two surfaces cannot
disagree: there is no second numbering to drift, no number to invent, and no
translation of a printed numeral into a count.

This supersedes ADR-0037's `Chapter N` label only. Its model — one recursive
`Chapter(title, startBlock, children)`, the derived cache beside the text, the
page break at every chapter start, the jump that saves the first block, and the
sheet — stands. A **Reading unit** still decides which rows the sheet offers as
choices, but it is no longer displayed.

**Rejected alternatives**: numbering the sheet's rows with the same invented
count, which makes the two surfaces agree with each other but still contradict
the printed book; extracting the number from the label, which fails across
languages, Roman numerals and titles that contain years; and keeping the count
with a caveat, which asks the reader to explain away a mismatch every page.

**Consequences**: `ReaderChapterEntry` loses its number, the position is
composed from the chapter's title and the existing page line instead of a
formatted count, and `docs/design.md` §7.2 and the user guide describe the two
lines.
