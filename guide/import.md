# Import

**Import** brings a **Book original** into your **Vault** and turns it into a
**Book** Lekto can render. It works the same on Android and on desktop; you start
it from the [Library](/library) with the **Import** button.

## Supported formats

Lekto imports three kinds of file, with different strengths:

| Format | Support | What it gives you |
| ------ | ------- | ----------------- |
| EPUB   | First-class | The book's headings, chapters and reading order are preserved, so it renders the way its author structured it. |
| TXT    | First-class | Plain text, read as one continuous book from start to end. |
| PDF    | Best-effort extraction | Lekto reads the text the file already carries. A scan, or a page that is one image, has no text to extract. |

Extraction depends on the text layer inside the PDF; Lekto does not do optical
character recognition. A scanned PDF, or one built from page images, extracts
poorly or not at all. If that happens, look for an EPUB or a TXT of the same
book.

## Importing a book

1. In the [Library](/library), choose **Import**.
2. Pick a file from your device's file picker.
3. Lekto reads the file, parses it to text, stores the **Book original** and the
   parsed text, and lists the **Book** in your Library.

Import is an occasional action, so it does not block the app. It shows its
progress while it works, and if it fails it reports the reason inline with a way
to dismiss it; the Library stays usable either way. A failed import leaves
nothing behind in the Vault — no partial book and no orphaned original — so you
can simply try again.

## What Lekto keeps

An imported book is stored as two things:

- The **Book original** — the file you supplied, kept in the Vault as the book's
  attachment. It is the enduring copy: the parsed text can always be rebuilt from
  it, and it travels with a [Vault export](/vault-and-sync) so the book can be
  restored on another device.
- The parsed text — what the reader actually renders. It is a **Derived asset**:
  Lekto can regenerate it from the original, so it stays device-local and is
  never synced.

Because the original stays in the Vault, a book is never lost when its parsed
text is discarded or rebuilt; Lekto regenerates that text on demand.

Next: find your book in the [Library](/library).

## Screenshots

### Desktop

![Choosing a file to import on desktop](/images/import-desktop.png)

### Android

![Choosing a file to import on Android](/images/import-android.png)
