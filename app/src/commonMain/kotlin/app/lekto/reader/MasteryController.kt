package app.lekto.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.LocalChanges
import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.defaultMasteryLookup
import app.lekto.core.difficulty.WordDifficulty
import app.lekto.core.readinglevel.ReaderLevels
import app.lekto.core.text.WordKey
import app.lekto.core.text.baseLanguage
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.vocabulary.VocabularyController

/**
 * The reader's single source of a word's **Mastery level** (ADR-0030): it
 * composes the saved **Vocabulary entry**, the reader's **Reader level** and the
 * **Difficulty table**, and owns the one [revision] the reader keys its word
 * layer on — so a save, a delete and a level change all recolour the open book at
 * once, with no toast, and the reader never reads a store itself.
 *
 * A saved entry's level always wins; otherwise the level derives from the word's
 * difficulty band and the reader's band; otherwise it is unknown, exactly as
 * before the feature (ADR-0030). The saved vocabulary's own state (the list, the
 * search, the delete) stays on [VocabularyController]; this controller delegates
 * to it and layers the derived default on top.
 *
 * The difficulty table is read through [difficulty], a function of the word's
 * language rather than a value, because there is one per reading language and each
 * is installed at runtime (issue #148); the levels are kept in snapshot
 * state so a change is observable, and loaded from the vault once at start.
 */
@Suppress("TooManyFunctions") // Each method is one step of the composition: a save, a delete, a level edit, a refresh.
class MasteryController(
    private val vocabulary: VocabularyController,
    private val levels: ReaderLevels,
    private val difficulty: (String?) -> WordDifficulty?,
    private val changes: LocalChanges,
) {

    private val declared = mutableStateMapOf<String, CefrLevel>()

    /** Bumped on every save, delete, refresh or level change, so a memoised reader recolours at once. */
    var revision by mutableStateOf(0)
        private set

    init {
        reloadLevels()
    }

    /** The level to paint each word: saved first, then derived, then unknown. */
    val mastery: MasteryLookup = defaultMasteryLookup(
        saved = { key -> vocabulary.entryFor(key)?.mastery },
        readerLevel = { language -> language?.let { code -> declared[baseLanguage(code)] } },
        difficulty = { key -> difficulty(key.language)?.levelOf(key) },
    )

    /** The saved entry for [key], or `null` when the word is unsaved. */
    fun entryFor(key: WordKey): VocabularyEntry? = vocabulary.entryFor(key)

    /** Every saved entry, ordered by surface then key, so the vocabulary list is stable. */
    fun all(): List<VocabularyEntry> = vocabulary.all()

    /** The reader's level for [language], or `null` when it is unset. */
    fun levelFor(language: String?): CefrLevel? = language?.let { code -> declared[baseLanguage(code)] }

    /** Every declared level, keyed by base language tag. */
    fun levels(): Map<String, CefrLevel> = declared.toMap()

    /**
     * The level a fresh save starts at (ADR-0030): the level the word currently
     * shows, or the familiar level when the word has no signal — so a save never
     * appears to demote a word and an unsignalled word is not saved as unknown.
     */
    fun freshSaveLevel(key: WordKey): MasteryLevel {
        val hasSignal = difficulty(key.language)?.levelOf(key) != null && levelFor(key.language) != null
        return if (hasSignal) mastery.levelOf(key) else MasteryLevel.FAMILIAR
    }

    /** Saves [entry] to the vault and folds it into the reader's state. */
    fun save(entry: VocabularyEntry) {
        vocabulary.save(entry)
        revision++
    }

    /** Deletes [key]'s entry from the vault and the reader's state. */
    fun delete(key: WordKey) {
        vocabulary.delete(key)
        revision++
    }

    /**
     * Re-reads the vault and folds it into the reader's state, bumping [revision]
     * so a word or a level the sync moved is shown without relaunching the app.
     */
    fun refresh() {
        vocabulary.refresh()
        reloadLevels()
        revision++
    }

    /**
     * Sets the reader's level for [language], a vault write (ADR-0030), and bumps
     * [revision] so the open book recolours at once. It is a content change, so
     * sync pushes it.
     */
    fun setLevel(language: String, level: CefrLevel) {
        levels.setLevel(language, level)
        declared[baseLanguage(language)] = level
        revision++
        changes.contentChanged()
    }

    /**
     * Clears the reader's level for [language]: the vault record is **deleted**
     * (issue #147; ADR-0030), and [revision] bumps so the open book recolours at
     * once. It is a content change, so sync pushes it.
     */
    fun clearLevel(language: String) {
        levels.clearLevel(language)
        declared.remove(baseLanguage(language))
        revision++
        changes.contentChanged()
    }

    /** Bumps [revision] without a write, so a change to the difficulty table recolours the reader. */
    fun invalidate() {
        revision++
    }

    private fun reloadLevels() {
        declared.clear()
        levels.levels().forEach { (language, level) -> declared[language] = level }
    }
}
