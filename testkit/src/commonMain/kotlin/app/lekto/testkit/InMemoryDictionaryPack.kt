package app.lekto.testkit

import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionaryPack
import app.lekto.core.dictionary.PackMetadata

/**
 * An in-memory [DictionaryPack] for tests: the pack's behaviour without a SQLite
 * file, so the domain's lookup and mastery keying are proved in `commonTest`,
 * while the real reader is proved against a built pack in `jvmTest`
 * (docs/testing.md, "Test levels").
 *
 * A surface the map does not name has no lemma, so keying falls back to the
 * normalised surface form exactly as the production pack does (ADR-0006).
 */
class InMemoryDictionaryPack(
    override val metadata: PackMetadata = testPackMetadata(),
    private val entries: Map<Pair<String, String>, List<DictionaryEntry>> = testEntries(),
    private val lemmas: Map<Pair<String, String>, String> = testLemmas(),
) : DictionaryPack {

    override fun entries(language: String, lemma: String): List<DictionaryEntry> = entries[language to lemma].orEmpty()

    override fun lemmaOf(surface: String, language: String): String? = lemmas[language to surface]

    override fun close() = Unit
}
