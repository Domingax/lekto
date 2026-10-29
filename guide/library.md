# Library

The **Library** is the list of **Books** in your **Vault**. It is the app's
starting screen and the way back from a reading session, on Android and on
desktop alike.

## What you see

The three books you read most recently lead the list, under **Continue
reading**, the most recent first. A book joins that section as soon as you turn
a page in it, and turning back to its first page does not remove it. A book you
have declared **Finished** never leads, even if it was the last one you read:
that section is for books you can carry on with. Below it, under **Other
books**, every other book — including a finished one, and one you have not
opened yet — is listed by title. When you have not read anything yet, there are
no headings: the list is just your books, by title.

Each row is one book: a small coloured tile carrying its **Reading language** —
`EN`, `FR`, `ES`, `DE` or `IT` — then its title, then a line with its
format and its **Reading progress**, like `EPUB · 28 %`. A thin rule under the
line fills to the same whole percentage. A book you have declared finished
reads `EPUB · Finished` with a check, and carries no rule: the declaration is not
a percentage. The tile's colour is decoration: the two letters are what tells you
the language, so it reads the same if you cannot tell the colours apart. There is
no file name on the row — the book is what you see, and its file is named only
when an import fails or when you delete the book.

When the Library is empty, it says so and offers one action: **Import a book**.
While it is being read, the list shows skeleton rows instead of claiming there
are no books.

The percentage is how far you are now, not the furthest you have read: paging
back lowers it. It is the share of the book's text that lies before your
**Reading position**, rounded down, so the last page reads a little under 100 %.

A book with no Reading position reads `EPUB · Not started`, with no rule. That
describes the data, not your history: opening a book without turning a page
writes no position. A book whose text Lekto is still measuring on this device
reads `EPUB · …` until the measurement lands, then shows its percentage.

The Library is a view of the Vault, not the storage itself: the books are one
part of the Vault, and your vocabulary and your **Reading positions** live in
the same Vault beside them. The row's **Reading progress** is worked out from
your Reading position and the book's text; the text itself is measured on this
device and is never part of the Vault. For where that data lives and how to move
it, see [Vault and sync](/vault-and-sync).

## Filtering by reading state

Every book is in one of three **Reading states**: **Unread** (you have not turned
a page), **In progress** (you have) or **Finished** (you declared it). Under the
search field sits one row of chips — `Unread`, `In progress`, `Finished` — and
you can select any combination of them.

The Library opens with **Unread** and **In progress** selected, so the shelf
shows what is still to read; select **Finished** to add the books you have
finished. The last selected chip cannot be turned off, so a chip tap never
leaves the shelf empty by accident.

The filter is a view of your Library, not a setting: it stays as you leave the
Library and come back, resets when you close the app, and is never written to
your **Vault**.

If the filter hides every book, the Library says why. When all your books are
finished it reads **All your books are finished.**; otherwise it reads **No books
in this state.** Either way, **Show all books** clears the filter and the search
and shows the whole shelf.

## Finding a book

When the Library holds at least one book, a **Search** field sits under the
title. Type part of a title and the list narrows to the books that contain it:
matching ignores capitals and accents, so *emile* finds *Émile*. Only titles are
searched, not file names.

While you are searching, the **Continue reading** section disappears and the
matches are listed by title. An empty field shows the Library as usual. The
search narrows the shelf in one more step, not instead of the reading-state
filter: the two apply together, so a book the filter hides stays hidden by a
query that matches it. If nothing matches, Lekto names what you typed and offers
**Clear search** and **Show all books**.

The search is a view of your Library, not a setting: leaving the Library — to
read a book, to open Vocabulary or Settings, or to open the Languages surface
inside Settings — clears it, and it is never saved to your **Vault**.

## Opening a book

Tap a book to open a [reading session](/reading). Lekto restores your **Reading
position** automatically, so you continue where you left off; see
[Reading](/reading) for how that position survives a change of screen or font.

## Deleting a book

There is no Delete on the shelf itself. On a phone or a tablet, swipe a row from
the right and the action appears; with a mouse, point at the row — it picks up a
faint tint and a small trash appears at its end. Either way Lekto asks you to confirm first — *Delete
« title »?* — and names the imported file, so you can tell which file is about to
go. Only then does it remove the **Book**: its original file, the text derived
from it, your **Reading position** for it and your **Finished** declaration. If
sync is on, the deletion travels to your other devices instead of the book coming
back; see [Vault and sync](/vault-and-sync) for how a deletion is remembered.

Deleting a book is permanent, so Lekto offers it only from the Library, never
from inside a [reading session](/reading).

## Bringing books in

**Import** brings a book into the Library: a word beside the title once there is
a shelf, and the filled button in an empty Library. See [Import](/import) for the
formats and what each supports.

## Screenshots

### Desktop

![The Library on desktop](/images/library-desktop.png)

### Android

![The Library on Android](/images/library-android.png)
