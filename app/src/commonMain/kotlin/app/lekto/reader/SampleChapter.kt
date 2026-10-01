package app.lekto.reader

import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BlockKind
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun

/**
 * A short sample book shown in the app until book import lands (the reader
 * replaces the skeleton greeting). It is deliberately long enough to paginate
 * into several pages at a phone-sized viewport, and carries a heading plus
 * repeated words so the mastery colouring is visible.
 */
object SampleChapter {

    const val LANGUAGE = "en"

    val chapter: ReaderChapter = ReaderChapter(
        title = "The Lantern Keeper",
        language = LANGUAGE,
        blocks = listOf(
            heading("The Lantern Keeper"),
            paragraph(
                "On the quiet evening when the harbour lights came on, Mira lit the lantern and waited " +
                    "for the boats. Every keeper before her had done the same, and none of them had ever " +
                    "asked why the light mattered. It simply did.",
            ),
            paragraph(
                "The village was small, and the sea around it was larger than any map suggested. " +
                    "Fishers returned with stories, and the keeper wrote each one down in a ledger that " +
                    "smelled of salt. Some stories repeated; those were the true ones.",
            ),
            paragraph(
                "That evening a storm gathered beyond the headland. The light would have to burn all " +
                    "night, and the keeper would have to stay awake beside it. She filled the reservoir, " +
                    "trimmed the wick, and set the glass to catch every flicker.",
            ),
            paragraph(
                "When the first boat appeared, low against the waves, the lantern threw its beam across " +
                    "the water and the boat turned toward it. Mira counted the crew as they came in, one by " +
                    "one, and marked the ledger. The light had done its quiet work again.",
            ),
            paragraph(
                "By morning the storm had passed and the harbour was calm. The keeper banked the flame, " +
                    "closed the glass, and climbed down to meet the day. The lantern rested, and the " +
                    "village woke, and nothing about either of them seemed remarkable.",
            ),
            paragraph(
                "But that was the point. A light that is always there is a light no one notices, and a " +
                    "keeper who is always ready is a keeper no one thanks. Mira did not mind. She had the " +
                    "ledger, the sea, and the quiet evening ahead, and that was enough.",
            ),
        ),
    )

    /**
     * A demo mastery map: a few words at hand-picked levels, everything else
     * known. The colouring rule is the product's; the map stands in for the
     * user's vocabulary until the vault exists.
     */
    val mastery: MasteryLookup = MasteryLookup { word, _ ->
        when (word.lowercase()) {
            "lantern", "harbour", "keeper", "ledger" -> MasteryLevel.UNKNOWN
            "quiet" -> MasteryLevel.FAMILIAR
            "evening", "remarkable" -> MasteryLevel.RECOGNIZED
            "light", "storm" -> MasteryLevel.MASTERED
            else -> MasteryLevel.KNOWN
        }
    }

    private fun heading(text: String): TextBlock =
        TextBlock(kind = BlockKind.HEADING, headingLevel = 1, runs = listOf(TextRun(text = text)))

    private fun paragraph(text: String): TextBlock =
        TextBlock(kind = BlockKind.PARAGRAPH, runs = listOf(TextRun(text = text)))
}
