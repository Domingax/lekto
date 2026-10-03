# A book's original is a vault attachment, and the vault format gains a version

A **Book** is a vault record of kind `books` whose JSON body is the book's metadata,
plus the book's **original file** stored as that record's **attachment** — one binary
file per record under the reserved `_attachments` directory. ADR-0014 anticipated this
exact moment: "when [book originals] do, a `formatVersion` bump extends the bundle to
attachments". The vault format therefore moves to **version 2**, and an older build
refuses a version-2 vault rather than guessing at the bytes it cannot see.

The alternative was to keep the original **outside** the vault, in the derived store,
because it is large and never authored by the user. We rejected it: the derived store
is *device-local and never exported* (ADR-0005), so a book's original would be lost on
a reinstall or a device move — but ADR-0005 lists "book originals" as vault content
precisely because the product's portability promise is that export moves everything the
user imported. Parsed text is reproducible from the original and stays derived; the
original is not reproducible and must be carried.

An attachment is stored and removed with its record and has no identity of its own:
`VaultStore.remove(id)` takes the attachment too, and `exportBundle` carries it. In the
export it is **Base64 within the JSON bundle**, because ADR-0014 chose one versioned
JSON document as the portability artifact — a book of a few megabytes encodes to a few
megabytes of text, which is acceptable for an occasional export and avoids re-opening
ADR-0014's rejected ZIP container.

**Consequences**: `VaultStore` gains `putAttachment`/`getAttachment`/`removeAttachment`;
the reserved `_attachments` segment cannot collide with a record because a record
`kind` must start alphanumeric ([`VaultPaths`]). A vault written by a version-2 build is
unreadable by a version-1 build, which is the intended compatibility gate. Parsed book
text remains a derived asset (ADR-0005), so opening a book reads the cache and re-parses
from the attachment when it is gone. The TXT/EPUB split stays behind ADR-0007's
`BookTextParser` seam; a TXT file is parsed by `TxtParser`, and neither the library nor
the reader learns which format it opened.
