package app.lekto.tools.dictionaries

import kotlinx.serialization.Serializable

/**
 * One **Word difficulty** row: the band a word key falls in, for a language
 * (ADR-0029). The key is the same identity the tokeniser computes — the lemma
 * when the pack knows one, the normalised surface form otherwise — so difficulty
 * and mastery never disagree on a word's identity (ADR-0006).
 */
@Serializable
internal data class DifficultyRow(val word: String, val language: String, val band: String)

/** The measured shape of one difficulty build, recorded in the manifest. */
@Serializable
internal data class DifficultyStats(val frequencyForms: Long, val wordKeys: Int, val bandedWords: Int)

/** The band cut points, in order A1…C2, as cumulative word-key counts. */
@Serializable
internal data class DifficultyCalibration(val cutSizes: List<Int>, val source: String, val validation: String)

/**
 * The in-memory difficulty table: the deterministic output of one transform
 * pass. The SQLite writer serialises it; the golden test pins its canonical JSON.
 */
@Serializable
internal data class DifficultyContent(
    val provenance: PackProvenance,
    val calibration: DifficultyCalibration,
    val stats: DifficultyStats,
    val rows: List<DifficultyRow>,
)

/** The difficulty table's canonical JSON form: stable ordering, no timestamps, no wall clock. */
internal fun DifficultyContent.canonicalJson(): String = packJson.encodeToString(this)

/** The difficulty release manifest: provenance, calibration and the measured shape. */
@Serializable
internal data class DifficultyManifest(
    val formatVersion: Int,
    val provenance: PackProvenance,
    val calibration: DifficultyCalibration,
    val stats: DifficultyStats,
)
