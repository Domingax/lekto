package app.lekto

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lekto.theme.Dark
import app.lekto.theme.LektoShapes
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import app.lekto.theme.LektoTypography
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The design tokens' contract (issue #181): the three papers are the values
 * `docs/design.md` §2.1 records, the app has no accent colour, and the type
 * scale and radii are the ones §3 and §4 name.
 *
 * The goldens pin the colours as they are painted; these assertions pin the
 * meaning a golden update could otherwise relax silently.
 */
class LektoThemeTest {

    private companion object {
        /** The 4 dp grid the spacing scale sits on. */
        const val SPACING_GRID = 4f
    }

    private data class Paper(
        val name: String,
        val paper: Color,
        val surface: Color,
        val ink: Color,
        val muted: Color,
        val separator: Color,
        val error: Color,
    )

    private val papers = listOf(
        Light to Paper(
            name = "Light",
            paper = Color(0xFFFAF7F2),
            surface = Color(0xFFF3EEE6),
            ink = Color(0xFF1C1A17),
            muted = Color(0xFF6B6357),
            separator = Color(0xFFD6CCBC),
            error = Color(0xFFB3261E),
        ),
        Dark to Paper(
            name = "Dark",
            paper = Color(0xFF15130F),
            surface = Color(0xFF1F1C17),
            ink = Color(0xFFE8E2D8),
            muted = Color(0xFF9A9083),
            separator = Color(0xFF3A342B),
            error = Color(0xFFF2B8B5),
        ),
        Sepia to Paper(
            name = "Sepia",
            paper = Color(0xFFF6ECD8),
            surface = Color(0xFFEFE3C9),
            ink = Color(0xFF3B2F1E),
            muted = Color(0xFF6B5B41),
            separator = Color(0xFFD8C7A6),
            error = Color(0xFFB3261E),
        ),
    )

    @Test
    fun theThreePapersMatchTheDesignDocument() {
        papers.forEach { (scheme, paper) ->
            assertEquals(paper.paper, scheme.background, "${paper.name}'s paper")
            assertEquals(paper.surface, scheme.surface, "${paper.name}'s surface")
            assertEquals(paper.ink, scheme.onBackground, "${paper.name}'s ink")
            assertEquals(paper.muted, scheme.onSurfaceVariant, "${paper.name}'s muted ink")
            assertEquals(paper.separator, scheme.outline, "${paper.name}'s hairline")
            assertEquals(paper.error, scheme.error, "${paper.name}'s red")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theThemeHandsItsTokensToMaterialTheme() {
        var chosen: ColorScheme? = null
        var titleSize = 0f
        var controlRadius: Shape? = null

        runComposeUiTest {
            setContent {
                LektoTheme(theme = Dark) {
                    chosen = MaterialTheme.colorScheme
                    titleSize = MaterialTheme.typography.headlineMedium.fontSize.value
                    controlRadius = MaterialTheme.shapes.small
                }
            }
        }

        assertEquals(Dark, chosen)
        assertEquals(LektoTypography.headlineMedium.fontSize.value, titleSize)
        assertEquals(RoundedCornerShape(8.dp), controlRadius)
    }

    @Test
    fun aFilledControlIsInkOnPaperInEveryTheme() {
        listOf(Light, Dark, Sepia).forEach { scheme ->
            assertEquals(scheme.onBackground, scheme.primary, "the ink is the only accent")
            assertEquals(scheme.background, scheme.onPrimary, "a filled control is ink on paper")
        }
    }

    @Test
    fun theScreenTitleIsBorrowedFromTheReadingSerif() {
        assertEquals(FontFamily.Serif, LektoTypography.headlineMedium.fontFamily)
        assertEquals(FontWeight.Bold, LektoTypography.headlineMedium.fontWeight)
        assertEquals(26.sp, LektoTypography.headlineMedium.fontSize)
        assertEquals(FontFamily.Serif, LektoTypography.titleMedium.fontFamily)
        assertEquals(18.sp, LektoTypography.titleMedium.fontSize)
        assertEquals(FontFamily.SansSerif, LektoTypography.titleSmall.fontFamily)
        assertEquals(16.sp, LektoTypography.titleSmall.fontSize)
        assertEquals(FontFamily.SansSerif, LektoTypography.bodyMedium.fontFamily)
        assertEquals(14.sp, LektoTypography.bodyMedium.fontSize)
    }

    @Test
    fun controlsSurfacesAndSheetsTakeTheThreeRadii() {
        assertEquals(RoundedCornerShape(8.dp), LektoShapes.small)
        assertEquals(RoundedCornerShape(12.dp), LektoShapes.medium)
        assertEquals(RoundedCornerShape(16.dp), LektoShapes.large)
    }

    @Test
    fun spacingIsFourBasedAndTheTouchTargetIsFortyEight() {
        assertEquals(4.dp, LektoSpacing.InsideLabelPair)
        assertEquals(48.dp, LektoSpacing.TouchTarget)

        listOf(
            LektoSpacing.InsideLabelPair,
            LektoSpacing.BetweenRelatedLines,
            LektoSpacing.BetweenRows,
            LektoSpacing.InsidePanel,
            LektoSpacing.ScreenEdge,
            LektoSpacing.BetweenBlocks,
            LektoSpacing.BetweenSections,
        ).forEach { step ->
            assertEquals(0f, step.value % SPACING_GRID, "$step must sit on the 4 dp grid")
        }
    }
}
