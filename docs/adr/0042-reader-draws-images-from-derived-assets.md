---
status: accepted
---

# The reader draws a book's images from derived assets, in place of one character

The EPUB parser has dropped every `<img>` since the first reader: `img` and `svg`
sit in the walk's skipped set (`core/src/jvmSharedMain/.../epub/XhtmlBlocks.kt`).
The trade-off was recorded rather than forgotten — `docs/research/epub-to-tokens-spike.md`
calls it a fidelity backlog, and `docs/research/paginated-reader-spike.md` says
what it would cost: "the model has no image block to place. Pagination would need
a non-text block kind and a height" (issue #263).

The cost is visible in real books. A figure's caption renders without its
picture. A cookbook's plates vanish. A book whose first spine document is its
cover opens on a blank page, because that document holds nothing but an image.

**An image is a block, and its bytes are a derived asset.** `TextBlock` gains an
`ImageRef`: the derived-asset name, the picture's width÷height ratio read from the
file's own header, and the source's `alt`. `BlockKind.IMAGE` marks the block. The
bytes never enter the **Vault**: the parser returns them beside the text
(`ParsedBook`), `VaultBookLibrary` writes them under `book-images/<bookId>/`, and
removes them with the book. They are a **Derived asset** in the sense ADR-0005
means, so they are never exported and never synced; each device re-derives its own
copy from the **Book original**, which ADR-0016 already carries. A wiped cache
restores pictures and text together.

**The name is a hash, not the book's path.** A vault-relative path only accepts
`[A-Za-z0-9._-]` per segment, and a real EPUB writes `images/Planche 1.png`. The
asset is therefore named after an FNV-1a hash of its archive path plus a safe
extension, which is also what makes two documents naming one picture share one
asset.

**Pagination keeps the character model.** The reader measures one `AnnotatedString`
per chapter with `TextMeasurer`, which has always accepted placeholders. An image
becomes **one character** — `IMAGE_PLACEHOLDER`, U+FFFC — whose rectangle is a
`Placeholder` sized from the text column's width and the header's ratio, capped at
half the page's height. So an image is never split across pages, a page break still
falls on a line boundary, and every offset the reader already keeps — **Reading
position**, chapter starts, word taps, selection, **Reading progress** — counts an
image as one character rather than learning a second coordinate system. An image
taller than that ceiling is scaled down to fit it rather than cut — half a plate is
unreadable — and the room left below is what lets its caption sit on the same page.

**An image cannot fail the book.** A remote `src`, an entry the archive does not
carry, a format whose header this build cannot size (SVG, for now — #269), or
bytes that do not decode, each fall back to the source's `alt`: drawn in the
placeholder's rectangle as a muted paragraph, and set as the image's
`contentDescription` when the picture does decode. An empty `alt` leaves a quiet
frame rather than a hole.

**Decoding is a platform seam, not a dependency.** Android uses `BitmapFactory`,
the desktop client uses the Skia it already links through Compose, so ADR-0011's
licence gate is untouched. The decode happens off the main thread, once per page
the picture appears on; a page turn never waits on a picture it does not show.

**Rejected alternatives**: a block-level composable layout, which rewrites
pagination and loses the character-offset model every saved position and chapter
jump is built on; storing the bytes in the **Vault** record, which bloats an
export and every sync payload with data the original already carries; re-reading
the archive at display time, which makes a page turn a second parse; scaling a
tall image to the column and letting it overflow, which cuts the plate the reader
wanted to see; and editing the book's path into a safe name, which collides two
files that differ only by an unsupported character.

**Consequences**: `BookTextCache.FORMAT_VERSION` moves to 4, so every cached book
is re-parsed once; a saved **Reading position** shifts by one character per image
before it, the risk ADR-0037 already accepted for any parser change; a page draws
only the pictures its range covers, because it hands Compose exactly the inline
content its slice names; and the two research gap tables lose their "images"
line.
