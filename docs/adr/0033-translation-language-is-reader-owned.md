---
status: superseded by ADR-0034
---

# The reader owns their translation language, and it lives in the vault

The app shipped one hard-coded translation target — the constant `fr` in
`DictionaryRelease` (issue #18) — because the first pack was English→French. That
constant feeds every translation the app makes: the reader's offline lookup, the
tokeniser's lemma index (ADR-0006), the **Vocabulary** list (ADR-0032), the **LLM
provider** phrase prompt (ADR-0022), and the **Dictionary shortcut** URLs. So a
reader who does not want French has no way to say so, and an installed
English→Spanish pack is never queried (issue #67).

The **Translation language** becomes the reader's own: the language they want
translations in, chosen once for the whole app. It is stored as a **Vault**
record of its own, as the **Reader level** is (ADR-0030), so it exports and syncs
with the rest of the reader's data and the same reader on two devices translates
the same way. The stored `fr` is gone.

At first launch it defaults to the device's language when the **Language
manifest** publishes a pack that translates into it, and to nothing otherwise. An
unset language behaves as "no pack", so the reader falls back to the stored
translation (ADR-0032) until they choose on the **Languages** surface. The first
launch asks nothing: it must stay a clear path to the first tapped word, with no
wall of settings (issue #55, #56).

We rejected **reading the device's language at every lookup**: a preference that
follows the reader should not change when the phone's language changes, and a
French speaker with an English phone would silently get English. We rejected
keeping it as device-local app configuration the way the **LLM provider** is
(ADR-0023): a translation target is reader data, not a per-device secret or a
transport, and it belongs with the reader's level and vocabulary. We rejected one
target per **Reading language**: a reader who knows French wants French for their
English and their Spanish books alike.

**Consequences**: `DictionaryRelease.TARGET_LANGUAGE` and the fixed resolution it
feeds are removed, and every translation surface reads the chosen language
instead. The reference shortcuts address the chosen pair, so the URL builders
must know the languages the manifest publishes — including the Spanish the pair
set already offers (issue #149), which the builders' English/French-only name
table does not know today. This ADR settles only the translation language; where
the rest of the reading preferences live stays open (#151).
