package app.lekto.tools.dictionaries

/**
 * The **Language manifest**'s on-disk contract, owned by the producer (this
 * module).
 *
 * The number is what the reader's strict handshake compares against
 * (`LanguageManifest.FORMAT_VERSION` in `core`, issue #146). `tools/dictionaries`
 * is standalone and cannot depend on `core`, so the constant is duplicated there
 * and the two are guarded by an architecture test (ADR-0013).
 */
object LanguageManifestFormat {
    /** The manifest format this build writes. Bump on any change to the schema. */
    const val VERSION: Int = 1
}
