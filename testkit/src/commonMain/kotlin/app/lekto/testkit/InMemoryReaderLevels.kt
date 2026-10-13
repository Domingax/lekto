package app.lekto.testkit

import app.lekto.core.CefrLevel
import app.lekto.core.readinglevel.ReaderLevels
import app.lekto.core.text.baseLanguage

/**
 * An in-memory [ReaderLevels] for tests: the app's composition and UI are proved
 * without a vault, while the vault-backed implementation is proved in
 * `core/commonTest` (docs/testing.md, "Test levels").
 */
class InMemoryReaderLevels(levels: Map<String, CefrLevel> = emptyMap()) : ReaderLevels {

    private val declared = LinkedHashMap<String, CefrLevel>().apply {
        levels.forEach { (language, level) -> put(baseLanguage(language), level) }
    }

    override fun levelFor(language: String?): CefrLevel? = language?.let { code -> declared[baseLanguage(code)] }

    override fun levels(): Map<String, CefrLevel> = declared.toMap()

    override fun setLevel(language: String, level: CefrLevel) {
        if (language.isBlank()) return
        declared[baseLanguage(language)] = level
    }

    override fun clearLevel(language: String) {
        if (language.isBlank()) return
        declared.remove(baseLanguage(language))
    }
}
