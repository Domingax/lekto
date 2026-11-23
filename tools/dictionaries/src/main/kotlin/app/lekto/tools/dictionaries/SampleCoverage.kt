package app.lekto.tools.dictionaries

/**
 * The coverage bars every shipped **Sample** must meet (issue #324).
 *
 * The guide demonstrates one paragraph per band and recolours its page on every
 * choice, so the text has to carry the colours the screen promises: at A1 one
 * word for each highlighted **Mastery level**, and from A1 to B2 at least three
 * coloured words spanning two levels. C1 and C2 are exempt — a reader at that
 * level knows the text, and the thin colour says so.
 */
internal object SampleCoverage {

    /** The band the guide opens at; its demonstrated paragraph must show every highlighted level. */
    const val NOTES_BAND: String = "A1"

    /** The bands checked: A1 uses the stronger notes bar, the others the spread bar. */
    val CHECKED_BANDS: List<String> = listOf("A1", "A2", "B1", "B2")

    /** The fewest coloured words the demonstrated paragraph keeps from A1 to B2. */
    const val MIN_COLOURED_WORDS: Int = 3

    /** The fewest distinct highlighted levels those coloured words span. */
    const val MIN_HIGHLIGHTED_LEVELS: Int = 2
}

/**
 * Refuses [sample] when the paragraph the guide would demonstrate cannot show
 * the colours it promises (issue #324).
 *
 * The paragraph is chosen the way `app`'s `guideParagraph` chooses it — the one
 * whose colours cover the most distinct highlighted levels, ties keeping the
 * earlier — and each band [SampleCoverage.CHECKED_BANDS] names is checked. A
 * sample that fails here never reaches the catalogue, so a future text cannot
 * silently lose a note or collapse the level screen's recolour to a lone word.
 */
internal fun requireSampleCoverage(sample: SampleContent) {
    val keysByParagraph = sample.paragraphs.map { paragraph ->
        sampleWords(paragraph, sample.language)
            .map { surface -> sample.forms[normaliseSurface(surface)] ?: normaliseSurface(surface) }
    }
    SampleCoverage.CHECKED_BANDS.forEach { readerBand ->
        val colours = requireNotNull(sample.colourMaps[readerBand]) {
            "the ${sample.language} sample has no $readerBand colour map"
        }
        val levels = keysByParagraph
            .map { keys -> highlightedLevelsOf(keys, colours) }
            .maxByOrNull { paragraphLevels -> paragraphLevels.distinct().size }
            .orEmpty()
        val distinct = levels.distinct()
        if (readerBand == SampleCoverage.NOTES_BAND) {
            val missing = HIGHLIGHTED_LEVELS.filterNot(distinct::contains)
            require(missing.isEmpty()) {
                "the ${sample.language} sample's $readerBand paragraph misses " +
                    "${missing.joinToString()} level word(s); the guide's four notes need one of each"
            }
        } else {
            require(
                levels.size >= SampleCoverage.MIN_COLOURED_WORDS &&
                    distinct.size >= SampleCoverage.MIN_HIGHLIGHTED_LEVELS,
            ) {
                "the ${sample.language} sample's $readerBand paragraph carries ${levels.size} coloured " +
                    "word(s) over ${distinct.size} level(s); the guide needs at least " +
                    "${SampleCoverage.MIN_COLOURED_WORDS} over ${SampleCoverage.MIN_HIGHLIGHTED_LEVELS}"
            }
        }
    }
}

/** The highlighted mastery level of every key in [keys], in reading order. */
private fun highlightedLevelsOf(keys: List<String>, colours: ColourMap): List<String> = keys
    .map { key -> colours[key] ?: MASTERY_KNOWN }
    .filterNot { level -> level == MASTERY_KNOWN }

/** The levels a note can name, in the order a reader acquires them. */
private val HIGHLIGHTED_LEVELS: List<String> = MASTERY_LEVELS.filterNot { level -> level == MASTERY_KNOWN }
