---
status: accepted
superseded in part by ADR-0040
---

# Book structure is a derived asset, read from the book's table of contents

> The whole-book pagination and the page count below are superseded by
> [ADR-0040](0040-reader-paginates-one-chapter-and-shows-progress.md): the reader
> measures one chapter and shows **Reading progress**, not `Page X of Y`. The
> chapter model, the page break at every chapter start and the immediate jump
> still stand.

A book is flattened to one run of text today: the EPUB parser turns the spine into blocks and
drops the structure the file already carries. The reader can only say "Page 99 of 357", and a
reader who wants to move to a chapter has no way to do it (issue #176). This decides that a
book's chapters are kept as a **Derived asset** (ADR-0005), sourced from the table of contents
the original already carries, and that the reader uses them to say where it is and to jump.

**Chapters are a Derived asset, beside the parsed text.** An EPUB 3 `nav` document, an EPUB 2
NCX and the spine are all re-readable from the book original, so chapters are not **Vault** data
(ADR-0005). They live in the same derived cache as the parsed text rather than in a second
file: one atomic write keeps the text and its structure on the same version, and a re-parse
cannot leave a text and a chapter list that disagree. `BookTextCache` wraps the text in an
envelope carrying a `formatVersion`. A missing or different version is a cache miss: the book is
re-parsed from its original and the cache is rewritten. There is no migration, because a derived
asset is cheap to rebuild and a stale shape must never be read as a valid one.

**The table of contents wins, then the spine.** The parser reads the EPUB 3 navigation document
first, found by `properties="nav"` and allowed to sit outside the spine; it reads the EPUB 2 NCX
next, found by the spine's `toc` attribute or by its media type. A TOC entry is kept only when it
targets a document in the spine. Its label is the chapter title; an empty label falls back to the
first heading in the chapter's own text, then to the file name. The label wins even when it
differs from the visible heading, because it is the author's name for the division. Entries are
ordered by spine order and then by block index; `playOrder` is not trusted. An entry whose
fragment cannot be resolved falls back to the start of its document, and an entry that resolves
to no block is dropped. Text before the first entry has no chapter; the last entry runs to the
end of the book. When no TOC entry is usable, each spine document becomes a chapter, with its
first heading or its file name as title.

**The hierarchy survives.** A chapter may contain chapters, and a chapter that contains others is
a **Part**. A part that only groups has no text of its own: its start is its first child's, it is
not numbered, and the chapter list shows it as a heading. A part with text before its first child
is a reading unit like any leaf, and is numbered. The reader numbers **reading units** in reading
order, so "Chapter 4" means the fourth reading unit, not the fourth TOC entry. The model is one
recursive `Chapter(title, startBlock, children)`; `StructuredText` gains `chapters`, and the
reader's `ReaderChapter` — which today names the whole book — becomes `ReaderText`.

**A TXT, and a book with one chapter, have no chapter list.** A TXT has one chapter covering the
whole text, because there is no structure to invent. A book whose structure yields one chapter
behaves the same way: the position stays a label and reads `Page X of Y`. The chapter sheet only
exists when there is more than one chapter.

**The reader paginates the whole book, and every chapter starts on a new page.** The page count
stays the book's, as the mockups show. A page break at every chapter start makes the first page of
a chapter exact: it can be paginated from the chapter's first block without measuring the chapters
before it. The current chapter is the deepest one whose range contains the page's first character.
The bottom bar reads `Chapter N · Page X of Y`, and a book with one chapter reads `Page X of Y`.
Before the first chapter, where there is no chapter, it reads `Page X of Y` and no row is current.

**A jump is immediate.** Choosing a chapter closes the list, sets the **Reading position** to the
chapter's first block, saves it at once so **Last read** moves, and shows the chapter's first page
without waiting for the whole book. The page number and the total are counted in the background
and the bar updates as they become known; it may show a placeholder for a moment. This is the
same asynchronous counter the reader already uses while pagination fills. A jump to the current
chapter only closes the list and writes nothing.

**The list is a sheet on a phone and a column on desktop.** On a phone the chapter list is a
bottom sheet over the page, up to about 60 % of the height, showing the hierarchy, highlighting
the current chapter and scrolling it into view. On desktop it is the reserved context column: the
chapter list when nothing is touched, the word when a word is, and back to the list when the word
closes. Below roughly 1100 dp the column has no room and the reader uses the phone's sheet. The
lookup panel and the chapter list are never open together. Back closes the list before the shell;
the app's full back stack remains issue #185.

**Per-chapter loading is deferred.** The model makes it possible to keep one chapter's text in
memory while the page numbers stay the book's, but this change does not do it. Loading by chapter
would change pagination, the counter and resume, so it is a separate decision.

**Rejected alternatives**: spine documents only, which loses every chapter that shares a file;
flattening the TOC, which loses the parts the file declares; a separate derived asset, which can
disagree with the text; chapters in the **Vault**, against ADR-0005; a page count per chapter,
against the whole-book **Reading position** and the mockups; continuous text without a page break
at chapter starts, which makes a jump either wait for a sequential pass or show an approximate
page; and waiting for an exact counter before showing the target, which delays the reader for no
benefit.

**Consequences**: the EPUB parser grows `nav` and NCX readers and records the anchors a content
document exposes; the parsed-text cache carries a format version, so every existing cache is
re-parsed once; `StructuredText` gains chapters and `TxtParser` returns one; pagination works per
chapter segment while the counter stays whole-book; the reader gains the chapter sheet, the
desktop column and the chapter label; `CONTEXT.md` gains **Chapter**, **Part** and **Chapter
list**. Reading positions remain character offsets, so a parser change can still shift them on
re-parse; that risk existed before and is not changed here.
