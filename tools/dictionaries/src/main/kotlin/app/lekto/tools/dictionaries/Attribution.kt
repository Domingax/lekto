package app.lekto.tools.dictionaries

/**
 * The licence and attribution data that must travel with the pack (ticket #17).
 *
 * Wiktionary content is CC BY-SA 4.0 (and GFDL at the reuser's choice); a derived
 * database is a ShareAlike distribution, so it must name the source, link the
 * licence, carry the licence text, state that the data was modified and that it
 * stays ShareAlike, and disclaim endorsement. The same rows are stored in the
 * pack's metadata table so the app's attribution screen reads one source (#18).
 */
internal object Attribution {

    const val GENERATOR: String = "Lekto tools/dictionaries"
    const val LICENSE_NAME: String = "CC BY-SA 4.0"
    const val LICENSE_URL: String = "https://creativecommons.org/licenses/by-sa/4.0/"
    const val LICENSE_FULL_TEXT_URL: String = "https://creativecommons.org/licenses/by-sa/4.0/legalcode"
    const val SOURCE_NAME: String = "Wiktionary contributors"
    const val SOURCE_SITE: String = "https://en.wiktionary.org/"
    const val SOURCE_DATA: String =
        "Wiktextract / Kaikki machine-readable extract (https://kaikki.org/dictionary/rawdata.html)"

    private const val LICENSE_RESOURCE: String = "/CC-BY-SA-4.0.txt"

    private const val MODIFICATIONS: String =
        "Extracted from the raw Wiktextract extract, filtered to English entries that carry a French " +
            "translation, trimmed to single-token surface forms, normalised (NFC, lower case) and " +
            "re-formatted as a read-only SQLite database."

    /** The full CC BY-SA 4.0 legal code, bundled so the build needs no network and stays reproducible. */
    val licenseText: String by lazy {
        requireNotNull(Attribution::class.java.getResourceAsStream(LICENSE_RESOURCE)) {
            "the bundled CC BY-SA 4.0 text must be on the classpath"
        }.bufferedReader().use { it.readText() }
    }

    /** The pack's metadata rows, in a fixed order so the build stays reproducible. */
    fun metadata(provenance: PackProvenance): List<Pair<String, String>> = listOf(
        "format_version" to PackFormat.VERSION.toString(),
        "generator" to GENERATOR,
        "license" to LICENSE_NAME,
        "license_url" to LICENSE_URL,
        "license_full_text_url" to LICENSE_FULL_TEXT_URL,
        "license_text" to licenseText,
        "attribution" to SOURCE_NAME,
        "source" to SOURCE_SITE,
        "source_data" to SOURCE_DATA,
        "modifications" to MODIFICATIONS,
        "share_alike" to "true",
        "source_input_url" to provenance.sourceUrl,
        "source_sha256" to provenance.sourceSha256,
        "source_extraction_date" to provenance.extractionDate,
        "notice" to summary(provenance),
    )

    /** The human-readable NOTICE published beside the pack: the summary plus the full licence text. */
    fun notice(provenance: PackProvenance): String = summary(provenance) + "\n\n" + licenseText

    private fun summary(provenance: PackProvenance): String =
        """
        |Lekto offline dictionary pack — English → French
        |
        |This pack is a derived work of Wiktionary content and is made available under the
        |Creative Commons Attribution-ShareAlike 4.0 International licence (CC BY-SA 4.0).
        |
        |Attribution: $SOURCE_NAME.
        |Source: $SOURCE_SITE
        |Data: $SOURCE_DATA
        |Licence: $LICENSE_NAME — $LICENSE_FULL_TEXT_URL
        |
        |Modifications: $MODIFICATIONS
        |
        |ShareAlike: this derived database remains licensed under CC BY-SA 4.0; you may reuse
        |it under the same terms.
        |
        |Lekto is not affiliated with, nor endorsed by, the Wikimedia Foundation or Wiktionary.
        |
        |Provenance:
        |  input URL: ${provenance.sourceUrl}
        |  input SHA-256: ${provenance.sourceSha256}
        |  upstream extraction date: ${provenance.extractionDate}
        |
        |The full licence text follows.
        """.trimMargin()
}
