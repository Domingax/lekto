package app.lekto.vocabulary

import app.lekto.core.vocabulary.VocabularyEntry

/**
 * The order the Vocabulary list opened on (issue #280): the controller's
 * recently-modified order, taken once and kept until the screen leaves.
 *
 * A level change rewrites the word's record, which moves its update time, so the
 * controller would lead with that row on the next read. The reader changed a
 * level, not their reading position: the rows must not jump under their finger.
 * A word saved or synced while the screen is open is not in the opening snapshot
 * and joins at the end rather than being dropped.
 *
 * `VocabularyDestination` holds one in `remember`, so leaving the list and
 * returning takes a fresh snapshot and sorts again.
 */
internal class VocabularyOrder(entries: List<VocabularyEntry>) {

    private val opening = entries.map { entry -> entry.key }
    private val opened = opening.toSet()

    /** [entries] in the opening order, with anything new kept where it arrived. */
    fun apply(entries: List<VocabularyEntry>): List<VocabularyEntry> {
        val byKey = entries.associateBy { entry -> entry.key }
        val kept = opening.mapNotNull { key -> byKey[key] }
        val arrived = entries.filterNot { entry -> entry.key in opened }
        return kept + arrived
    }
}
