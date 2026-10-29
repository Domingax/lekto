package app.lekto.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.X

/** The most of the screen a lookup sheet may cover, so the page and the washed word stay visible. */
internal const val LOOKUP_SHEET_MAX_HEIGHT_FRACTION: Float = 0.45f

/** The 1 dp hairline a surface draws around itself (docs/design.md §4: no shadows). */
private val Hairline = 1.dp

/** The drag handle's bar: its width and its height. */
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp

/**
 * The shell both lookup panels share (docs/design.md §7.3; issues #210, #211):
 * a sheet raised from the bottom over the reader, square where it joins the edge,
 * capped at [LOOKUP_SHEET_MAX_HEIGHT_FRACTION] of the screen and marked by a drag
 * handle. A panel, not an outlined card: the surface tone and a hairline stand in
 * for the shadows the identity forbids.
 *
 * [inColumn] is the desktop shape (issue #199): the panel is the reserved context
 * column, so it fills the room it is given, sits beside the page rather than over
 * it, and drops the drag handle — it was not raised from an edge. The surface tone
 * stays; the hairline is the column's own rule.
 *
 * [content] is the panel's own body — a word and its senses, or a phrase and its
 * translation — so the word and phrase panels cannot drift apart. The sheet
 * swallows taps that land on it, so only a tap truly outside it — which its
 * [DismissScrim] handles — dismisses it; a tap on the reader behind it then
 * never turns the page or opens another lookup.
 */
@Composable
internal fun LookupSheet(
    modifier: Modifier = Modifier,
    sheetModifier: Modifier = Modifier,
    inColumn: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (inColumn) {
        ColumnSheet(modifier, sheetModifier, content)
    } else {
        BottomSheet(modifier, sheetModifier, content)
    }
}

/** The desktop shape: the panel fills the context column and has no edge to be raised from. */
@Composable
private fun ColumnSheet(modifier: Modifier, sheetModifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxSize().then(sheetModifier),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().swallowTaps())
            Column(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** The phone shape: a sheet raised from the bottom edge, capped so the page stays visible. */
@Composable
private fun BottomSheet(modifier: Modifier, sheetModifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxSheetHeight =
            if (constraints.hasBoundedHeight) maxHeight * LOOKUP_SHEET_MAX_HEIGHT_FRACTION else Dp.Infinity
        val theme = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .then(sheetModifier),
            color = theme.surface,
            border = BorderStroke(Hairline, theme.outline),
        ) {
            Box(Modifier.fillMaxWidth()) {
                // The sheet's own surface, behind the controls: a tap that lands on
                // no control stops here instead of reaching the scrim over the page,
                // while the controls stay above it and take their tap first.
                Box(Modifier.matchParentSize().swallowTaps())
                Column(Modifier.fillMaxWidth()) {
                    DragHandle()
                    content()
                }
            }
        }
    }
}

/**
 * The sheet's header band: the title at the content edge and whatever controls
 * sit beside it. The frame lives here so a word and a phrase cannot disagree
 * about where the title starts or where the close action lands.
 */
@Composable
internal fun LookupHeader(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = LektoSpacing.InsidePanel,
            end = LektoSpacing.InsideLabelPair,
            top = LektoSpacing.InsideLabelPair,
        ),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/** The close action every lookup sheet carries, as the icon the design draws (§7.3). */
@Composable
internal fun LookupCloseButton(onDismiss: () -> Unit, tag: String) {
    IconButton(onClick = onDismiss, modifier = Modifier.handCursor().testTag(tag)) {
        Icon(imageVector = LektoIcons.X, contentDescription = LocalAppTexts.current[TextKey.CLOSE])
    }
}

/** A hairline between two blocks; inset so it parts the content, not the sheet's whole width. */
@Composable
internal fun SheetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = LektoSpacing.InsidePanel),
        color = MaterialTheme.colorScheme.outline,
    )
}

/** The grabber that says the sheet was raised from the bottom edge. It is a hint, not a control. */
@Composable
private fun DragHandle() {
    Box(Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRelatedLines), contentAlignment = Alignment.Center) {
        Box(Modifier.width(HandleWidth).height(HandleHeight).background(MaterialTheme.colorScheme.outline, CircleShape))
    }
}
