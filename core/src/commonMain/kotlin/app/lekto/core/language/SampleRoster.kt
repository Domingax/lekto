package app.lekto.core.language

/**
 * The embedded roster (issue #298): the **Reading language**s the first launch
 * guides the reader through, generated at build from the **Sample catalogue**
 * (#297) — the catalogue's reading languages, in the catalogue's order. A
 * language is in the catalogue only when its sample has a published
 * **Difficulty table** and at least one **Dictionary pack**, so the roster a
 * build ships is the list the guide renders with no read, no spinner and no
 * network.
 *
 * The roster bounds the list; the manifest corrects it. Adding a language
 * needs a sample, so only a build can add; removing one from the manifest
 * drops it at the next read, with no rebuild.
 */
object SampleRoster {

    /**
     * The reading languages this build ships a sample for, in the committed
     * catalogue's order. `CommittedSampleCatalogTest` proves it matches the
     * catalogue the build actually ships, so the two cannot drift apart.
     */
    val LANGUAGES: List<String> = listOf("de", "en", "es", "fr", "it", "ja", "pt", "tr")
}

/**
 * The roster as offered languages: each code named by [readingLanguageName],
 * carrying no artefact until a manifest is read. The guide renders these with
 * no read; the **Languages** surface lists them with `List not loaded` until
 * the manifest arrives.
 */
fun sampleRosterLanguages(): List<OfferedLanguage> = SampleRoster.LANGUAGES.map { language ->
    OfferedLanguage(
        language = language,
        displayName = readingLanguageName(language),
        difficulty = null,
        dictionaries = emptyList(),
    )
}

/**
 * The one predicate both surfaces share (issue #298): the guide's first screen
 * and the **Languages** surface return the same list for the same inputs.
 *
 * [manifest] is the catalogue read, or `null` when it has not been read: the
 * roster alone then, so a first launch with no network still lists every
 * language. When read, the list is the intersection in roster order — a
 * language the manifest removed disappears, and one it added waits for a build
 * that ships its sample. An empty intersection is empty, so the caller can say
 * so and point at Settings ▸ Languages.
 */
fun offeredLanguages(roster: List<OfferedLanguage>, manifest: List<OfferedLanguage>?): List<OfferedLanguage> {
    if (manifest == null) return roster
    val byLanguage = manifest.associateBy(OfferedLanguage::language)
    return roster.mapNotNull { offered -> byLanguage[offered.language] }
}

/**
 * The same predicate over the catalogue's own state: a refused or malformed
 * manifest reads [LanguageCatalogState.Unavailable], so the roster's list
 * survives a bad fetch intact.
 */
fun offeredLanguages(roster: List<OfferedLanguage>, state: LanguageCatalogState): List<OfferedLanguage> = when (state) {
    is LanguageCatalogState.Available -> offeredLanguages(roster, state.languages)
    LanguageCatalogState.Unavailable -> roster
}
