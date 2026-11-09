package app.lekto.tools.dictionaries

/**
 * The deterministic assembly: one **Translation language**'s content file and the
 * **Reading language**'s shared form index in, the in-memory pack out.
 *
 * It no longer streams the edition or accumulates forms. The index is an input
 * (ADR-0045), so every pack of a language carries the same `form` table and
 * `stats.formCount`; the ordering is already fixed by the intermediate, and no
 * wall clock or timestamp enters.
 */
internal class PackBuilder(private val content: EditionContent, private val index: FormIndex) {

    fun build(): PackContent {
        require(index.readingLanguage == content.readingLanguage) {
            "the form index is for ${index.readingLanguage}, not ${content.readingLanguage}"
        }
        return PackContent(
            provenance = content.provenance,
            stats = stats(),
            entries = content.entries,
            forms = index.forms,
        )
    }

    private fun stats(): PackStats = PackStats(
        totalLines = content.stats.totalLines,
        malformedLines = content.stats.malformedLines,
        readingEntries = content.stats.readingEntries,
        entriesWithTranslation = content.stats.entriesWithTranslation,
        lemmaCount = content.entries.map(PackEntry::lemma).distinct().size,
        entryCount = content.entries.size,
        formCount = index.forms.size,
        senseCount = content.entries.sumOf { it.senses.size },
        translationCount = content.entries.sumOf { entry -> entry.senses.sumOf { it.translations.size } },
    )
}

/** Whether [line] could be an entry of [language], cheaply skipping the rest before a full parse. */
internal fun couldBe(line: String, language: String): Boolean =
    line.contains(langCodeCompact(language)) || line.contains(langCodeSpaced(language))

/** The JSON line fragment a compact-serialised entry's language carries. */
internal fun langCodeCompact(language: String): String = "\"lang_code\":\"$language\""

/** The JSON line fragment a pretty-serialised entry's language carries. */
internal fun langCodeSpaced(language: String): String = "\"lang_code\": \"$language\""

/** A sense with no reading-language definition: a translation-only entry still needs one. */
internal const val SENSE_WITHOUT_DEFINITION: String = ""
