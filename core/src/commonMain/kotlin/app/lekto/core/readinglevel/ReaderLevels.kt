package app.lekto.core.readinglevel

import app.lekto.core.CefrLevel

/**
 * The reader's declared **Reader level** per language (CONTEXT.md; ADR-0030):
 * one CEFR band per reading language, user-authored data held in the vault.
 *
 * A language is named by its base BCP-47 tag (`en-US` → `en`) as the pack and
 * the difficulty table already collapse it (ADR-0018). A language with no level
 * set has none, so the default-mastery rule simply does not apply to it.
 */
interface ReaderLevels {

    /** The reader's level for [language]'s base tag, or `null` when it is unset (or [language] is `null`). */
    fun levelFor(language: String?): CefrLevel?

    /** Every level set, keyed by base language tag. */
    fun levels(): Map<String, CefrLevel>

    /** Sets the level for [language], replacing any previous one. A no-op language is ignored. */
    fun setLevel(language: String, level: CefrLevel)

    companion object {
        /**
         * A [ReaderLevels] that knows none and stores nothing, where no vault is
         * wired. It stands in so the default-mastery rule still composes (and
         * answers unknown) rather than the reader losing its save path.
         */
        val None: ReaderLevels = object : ReaderLevels {
            override fun levelFor(language: String?): CefrLevel? = null

            override fun levels(): Map<String, CefrLevel> = emptyMap()

            override fun setLevel(language: String, level: CefrLevel) = Unit
        }
    }
}
