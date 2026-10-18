# Reading preferences are app-private settings outside the vault

The reader's styles are fixed: `ReaderStyles.Reading` is the only one — a serif face at 18.sp with
a 1.7 leading and one ink colour — the horizontal margin is a constant, and nothing lets the reader
change the face, the size, the leading or the margins. Issue #141 drew the boundary between the
three kinds of persisted data the app already had — the **Vault**, app-private configuration
(ADR-0023) and **Derived assets** (ADR-0005) — and left reading customisation outside all three,
because it fits none of them. This settles where it goes.

A **Reading preference** is how Lekto renders a book's text for the reader on this device: the
face, the size, the leading and the margins. It is app-private device configuration on ADR-0023's
pattern — one JSON document through the app-private byte seam, beside the vault and the derived
assets, never a **Vault** record and never a secret. It enters no export and no sync, and a second
device starts at the defaults; the reader sets it again, as they set their **LLM provider** and
their **App language** again (ADR-0023, ADR-0034).

The sharpest contrast is the **Reader level**, which went the other way (ADR-0030) — and it is the
right contrast, because the two do not share a requirement. The level must hold on every device or
the same book reads differently on each, since the default-mastery rule colours the text from it. A
face, a size, a leading and a margin carry no such obligation: a size is chosen for a screen, and a
margin for a screen's width. And sync is off by default (ADR-0009), so a Vault record would reach a
second device only when the reader has turned syncing on — the wrong price for a preference.

The preference is **general**, not per **Reading language**. A reader's preferred face and size do
not change with the book's language, and the platform's font stacks already resolve per script: the
serif the reader defaults to is a script-appropriate face, not a Latin one.

We rejected the **Vault** record, for ADR-0005's reason and one more: it would export and sync, and
a preference is a choice about the device rather than a fact about its reader. We rejected the
**per Reading language** preference, because the script problem is already solved a layer down and
a per-language picker for a font size is a screen nobody asked for. We rejected a **bundled font
file**: it is a new shipped artefact with its own licence notice, and it buys what the platform's
faces already give; a real request — a dyslexia-friendly face, say — can still carry one. We
rejected **two controls for size and leading**, because the leading is a ratio of the size, the
reader's single style already holds it that way, and two controls that fight each other make a
worse screen than one. We rejected **replacing the platform font scale** rather than multiplying by
it: the reader already repaginates on `density.fontScale`, and ignoring it would break the
accessibility the reader has today.

**Consequences**: `ReaderStyles.Reading` stops being the only style and becomes the defaults a
`ReadingPreferences` value starts from, so the reader builds its styles from the value and tests
that need a fixture build one from the defaults. A change repaginates the open book at once, with
no toast, the way a **Reader level** change recolours it, and the **Reading position** is a
character offset chosen for exactly this reflow, so the reader keeps their place. The chosen size
stays in `sp`, so the platform's font scale still multiplies it. The controls live in the reader,
where the effect is visible and can be previewed as it is changed, rather than in Settings, which
the reader would have to leave the book to reach; the navigation/UX rework may replace the reader's
chrome, and the control is part of that chrome either way (issue #169).

**Not decided here**: the **theme** — light, dark and sepia. It recolours the whole app rather
than the book's text, so it is not a reading preference, and whether it is device configuration or
reader-owned data that follows the reader is its own question (issue #168). This ADR is not the
answer to it, and the theme must not inherit this one by default.
