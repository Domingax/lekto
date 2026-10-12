package app.lekto.tools.dictionaries

/**
 * The **Difficulty table**'s on-disk contract, owned by the producer (this module).
 *
 * The number is written to SQLite's `user_version` and is what the reader's
 * strict version handshake compares against (`WordDifficulty.FORMAT_VERSION` in
 * `core`, ADR-0029). `tools/dictionaries` is standalone and cannot depend on
 * `core`, so the constant is duplicated there and the two are guarded by an
 * architecture test (ADR-0013).
 */
object DifficultyFormat {
    /** The table format this build writes. Bump on any change to the schema or content model. */
    const val VERSION: Int = 1

    /** The reading language this build produces a difficulty table for. */
    const val ENGLISH: String = "en"
}
