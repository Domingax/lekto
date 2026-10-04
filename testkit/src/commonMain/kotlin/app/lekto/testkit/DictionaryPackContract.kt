package app.lekto.testkit

import app.lekto.core.dictionary.DictionaryPack

/**
 * The specification of the [DictionaryPack] seam, as executable cases: what a
 * lemma query, a surface→lemma lookup and the metadata handshake must return.
 *
 * It is framework-free, so the in-memory pack runs it in `commonTest` and the
 * real SQLite reader runs the same cases against a built pack in `jvmTest` (and
 * its Android twin in `androidHostTest`) — the same behaviours, so a reader
 * cannot silently differ (docs/testing.md, "Test levels"). Each case opens a
 * fresh pack from [newPack] and closes it, so cases cannot leak state.
 */
class DictionaryPackContract(private val newPack: () -> DictionaryPack) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unknown lemma has no entries") {
            use { pack -> expectTrue(pack.entries("en", "zzzz").isEmpty(), "an unknown lemma must be empty") }
        },
        ContractCase("a known lemma returns its senses, translations and pronunciation") {
            use { pack ->
                val entry = pack.entries("en", BLORPLE.lemma).first()
                expectEquals(
                    BLORPLE.senses.map { sense -> sense.definition },
                    entry.senses.map { sense -> sense.definition },
                    "the definitions in order",
                )
                expectEquals(BLORPLE.translations, entry.translations, "the translations in order")
                expectEquals(BLORPLE.pronunciation, entry.pronunciation, "the pronunciation")
            }
        },
        ContractCase("an inflected surface resolves to its lemma") {
            use { pack -> expectEquals("blorple", pack.lemmaOf("blorpled", "en"), "the lemma of 'blorpled'") }
        },
        ContractCase("an unknown surface has no lemma") {
            use { pack -> expectTrue(pack.lemmaOf("zzzz", "en") == null, "an unknown surface must have no lemma") }
        },
        ContractCase("a lemma in another language is not returned") {
            use { pack ->
                expectTrue(pack.entries("fr", BLORPLE.lemma).isEmpty(), "the language must separate entries")
            }
        },
        ContractCase("the metadata carries the format version and the licence") {
            use { pack ->
                expectEquals(DictionaryPack.FORMAT_VERSION, pack.metadata.formatVersion, "the format version")
                expectEquals("CC BY-SA 4.0", pack.metadata.license, "the licence")
                expectTrue(pack.metadata.attribution != null, "the attribution")
            }
        },
    )

    private inline fun <T> use(block: (DictionaryPack) -> T): T {
        val pack = newPack()
        try {
            return block(pack)
        } finally {
            pack.close()
        }
    }
}
