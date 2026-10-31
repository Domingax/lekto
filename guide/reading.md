# Reading

A **Reading session** opens a **Book** and presents the text Lekto renders for it,
one page at a time.

## Moving through a book

Turn pages with a horizontal swipe — leftward for the next page, rightward for the
previous — or with the two chevrons in the bottom bar. A tap that is not on a word
does nothing, so reaching for the text never turns a page by accident. The two
bars stay on screen while you read. With a screen reader, the page offers
**Previous page** and **Next page** as actions, so you can move through the book
from the page itself.

To look up more than one word, press and hold a word, then drag to the last word
of the phrase and let go. The words wash as you drag, and the translation panel
opens when you release.

## Moving by chapter

When a book declares chapters, the bottom bar tells you both where you are in
the chapter and how far through the book you are. The chapter's own title sits
above the percentage — `XI`, then `46%` — so it uses the same words the chapter
list shows. A long title is shortened with `…` to keep the text visible. Every
chapter starts on a new page.

Tap that position to open the chapter list. It keeps the book's hierarchy, marks
the chapter you are reading and scrolls it into view. Choose a chapter to jump
there: the reader saves your new place at once and shows the chapter straight
away, because it only lays out the chapter you are reading. Close the list with
**Close**, by tapping outside it, or with the system back gesture; back closes the
list before it closes the reader.

A book with one chapter — and every text file — has no chapter list. Its
position stays a label and reads `42%`.

## Resuming where you left off

The reader saves your **Reading position** per book and restores it when you
reopen the book, so a session continues where the last one stopped.

The position is a character offset into the text, not a page number. Pages are a
function of the screen and the font, so a page number would not survive a reflow;
the offset names the same place whether you change the font size, resize the
window, or open the book on another device. You return to a book from the
[Library](/library).

The reader fills the screen: the arrow at its top left returns to the Library, and
so does the system back gesture on Android. Back closes a lookup panel or the
**Aa** panel first, so it never costs you your page.

## Finishing a book

Lekto never decides on its own that you have finished a book, and reaching its
last page is not the same as having read it. When you are on the last page, tap
the next chevron once more, or swipe leftward, and a full-screen state opens. It
says **THE END**,
then the book's title, and offers **Mark as finished** and **Back to Library**.

**Mark as finished** declares the book finished and leaves the state open: a
check and **Finished** appear, the main action becomes **Back to Library**, and
**Mark as unfinished** is offered below it. Marking never moves your **Reading
position**, so reopening the book still takes you where you stopped.

If you marked a book by mistake, **Mark as unfinished** removes only the
declaration. Your Reading position, your last-read time and your vocabulary stay
as they were.

A book you declared finished stays finished when you reopen it or read it again;
only an explicit **Mark as unfinished** changes that. The state closes on
**Back to Library**, or with the system back gesture on Android, which returns
you to the last page. The Library shows finished books when you select the
**Finished** filter; see [Library](/library).

## Changing how the text looks

Tap **Aa** in the reader's top bar to open the **Aa** panel. The panel sits under
the page and the bottom bar, so the bottom of the page is hidden while it is
open. The percentage does not change: the panel is temporary, and the whole page
comes back when you close it. Its chips set the text face (serif or sans-serif),
the text size, and how wide the page margins are. There is nothing to confirm:
the book repaginates as you tap a chip, so you see the effect straight away and
stay on the same passage. Close the panel with **Close**, by tapping **Aa**
again, or by tapping the page outside it — a tap outside the panel closes it and
does not look a word up.

Your **Reading preference** is kept on this device only. It is not part of your
vault, so it does not travel with an export or a sync — set it again on another
device.

## Mastery colouring

Every **Word token** is coloured by its **Mastery level**, on a five-point scale:

| Level | Meaning | Shown as |
| ----- | ------- | -------- |
| 0 | Unknown | Highlighted |
| 1 | Recognized | Highlighted |
| 2 | Familiar | Highlighted |
| 3 | Learned | Highlighted |
| 4 | Known | Normal text, no highlight |

Levels 0–3 are highlighted so unfamiliar words stand out. Level 4 is invisible:
a word you know sits in the text like any other. As you save words and raise their
levels, the page slowly clears.

## Your reader level

You can tell Lekto the level you read at in a language — one of the CEFR bands
A1 to C2 — on the **Languages** surface. Pick the band beside a language. A word
at or below that level then reads as normal text straight away, without you
saving it, and a harder word stays highlighted by how far above your level it is.
A word you have saved keeps the level you gave it, whatever your reader level is.
Choose **Not set** to clear the level again.

Your level is your own data: it is kept in your vault, so it survives a restart
and travels with an export. The comparison also needs word-difficulty data for
the language, which you download once on the **Languages** surface — beside the
level, so you can see whether it is installed for each language, and download it
afresh when it is out of date. A language Lekto publishes no word-difficulty data
for says so and offers no download. The data is device-local and never part of
your vault or an export. Until it is installed, or for a language you have not set
a level for, nothing changes and every unsaved word stays highlighted.

## Look a word up

Tap a **Word token** to open the lookup panel. See [Word lookup](/word-lookup).

## On a desktop

A wide window keeps a column at the reader's right, and the page never moves when
the column changes. With nothing touched, the column lists the book's chapters —
the same list the bottom bar raises on a phone, with the chapter you are reading
marked. Choose a chapter there and Lekto jumps to it and saves your place, just
as it does from the phone's list. When a book has no chapter list, the column
says so.

On a keyboard, the left and right arrow keys turn the page. They stand down while
a panel or the end of the book is open, so the keys stay with what is open.

Tap a word or select a phrase and the column shows it instead: the word's
dictionary entry, or the phrase and its translation, with the same controls the
phone's panel offers. Close it and the chapter list comes back — or tap the
position in the bottom bar, which returns the column to the list without closing
the word by hand. Because the column is part of the layout rather than a panel
over the text, selecting a word never reflows the page or moves it sideways.

In a narrow window — under about 1100 dp, so on any phone — there is no column:
the chapter list opens from the position in the bottom bar, and the lookup panel
is raised from the bottom over the page.

## Screenshots

### Desktop

![Reading on desktop](/images/reader-desktop.png)

### Android

![Reading on Android](/images/reader-android.png)
