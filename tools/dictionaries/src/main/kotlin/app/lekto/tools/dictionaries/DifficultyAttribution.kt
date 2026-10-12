package app.lekto.tools.dictionaries

/**
 * The licence and attribution the **Difficulty table** must carry (ADR-0029;
 * docs/research/word-difficulty-data-licences.md).
 *
 * wordfreq's data files are CC BY-SA 4.0 — the same licence as the dictionary
 * pack — so a derived band is Adapted Material whose ShareAlike is already
 * satisfied. The obligations are **attribution only**, and wordfreq's `NOTICE.md`
 * requires the credits to travel **inside** the data, not merely in the app: the
 * exact name **Robyn Speer**, a link to the licence, an indication of the
 * modification (frequencies → bands; form frequencies aggregated to lemmas), and
 * the three upstream credits — **Google Books Ngram Viewer + link**,
 * **OpenSubtitles**, and the **SUBTLEX** authors with the statement that SUBTLEX
 * is freely available.
 */
internal object DifficultyAttribution {

    const val GENERATOR: String = "Lekto tools/dictionaries"

    const val LICENSE_NAME: String = "CC BY-SA 4.0"

    const val LICENSE_URL: String = "https://creativecommons.org/licenses/by-sa/4.0/"

    const val SOURCE_NAME: String = "wordfreq by Robyn Speer"

    const val SOURCE_SITE: String = "https://github.com/rspeer/wordfreq"

    const val SOURCE_DATA: String =
        "wordfreq v3 (https://github.com/rspeer/wordfreq), built from Google Books Ngram Viewer " +
            "(https://books.google.com/ngrams), OPUS OpenSubtitles " +
            "(https://www.opensubtitles.org/), and the SUBTLEX word lists by Brysbaert et al. " +
            "(SUBTLEX is freely available data)."

    private const val MODIFICATIONS: String =
        "wordfreq surface-form frequencies aggregated onto Wiktionary lemmas (or normalised surface " +
            "forms when no lemma is known) and re-expressed as CEFR bands A1–C2 at the calibrated " +
            "cut points."

    private const val ATTRIBUTION: String =
        "Difficulty bands derived from wordfreq by Robyn Speer, data licensed CC BY-SA 4.0. " +
            "wordfreq's code is Apache-2.0."

    /** The human-readable NOTICE published beside the table and carried in its metadata. */
    fun notice(provenance: PackProvenance): String =
        """
        |Lekto word-difficulty table — English (CEFR A1–C2)
        |
        |This table is a derived work of wordfreq and is made available under the Creative
        |Commons Attribution-ShareAlike 4.0 International licence (CC BY-SA 4.0).
        |
        |Attribution: $SOURCE_NAME
        |Source: $SOURCE_SITE
        |Data: $SOURCE_DATA
        |Licence: $LICENSE_NAME — $LICENSE_URL
        |
        |Modifications: $MODIFICATIONS
        |Calibration: ${Calibration.SOURCE}
        |             ${Calibration.VALIDATION}
        |
        |ShareAlike: this derived database remains licensed under CC BY-SA 4.0; you may reuse
        |it under the same terms.
        |
        |Provenance:
        |  input URL: ${provenance.sourceUrl}
        |  input SHA-256: ${provenance.sourceSha256}
        |  upstream extraction date: ${provenance.extractionDate}
        """.trimMargin()

    /** The table's metadata rows, in a fixed order so the build stays reproducible. */
    fun metadata(provenance: PackProvenance): List<Pair<String, String>> = listOf(
        "format_version" to DifficultyFormat.VERSION.toString(),
        "generator" to GENERATOR,
        "license" to LICENSE_NAME,
        "license_url" to LICENSE_URL,
        "attribution" to ATTRIBUTION,
        "source" to SOURCE_SITE,
        "source_data" to SOURCE_DATA,
        "modifications" to MODIFICATIONS,
        "share_alike" to "true",
        "source_input_url" to provenance.sourceUrl,
        "source_sha256" to provenance.sourceSha256,
        "notice" to notice(provenance),
    )
}
