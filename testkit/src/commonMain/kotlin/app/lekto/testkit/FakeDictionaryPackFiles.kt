package app.lekto.testkit

import app.lekto.core.dictionary.DictionaryPack
import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.DictionaryPackMissing
import app.lekto.core.dictionary.PackDatabase
import app.lekto.core.dictionary.PackDatabaseFactory
import app.lekto.core.dictionary.PackDownloader
import app.lekto.core.dictionary.PackRow
import app.lekto.core.vault.DerivedAssetStore

/**
 * A [PackDatabase] held in memory: the installer's state machine is proved
 * without a SQLite file, while the real reader is proved against a built pack in
 * `jvmTest`. It answers only what the installer inspects — the version and the
 * `pack_metadata` rows.
 */
class FakePackDatabase(
    override val formatVersion: Int = DictionaryPack.FORMAT_VERSION,
    private val values: Map<String, String> = testPackMetadataValues(),
) : PackDatabase {

    override fun rows(sql: String, args: List<String>): List<PackRow> = if (sql.contains("pack_metadata")) {
        values.map { (key, value) -> listOf(key, value) }
    } else {
        emptyList()
    }

    override fun close() = Unit
}

/**
 * The platform pack I/O as a fake: a download writes a marker through the
 * [derived] store and the factory opens whatever version was configured, so an
 * installer test can drive download, refusal and removal without a network or a
 * real SQLite file.
 */
class FakeDictionaryPackFiles(
    private val derived: DerivedAssetStore,
    var version: Int = DictionaryPack.FORMAT_VERSION,
) : PackDownloader,
    PackDatabaseFactory {

    /** When set, the next download fails with it; used to test graceful failure. */
    var failure: Exception? = null

    /** When set, opening the pack throws it; used to test a corrupt pack. */
    var openFailure: Exception? = null

    /** How many downloads have been attempted. */
    var downloads: Int = 0
        private set

    override fun download(url: String, destination: String) {
        failure?.let { throw it }
        downloads++
        derived.put(DictionaryPackInstaller.PACK_PATH, PACK_MARKER)
    }

    override fun open(path: String): PackDatabase {
        openFailure?.let { throw it }
        if (derived.get(DictionaryPackInstaller.PACK_PATH) == null) throw DictionaryPackMissing()
        return FakePackDatabase(version)
    }

    private companion object {
        /** Distinctive bytes, so a test can prove the pack never reaches the vault's export. */
        val PACK_MARKER = "dictionary-pack-bytes".encodeToByteArray()
    }
}
