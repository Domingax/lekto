package app.lekto.testkit

import app.lekto.core.CefrLevel
import app.lekto.core.difficulty.DifficultyMetadata
import app.lekto.core.difficulty.WordDifficulty

/**
 * The bands every [app.lekto.core.difficulty.WordDifficulty] test knows, matching
 * the committed SQLite fixture the difficulty pipeline produces from
 * `tools/dictionaries/src/test/resources/difficulty-frequency-sample.tsv`.
 *
 * "blorple" is a B1 word and "zzzz" is absent, so a case can prove a known band,
 * a word with no band, and language separation.
 */
fun testDifficultyBands(): Map<Pair<String, String>, CefrLevel> = mapOf(
    ("en" to "blorple") to CefrLevel.B1,
)

/** The table metadata a test build carries, matching the producer's attribution rows. */
fun testDifficultyMetadata(formatVersion: Int = WordDifficulty.FORMAT_VERSION): DifficultyMetadata =
    DifficultyMetadata.from(formatVersion, testDifficultyMetadataValues())

/** The raw `difficulty_metadata` rows, for a fake [app.lekto.core.dictionary.PackDatabase]. */
fun testDifficultyMetadataValues(): Map<String, String> = mapOf(
    "format_version" to WordDifficulty.FORMAT_VERSION.toString(),
    "attribution" to "wordfreq by Robyn Speer",
    "license" to "CC BY-SA 4.0",
    "license_url" to "https://creativecommons.org/licenses/by-sa/4.0/",
    "source" to "https://github.com/rspeer/wordfreq",
    "source_data" to "wordfreq (Google Books Ngram Viewer, OpenSubtitles, SUBTLEX)",
    "modifications" to "Frequencies aggregated onto lemmas and re-expressed as CEFR bands.",
    "notice" to "Derived from wordfreq by Robyn Speer, CC BY-SA 4.0.",
)
