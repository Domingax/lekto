package app.lekto.core.language

/**
 * The published **Language manifest**'s format handshake (issue #145).
 *
 * The manifest is the catalogue of what the pipeline offers for reading: each
 * **Reading language** and the artefacts published for it — a **Difficulty
 * table** per reading language, and a **Dictionary pack** per (Reading language
 * × **Translation language**) pair, carrying its translation language. It is
 * built and published by `tools/dictionaries` from its own output and fetched
 * from one stable, date-free release alias, then cached as a **Derived asset**
 * (issue #146).
 *
 * [FORMAT_VERSION] is declared twice — once by the producer
 * (`LanguageManifestFormat.VERSION`, which depends on no Lekto module) and once
 * here, because `core` cannot depend on the tool — so a synchronisation test in
 * `architecture` keeps the two in step (ADR-0013). A manifest this build cannot
 * read is refused rather than misread.
 */
object LanguageManifest {
    /** The manifest format this reader understands, duplicated from the producer (ADR-0013). */
    const val FORMAT_VERSION: Int = 1
}
