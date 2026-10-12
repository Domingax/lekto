package app.lekto.testkit

import app.lekto.core.dictionary.DictionaryPackMissing
import app.lekto.core.dictionary.PackDatabase
import app.lekto.core.dictionary.PackDatabaseFactory
import app.lekto.core.dictionary.PackDownloader
import app.lekto.core.dictionary.PackRow
import app.lekto.core.difficulty.DifficultyInstaller
import app.lekto.core.difficulty.WordDifficulty
import app.lekto.core.vault.DerivedAssetStore

/**
 * A [PackDatabase] held in memory: the table's installer state machine is proved
 * without a SQLite file, while the real reader is proved against a built artifact
 * in `jvmTest`. It answers only what the installer inspects — the version and the
 * `difficulty_metadata` rows.
 */
class FakeDifficultyDatabase(
    override val formatVersion: Int = WordDifficulty.FORMAT_VERSION,
    private val values: Map<String, String> = testDifficultyMetadataValues(),
) : PackDatabase {

    override fun rows(sql: String, args: List<String>): List<PackRow> = if (sql.contains("difficulty_metadata")) {
        values.map { (key, value) -> listOf(key, value) }
    } else {
        emptyList()
    }

    override fun close() = Unit
}

/**
 * The table's platform I/O as a fake: a download writes a marker through the
 * [derived] store and the factory opens whatever version was configured, so an
 * installer test can drive download, refusal and removal without a network or a
 * real SQLite file.
 */
class FakeDifficultyFiles(
    private val derived: DerivedAssetStore,
    val language: String = "en",
    var version: Int = WordDifficulty.FORMAT_VERSION,
) : PackDownloader,
    PackDatabaseFactory {

    /** When set, the next download fails with it; used to test graceful failure. */
    var failure: Exception? = null

    /** When set, opening the table throws it; used to test a corrupt table. */
    var openFailure: Exception? = null

    /** How many downloads have been attempted. */
    var downloads: Int = 0
        private set

    override fun download(url: String, destination: String) {
        failure?.let { throw it }
        downloads++
        derived.put(DifficultyInstaller.pathOf(language), MARKER)
    }

    override fun open(path: String): PackDatabase {
        openFailure?.let { throw it }
        if (derived.get(DifficultyInstaller.pathOf(language)) == null) {
            throw DictionaryPackMissing()
        }
        return FakeDifficultyDatabase(version)
    }

    private companion object {
        /** Distinctive bytes, so a test can prove the table never reaches the vault's export. */
        val MARKER = "difficulty-table-bytes".encodeToByteArray()
    }
}
