package app.lekto.core.text

/**
 * A paragraph the parser reads as a chapter heading, with the block it becomes
 * and the title the reader shows for it.
 */
internal data class TxtHeading(val blockIndex: Int, val title: String)

/**
 * The structure a plain text carries but does not declare (ADR-0041): the chapter
 * headings a `TxtParser` turns into a **Chapter list**.
 *
 * A heading candidate is a short paragraph that opens with a numbering a reader
 * would recognise — `Chapter 4`, `Chapitre IV`, `Partie 2`, or a bare numeral —
 * because a false chapter costs more than a missing one: a wrong boundary splits
 * a page mid-scene, while a book with no list behaves as it always did.
 *
 * Candidates are only accepted as a list when their numbering is consistent: it
 * must increase, and either every heading is a bare numeral counting from one by
 * one, or none is. That is what keeps the page numbers of a scraped file from
 * passing for chapters.
 */
internal object TxtChapters {

    /** The headings [paragraphs] carry, or none when they do not form a list. */
    fun headings(paragraphs: List<String>): List<TxtHeading> {
        val numbered = paragraphs.mapIndexedNotNull { index, paragraph ->
            numberingOf(paragraph)?.let { numbering -> NumberedHeading(TxtHeading(index, paragraph), numbering) }
        }
        if (numbered.size < MIN_CHAPTERS) return emptyList()
        return if (numbered.consistent()) numbered.map { heading -> heading.heading } else emptyList()
    }

    /**
     * Whether [text] opens like a chapter heading, numbered or not: `CHAPITRE
     * PREMIER` is a heading even though no numeral follows it, so it is never
     * taken for a book's title.
     */
    fun looksLikeHeading(text: String): Boolean = HEADING_KEYWORD.containsMatchIn(text) || numberingOf(text) != null

    /** One chapter per heading, or the single chapter a book with no structure has. */
    fun chaptersOf(headings: List<TxtHeading>): List<Chapter> = headings
        .map { heading -> Chapter(heading.title, heading.blockIndex) }
        .ifEmpty { listOf(Chapter(title = "", startBlock = 0)) }

    /**
     * The ordinal a heading opens with, or `null` when the paragraph is not a
     * heading: too long to be one, or opening with no numbering at all.
     */
    private fun numberingOf(text: String): Numbering? =
        if (text.length > HEADING_MAX_LENGTH) null else bareNumbering(text) ?: keywordNumbering(text)

    /** A paragraph that is only a numeral, with an optional full stop. */
    private fun bareNumbering(text: String): Numbering? = BARE_NUMERAL.matchEntire(text)
        ?.groupValues?.get(1)
        ?.let(::ordinalOf)
        ?.let { ordinal -> Numbering(ordinal, bare = true) }

    /** A paragraph that opens with `Chapter`, `Chapitre`, `Partie` and its numeral. */
    private fun keywordNumbering(text: String): Numbering? = KEYWORD_NUMBER.find(text)
        ?.groupValues?.get(1)
        ?.let(::ordinalOf)
        ?.let { ordinal -> Numbering(ordinal, bare = false) }

    /** The value of an Arabic or a Roman numeral, or `null` when it is neither. */
    private fun ordinalOf(token: String): Int? = token.toIntOrNull() ?: romanOf(token)

    private fun romanOf(token: String): Int? {
        var total = 0
        var previous = 0
        for (character in token.uppercase().reversed()) {
            val value = ROMAN_VALUES[character] ?: return null
            if (value < previous) {
                total -= value
            } else {
                total += value
                previous = value
            }
        }
        return total.takeIf { value -> value > 0 }
    }

    /** Whether [this] run of candidates numbers a chapter list rather than a stray line. */
    private fun List<NumberedHeading>.consistent(): Boolean {
        val ordinals = map { heading -> heading.numbering.ordinal }
        val bare = map { heading -> heading.numbering.bare }
        return ordinals.zipWithNext().all { (first, second) -> second > first } &&
            when {
                bare.all { isBare -> isBare } -> ordinals == (1..size).toList()
                bare.any { isBare -> isBare } -> false
                else -> true
            }
    }

    /** A candidate heading and the numbering it opens with. */
    private data class NumberedHeading(val heading: TxtHeading, val numbering: Numbering)

    /** A heading's numbering: its value, and whether it is a bare numeral. */
    private data class Numbering(val ordinal: Int, val bare: Boolean)

    /** Shorter than this, a paragraph is a heading rather than prose. */
    private const val HEADING_MAX_LENGTH = 120

    /** At least two headings make a list; a single one is the paragraph it looks like. */
    private const val MIN_CHAPTERS = 2

    /** The openings a chapter heading uses, numbered or not. */
    private val HEADING_KEYWORD = Regex("""^(?:chapter|chapitre|part|partie|livre)\b""", RegexOption.IGNORE_CASE)

    /** `Chapter 4`, `Chapitre IV`, `Partie 2`: a keyword and the numeral it counts. */
    private val KEYWORD_NUMBER = Regex(
        """^(?:chapter|chapitre|part|partie|livre)\s+([0-9]{1,4}|[IVXLCDM]{1,12})(?![0-9A-Za-z])""",
        RegexOption.IGNORE_CASE,
    )

    /** A paragraph that is only the number, with an optional full stop. */
    private val BARE_NUMERAL = Regex("""^([0-9]{1,4}|[IVXLCDM]{1,12})\.?$""", RegexOption.IGNORE_CASE)

    private val ROMAN_VALUES = mapOf(
        'I' to 1,
        'V' to 5,
        'X' to 10,
        'L' to 50,
        'C' to 100,
        'D' to 500,
        'M' to 1000,
    )
}
