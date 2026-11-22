---
status: accepted
---

# A vocabulary entry remembers the books it was saved from

A finished book says nothing about what it gave the reader (issue #280). The
Library row carries a percentage and the end-of-book state carries only the
declaration; the **Vocabulary** is one flat list, and a word's record does not
know which book it came from. "The words this book gave me" therefore cannot be
answered at all, and saving words — the product's second outcome — ends in
storage rather than in a report.

**The book history lives on the word.** A **Vocabulary entry** gains
`bookIds: List<String>`, the books whose reading sessions saved it, each named
once and newest last. It is written by the same save that stores the word, so
there is still one record per word, no separate link record can be orphaned, and
the history dies only when the reader deletes the word. The link lives on the
word rather than on the book because the **Word key** is already the entry's
identity (ADR-0003, ADR-0006): a second record per (book, word) pair would
duplicate that identity and need its own deletions.

**An older record reads with no books.** The body's `schemaVersion` moves to 2,
but the tolerance is the codec's, not a version check: `VaultCodec` applies the
kotlinx default (`emptyList()`) to a body that has no `bookIds` and ignores keys
this build does not know (`VaultCodec.json`; ADR-0003). Nothing in
`VocabularyRecord.entryOf` reads `schemaVersion`, as for every record kind, so a
vault written by an older build loads unchanged and every word simply names no
book. A newer write is read by an older build the same way: it ignores the field
and keeps the word. No migration pass visits the vault.

**Deletion never cascades.** Deleting a **Book** leaves every **Vocabulary
entry** untouched: no level, no context sentence and no book id changes. The
reader's vocabulary is the point of the app, and an unmastered word does not
deserve to disappear with the file it came from. A `bookIds` entry for a deleted
book is harmless — no surface resolves it, and the word itself is still the
reader's. Only deleting the word removes the record and its history, and that
deletion keeps leaving its **Tombstone** (ADR-0015).

**Recency is the record's, not the entry's.** "Recently modified" reads the
vault record's `updatedAt` through `Vocabulary.allWithTimes()`, and a save
returns the moment it stamped. The entry gains no timestamp: a field on the body
would need its own sync tie-break and would give one word two update times. The
list orders by the record's time, most recent first, with the word breaking a
tie; the order is frozen when the screen opens, so changing a level never
reorders rows under the reader. Leaving and returning takes a fresh snapshot.

**The end-of-book state reports two numbers.** For the book in hand it shows the
entries whose history names it — `18 words from this book` — and how many of
those are **Known** — `5 now known` — then `See the words`, which opens the
**Vocabulary** narrowed to that book. A word saved from two books counts for
both. There is no third number: "still in your vocabulary" would always equal the
total, because the history dies with the word, and showing it would announce a
loss that cannot happen.

**The narrowing is view state.** The book chip carries the book's title and
exists only while the list was reached from that book; the count beside the
Vocabulary title stays the whole vocabulary, as it does for search and level. The
filter is a value on the destination, never a vault record (ADR-0005). `See the
words` replaces the reader, so Back from the list lands on the **Library** and
the captured session does not reopen behind it.

**The honest limit.** A book read before this change has no stored ids, so its
summary draws no line at all — never `0 words`. Absence is the truth: the word's
history predates the field, and a zero would claim the book gave nothing.

## Considered options

- **A link record per (book, word) pair.** It would make the relation symmetric,
  but it adds a record kind and a lifecycle, can be orphaned by either side, and
  duplicates the word identity that already exists.
- **A list of book ids on the book record.** It would rewrite imported content
  for a reading fact, and deleting a word would have to reach into the book.
- **Cascade the word when its book is deleted.** It would silently destroy
  vocabulary the reader saved and may have raised to **Known** — the one loss the
  product must not spring on them.
- **A timestamp on the entry.** Two update times per word (the body's and the
  record's) would disagree after a merge, and the body's would need its own
  last-writer-wins rule.
- **A third end-of-book count.** It would always equal the total, announcing a
  loss that cannot happen.
- **A `schemaVersion` check on read.** It would reject an older body the codec
  already reads, and the field is a record convention nothing consults.

## Consequences

`VocabularyEntry` gains `bookIds`, `VocabularyRecord.SCHEMA_VERSION` moves to 2,
and `Vocabulary` gains `allWithTimes()` and a save that reports its record time.
`VocabularyController` merges the book id into the stored history on save and
orders the list by recency; the reader's end state gains the two counts and
`See the words`, and `Destination.Vocabulary` carries the book it was reached
from. `CONTEXT.md`'s **Vocabulary entry** gains the book history, and
`docs/design.md` §7.2 and §7.4 describe the end state and the chip.

The trade is last-writer-wins per word: when the same word is saved from two
devices at once, each writes its own list, and one device's id is lost with the
losing body. That is accepted and confined to that one word's history — the word,
its level and its meaning survive — because the alternative is a merge rule for a
list no surface depends on for correctness. A loss is corrected the next time the
reader saves the word from that book again.
