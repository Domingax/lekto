package app.lekto.core.vocabulary

import app.lekto.core.text.WordKey
import kotlin.time.Instant

/**
 * The user's saved words (CONTEXT.md, "Vocabulary entry"; issue #22), keyed by
 * the [WordKey] that gives them their identity (ADR-0006).
 *
 * The seam is synchronous — a save is a vault write, which is blocking — so the
 * caller that must not block the UI runs it off the main thread. The reader
 * reads a [app.lekto.core.MasteryLookup] built from these entries, never the
 * store itself.
 *
 * Recency is the **vault record's** `updatedAt`, not a field on the entry
 * (ADR-0050): [allWithTimes] exposes it and [save] reports the time it wrote, so
 * the list can order itself recently-modified without the entry growing a
 * timestamp the sync would then have to settle.
 */
interface Vocabulary {

    /** Every saved entry, in no particular order. */
    fun all(): List<VocabularyEntry>

    /** Every saved entry with the moment its vault record last changed, in no particular order. */
    fun allWithTimes(): List<VocabularyEntryTime>

    /** The entry whose key is [key], or `null` when the word is unsaved. */
    fun entryFor(key: WordKey): VocabularyEntry?

    /**
     * Writes [entry], replacing the entry for the same key, and returns the moment
     * its record was stamped. The key is what makes a save idempotent: saving an
     * inflection updates its lemma's entry rather than adding a second one.
     */
    fun save(entry: VocabularyEntry): Instant

    /**
     * Deletes the entry for [key]; an absent key is not an error. The record is
     * removed from the vault, so the word is gone when the vault is read again,
     * including after a restart, and a **tombstone** is left in the sync engine's
     * own store (ADR-0015), so the deletion travels to another device instead of
     * being copied back on the next sync.
     */
    fun delete(key: WordKey)
}

/**
 * A saved entry and the moment its vault record last changed: the recency the
 * Vocabulary list orders by (issue #280; ADR-0050).
 */
data class VocabularyEntryTime(val entry: VocabularyEntry, val updatedAt: Instant)
