package app.lekto.core.vocabulary

import app.lekto.core.text.WordKey

/**
 * The user's saved words (CONTEXT.md, "Vocabulary entry"; issue #22), keyed by
 * the [WordKey] that gives them their identity (ADR-0006).
 *
 * The seam is synchronous — a save is a vault write, which is blocking — so the
 * caller that must not block the UI runs it off the main thread. The reader
 * reads a [app.lekto.core.MasteryLookup] built from these entries, never the
 * store itself.
 */
interface Vocabulary {

    /** Every saved entry, in no particular order. */
    fun all(): List<VocabularyEntry>

    /** The entry whose key is [key], or `null` when the word is unsaved. */
    fun entryFor(key: WordKey): VocabularyEntry?

    /**
     * Writes [entry], replacing the entry for the same key. The key is what
     * makes a save idempotent: saving an inflection updates its lemma's entry
     * rather than adding a second one.
     */
    fun save(entry: VocabularyEntry)

    /**
     * Deletes the entry for [key]; an absent key is not an error. The record is
     * removed from the vault, so the word is gone when the vault is read again,
     * including after a restart. No tombstone is written yet, so the deletion
     * cannot travel to another device until the sync engine lands and writes one
     * (ADR-0015).
     */
    fun delete(key: WordKey)
}
