package app.lekto.reader

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.text.WordToken
import app.lekto.core.text.tokenise
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The geometry of the dotted rule under a highlighted word (issue #281): the
 * rule spans its own word and no neighbour, hangs under the glyphs at one height
 * per line, and a known word gets none. It is measured against a real
 * [TextLayoutResult] — the page's own source of geometry — because there is no
 * layout outside a composition to measure against.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // The line's width and the sub-pixel tolerance are the experiment's parameters.
class ReaderRulesTest {

    private val styles = ReaderStyles.of(ReadingPreferences.DEFAULT, mastery = MasteryPalette.Light)

    private val line = "the lantern glows beside the keeper"

    @Test
    fun aRuleSpansItsOwnWordAndNotItsNeighbours() = runComposeUiTest {
        lateinit var layout: TextLayoutResult
        lateinit var words: List<WordToken>
        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            layout = measurer.measure(
                text = AnnotatedString(line),
                style = styles.body,
                constraints = Constraints(maxWidth = with(density) { 280.dp.roundToPx() }),
            )
            words = tokenise(line, "en", WhitespaceTextSegmenter()).words
        }
        waitForIdle()

        val lantern = words.first { word -> word.surface == "lantern" }
        val rule = ruleUnder(layout, lantern).single()

        assertTrue(rule.left >= layout.getBoundingBox(lantern.start).left, "the rule starts at the word: $rule")
        assertTrue(rule.right >= layout.getBoundingBox(lantern.end - 1).right, "the rule reaches its last glyph: $rule")
        assertTrue(rule.left >= layout.getBoundingBox(words[0].end - 1).right, "the rule must not reach 'the': $rule")
        assertTrue(rule.right <= layout.getBoundingBox(words[2].start).left, "the rule must not reach 'glows': $rule")
        assertTrue(
            rule.y >= layout.getBoundingBox(lantern.start).center.y,
            "the rule hangs under the word, not through it: $rule",
        )
        assertTrue(
            rule.y <= layout.getLineBottom(layout.getLineForOffset(lantern.start)),
            "the rule stays inside the word's line: $rule",
        )
    }

    @Test
    fun everyWordOnALineHangsItsRuleAtOneHeight() = runComposeUiTest {
        lateinit var layout: TextLayoutResult
        lateinit var words: List<WordToken>
        setContent {
            val measurer = rememberTextMeasurer()
            layout = measurer.measure(AnnotatedString(line), style = styles.body)
            words = tokenise(line, "en", WhitespaceTextSegmenter()).words
        }
        waitForIdle()

        // "glows" has a descender and "keeper" does not: a per-glyph bottom would
        // make the line's rules zigzag.
        val heights = words.map { word -> ruleUnder(layout, word).single().y }.toSet()

        assertEquals(1, heights.size, "one rule height per line: $heights")
    }

    @Test
    fun aKnownWordGetsNoRuleAndEachLevelKeepsItsColour() = runComposeUiTest {
        lateinit var layout: TextLayoutResult
        lateinit var words: List<WordToken>
        setContent {
            val measurer = rememberTextMeasurer()
            layout = measurer.measure(AnnotatedString(line), style = styles.body)
            words = tokenise(line, "en", WhitespaceTextSegmenter()).words
        }
        waitForIdle()

        val levels = mapOf("lantern" to MasteryLevel.UNKNOWN, "glows" to MasteryLevel.FAMILIAR)
        val rules = pageRules(layout, words, levelOf = { word -> levels[word.surface] ?: MasteryLevel.KNOWN }, styles.mastery)

        assertEquals(
            setOf(MasteryPalette.Light.unknown, MasteryPalette.Light.familiar),
            rules.groups.map { group -> group.colour }.toSet(),
            "one path per level colour, and a known word in none",
        )
        assertEquals(2, rules.groups.size)

        val allKnown = pageRules(layout, words, levelOf = { MasteryLevel.KNOWN }, styles.mastery)
        assertTrue(allKnown.groups.isEmpty(), "a known page carries no rule and no path")
    }
}
