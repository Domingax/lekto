package app.lekto.vocabulary

import app.lekto.core.dictionary.WordLookup
import app.lekto.core.dictionary.firstGloss
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry

/**
 * The glosses the installed **Dictionary packs** answer for [entries] (ADR-0032),
 * keyed by each entry's identity: the entry is queried in its own **Reading
 * language**'s pack and the offline result's first gloss kept ([firstGloss]). An
 * entry [lookup] cannot answer — no pack installed, the pack does not know the
 * word, or the pack is incompatible or corrupt — is absent, so the **Vocabulary**
 * list falls back to the entry's stored translation.
 *
 * [lookup] resolves a (reading language × **Translation language**) surface to its
 * offline result, so the caller captures the reader's chosen translation language.
 * The pack query is blocking, so the caller runs the whole batch off the UI
 * thread. The pack is only read: nothing here reaches the **Vault**, so the
 * derived gloss is a **Derived asset** (ADR-0005) and one entry can read
 * differently on two devices with different packs installed.
 */
fun packTranslations(
    entries: List<VocabularyEntry>,
    lookup: (readingLanguage: String?, surface: String) -> WordLookup,
): Map<WordKey, String> = entries.mapNotNull { entry ->
    lookup(entry.key.language, entry.surface).firstGloss()?.let { gloss -> entry.key to gloss }
}.toMap()
