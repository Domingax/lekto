# A book is finished when the reader declares it, in its own vault record

A **Reading position** is a character offset, and **Reading progress** is its share of the
rendered text; neither is a completion signal. At the last page the position is simply the last
offset, so the **Library** cannot tell a book read to the end from one abandoned at ninety-eight
per cent (issue #175). This ADR settles **Finished** as a declaration by the reader, not a fact
derived from the position. The reader declares it from the end-of-book state after turning past
the last page, and the app never declares it on the reader's behalf.

**Finished is a separate vault record.** It is a record of kind `progress`, id
`<bookId>-finished`, schema version 1, whose body is `FinishedBook(bookId, finishedAt)` —
`progress/<bookId>-finished.json` on disk. The position record cannot hold the flag: its
`updatedAt` is **Last read** (#174), so writing a flag there would move the book to the top of the
Library as if a page had turned, contradicting the glossary's rule that last read is a page
turned, not the book opened. The book record cannot hold it either: a book is imported content,
and a reading fact must not rewrite it. A separate record keeps the two facts independent,
travels with the **Vault**, and merges last-writer-wins like every other record (ADR-0004,
ADR-0015). The body's `finishedAt` is the declaration the domain reads; the record's `updatedAt`
stays sync metadata and does not become a domain fact. Re-marking after an undo writes a new
record with a new declaration time.

**Only an explicit undo removes it.** "Mark as unfinished" deletes the record and leaves a
**Tombstone**, so a device that has not seen the undo cannot copy the declaration back on the next
sync. Reopening the book, turning a page, or rereading it does not revoke the declaration:
"finished" says the reader has read the book, not that the position is at its end. The undo lives
in the end-of-book state; the Library row gains no action for it. Deleting a book removes its
finished record and leaves a tombstone for it too, exactly as it already does for the position
(ADR-0015).

**The Library treats it as a fact, not a percentage.** A finished book is never in the leading
"three most recently read books" group, even if it was the last book read: that group is for books
the reader can continue. The default filter shows **Unread** and **In progress**, and finished
books appear when the reader adds the **Finished** state. The filter itself is session-only view
state, never a vault record (ADR-0005). The row says `FORMAT · Finished` and carries no progress
rule: a declaration is not a percentage, and a book read to the end may have been reread or
skipped.

## Considered options

- **Derived from the position.** Reaching the end would need the text length of every book, the
  O(library) read issue #172 is trying to avoid, and it would still conflate "at the last page"
  with "read the book".
- **A field on the position record.** It would bump the record's `updatedAt`, which is **Last
  read**, and move a book merely because it was declared finished. It would also tie a declaration
  to a position the reader may later overwrite.
- **A field on the book record.** It would rewrite imported content for a reading fact, and make
  the book's own update time carry two meanings.
- **No declaration.** The Library could not tell a finished book from an abandoned one, which is
  the problem this ADR exists to solve.
- **Reopening unmarks it.** A reread would erase the fact the reader declared, and the state would
  depend on the current position again.

## Consequences

`CONTEXT.md` gains **Reading state**, **Finished**, **Unread** and **In progress**, and the
**Library** definition narrows to unfinished books in its leading group. `VaultBookLibrary.delete`
removes the finished record with the book and leaves its tombstone. The Library gains a
multi-select filter over the three states, defaulting to Unread and In progress, and the reader
gains the end-of-book state that declares and undoes the fact. There is no migration: no finished
record exists before this decision, so the absence of one already means "not declared finished".
