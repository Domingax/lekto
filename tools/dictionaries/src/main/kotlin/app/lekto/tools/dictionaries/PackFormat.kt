package app.lekto.tools.dictionaries

/**
 * The dictionary pack's on-disk contract, owned by the producer (this module).
 *
 * The number is written to SQLite's `user_version` and is what the reader's
 * strict version handshake compares against (`DictionaryPack.FORMAT_VERSION` in
 * `core`, later work). `tools/dictionaries` is standalone and cannot depend on
 * `core`, so the constant is duplicated there and the two are guarded by an
 * architecture test when the reader lands (ADR-0013).
 */
object PackFormat {
    /** The pack format this build writes. Bump on any change to the schema or content model. */
    const val VERSION: Int = 1

    /** The source and target language codes the pack is built for. */
    const val ENGLISH: String = "en"

    /** Languages are opaque strings; the pack keeps English lemmas with French translations. */
    const val FRENCH: String = "fr"
}
