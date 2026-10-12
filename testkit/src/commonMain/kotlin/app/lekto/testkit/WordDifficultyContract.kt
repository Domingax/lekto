package app.lekto.testkit

import app.lekto.core.CefrLevel
import app.lekto.core.difficulty.WordDifficulty
import app.lekto.core.text.WordKey

/**
 * The specification of the [WordDifficulty] seam, as executable cases: what a
 * band query and the metadata handshake must return (ADR-0029).
 *
 * It is framework-free, so the in-memory table runs it in `commonTest` and the
 * real SQLite reader runs the same cases against a built artifact in `jvmTest`
 * (and its Android twin in `androidHostTest`) — the same behaviours, so a reader
 * cannot silently differ (docs/testing.md, "Test levels"). Each case opens a
 * fresh table from [newTable] and closes it, so cases cannot leak state.
 */
class WordDifficultyContract(private val newTable: () -> WordDifficulty) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("a known word returns its difficulty band") {
            use { table ->
                expectEquals(CefrLevel.B1, table.levelOf(WordKey("en", "blorple")), "the band of 'blorple'")
            }
        },
        ContractCase("an unknown word has no band") {
            use { table -> expectTrue(table.levelOf(WordKey("en", "zzzz")) == null, "an unknown word must be null") }
        },
        ContractCase("a word with no language has no band") {
            use { table -> expectTrue(table.levelOf(WordKey(null, "blorple")) == null, "a null language must be null") }
        },
        ContractCase("a band in another language is not returned") {
            use { table ->
                expectTrue(table.levelOf(WordKey("fr", "blorple")) == null, "the language must separate bands")
            }
        },
        ContractCase("the metadata carries the format version and the licence") {
            use { table ->
                expectEquals(WordDifficulty.FORMAT_VERSION, table.metadata.formatVersion, "the format version")
                expectEquals("CC BY-SA 4.0", table.metadata.license, "the licence")
                expectTrue(table.metadata.attribution?.contains("Robyn Speer") == true, "the wordfreq attribution")
            }
        },
    )

    private inline fun <T> use(block: (WordDifficulty) -> T): T {
        val table = newTable()
        try {
            return block(table)
        } finally {
            table.close()
        }
    }
}
