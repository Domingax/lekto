package app.lekto.testkit

import app.lekto.core.CefrLevel
import app.lekto.core.difficulty.DifficultyMetadata
import app.lekto.core.difficulty.WordDifficulty
import app.lekto.core.text.WordKey
import app.lekto.core.text.baseLanguage

/**
 * An in-memory [WordDifficulty] for tests: the table's
 * behaviour without a SQLite file, so the default-mastery composition is proved
 * in `core/commonTest`, while the real reader is proved against a built artifact
 * in `jvmTest` (docs/testing.md, "Test levels").
 *
 * A key the map does not name has no band, so it reads as unknown exactly as a
 * word absent from the production table does (ADR-0029).
 */
class InMemoryWordDifficulty(
    override val metadata: DifficultyMetadata = testDifficultyMetadata(),
    private val bands: Map<Pair<String, String>, CefrLevel> = testDifficultyBands(),
) : WordDifficulty {

    override fun levelOf(key: WordKey): CefrLevel? =
        key.language?.let(::baseLanguage)?.let { language -> bands[language to key.key] }

    override fun close() = Unit
}
