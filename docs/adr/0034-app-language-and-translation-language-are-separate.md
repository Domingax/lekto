# The app language and the translation language are separate device settings

The app shipped one hard-coded translation target — the constant `fr` in
`DictionaryRelease` (issue #18) — and an English-only interface. ADR-0033 made the
target a **Vault** record the reader owns and carries between devices. ADR-0034
then folded the interface language and the translation target into one device
setting. Neither works: a reader reading English books wants the **interface in
English** and the **translations in French**, and one setting cannot hold both.

Two settings, both device-local app configuration (ADR-0023's pattern — one JSON
document through the app-private byte seam, beside the vault and the derived
assets, never a **Vault** record):

- the **app language** — the language of the app's own texts;
- the **Translation language** — the language every translation resolves into:
  the reader's offline lookup, the tokeniser's lemma index (ADR-0006), the
  **Vocabulary** list (ADR-0032), the **LLM provider** phrase prompt (ADR-0022)
  and the **Dictionary shortcut** / **Translation shortcut** URLs.

Neither enters an export or a sync, and neither follows the reader between
devices; the reader sets them again, as they set their **LLM provider** again.
This **supersedes ADR-0033**, whose Vault record for the target is the wrong home
for a device's language, and reverses the single-setting step ADR-0034 took
before it.

The **app language** offers the languages the app ships — English and French — and
defaults at first launch to the device's language when the app ships it, and to
English otherwise.

The **translation language** offers the languages the **Language manifest**
publishes a pack into — the pairs the pipeline builds (ADR-0031, issue #149) — and
defaults at first launch to the device's language when a pack translates into it,
and to nothing otherwise, the reader choosing on the **Languages** surface. A
language with no published pack behaves as "no pack": the lookup reports it
honestly and the **Vocabulary** list falls back to the stored translation
(ADR-0032). The two settings are independent: the shipped interface languages and
the published translation languages need not coincide.

We rejected **one setting for both**, the whole point being that they differ. We
rejected the ADR-0033 model — the target as a **Vault** record that exports and
syncs — because a device's display and translation languages are that device's
own configuration. We rejected **only the translation language**, with the
interface left English: the reader asked for the whole app in their language, and
the interface follows it. We rejected **a target inferred from the book's
language** (an English book translated into English is meaningless) and **from the
system locale read at every lookup** (a preference that follows the reader should
not change when the phone's language changes).

**Consequences**: `DictionaryRelease.TARGET_LANGUAGE` and the fixed resolution it
fed are removed, and every translation surface reads the chosen translation
language instead. The reference shortcuts address the chosen pair, so the URL
builders must know the languages the manifest publishes — including the Spanish
the pair set already offers (issue #149), which the builders' English/French-only
name table does not know today. The app must externalise its own texts so the
**app language** can select them; that work is staged in its own issue (#165), and
until it lands the interface stays English while the translation language already
drives every lookup.
