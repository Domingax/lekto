package app.lekto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.contentColorOn
import app.lekto.theme.hoverWash
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Trash
import kotlinx.coroutines.launch

/** The delete action's icon size: 18 dp, the size the identity uses inside a control (§5). */
internal val DELETE_ICON_SIZE = 18.dp

/**
 * A row's delete: the tag a UI test clicks, and the action that opens the
 * confirmation. One value, so a row's frame is four parameters rather than five.
 */
internal data class DeleteAction(val tag: String, val onDelete: () -> Unit)

/**
 * A list row whose delete is a gesture rather than a red word sitting on every
 * row (`docs/design.md` §7.1, §7.4, §8; issues #208, #212) — the Library's shelf
 * and the Vocabulary list delete the same way.
 *
 * On a touch screen the row is swiped from its trailing edge, and the swipe
 * uncovers the red plate with the trash and the word. With a pointer the row
 * takes the theme ink's 5 % wash and a trash appears in a slot already reserved
 * for it, so revealing it never shifts the row. Neither path deletes anything:
 * both call [DeleteAction.onDelete], which opens the confirmation, because there
 * is no undo.
 *
 * [row] draws the row's own anatomy over the paper this frame paints — that
 * paint is also what hides the swipe's plate until the reader uncovers it — and
 * receives the modifier that claims the width left beside the delete. The row's
 * own screen still owns its accessibility action, so a screen reader reaches the
 * delete without the gesture.
 */
@Composable
internal fun DeletingRow(
    delete: DeleteAction,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    row: @Composable (Modifier) -> Unit,
) {
    // A drag is the touch idiom and only that: with a pointer, the delete waits
    // for the row's hover instead.
    if (!touchClient) {
        DeleteRowFrame(delete, modifier, verticalAlignment, row)
        return
    }
    val dismiss = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier.fillMaxWidth(),
        // A swipe only ever asks: deleting is irreversible, so the gesture opens
        // the confirmation and the row goes back where it was.
        enableDismissFromStartToEnd = false,
        onDismiss = {
            delete.onDelete()
            scope.launch { dismiss.reset() }
        },
        backgroundContent = { SwipeDeleteBackground() },
    ) {
        DeleteRowFrame(delete, Modifier, verticalAlignment, row)
    }
}

/** What a swipe uncovers: the theme's red, the trash and the word it stands for. */
@Composable
private fun SwipeDeleteBackground() {
    val theme = MaterialTheme.colorScheme
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.error, LektoRadii.Control)
            .padding(horizontal = LektoSpacing.InsidePanel),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LektoIcons.Trash,
            contentDescription = null,
            tint = contentColorOn(theme.error),
            modifier = Modifier.size(DELETE_ICON_SIZE),
        )
        Spacer(Modifier.width(LektoSpacing.BetweenRelatedLines))
        Text(texts[TextKey.DELETE], color = contentColorOn(theme.error), style = MaterialTheme.typography.bodyLarge)
    }
}

/** The row itself: its content and the trash the mouse uncovers. */
@Composable
private fun DeleteRowFrame(
    delete: DeleteAction,
    modifier: Modifier,
    verticalAlignment: Alignment.Vertical,
    row: @Composable (Modifier) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = MaterialTheme.colorScheme
    // The frame paints the paper over the swipe's red, so the action stays hidden
    // until the reader uncovers it. The hover wash is drawn over the paper, not
    // under it, and it is square: a row is a band across the list, not a card
    // (§8: the wash marks the row, it does not round it into a surface).
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.background)
            .background(if (hovered) hoverWash(theme.onBackground) else Color.Transparent)
            .padding(vertical = LektoSpacing.BetweenRows)
            .hoverable(interaction),
        verticalAlignment = verticalAlignment,
    ) {
        row(Modifier.weight(1f))
        // The action's place is reserved, so revealing it never shifts the row.
        Box(Modifier.size(LektoSpacing.TouchTarget)) {
            if (hovered) DeleteIcon(delete, alignedToFirstLine = verticalAlignment == Alignment.Top)
        }
    }
}

/**
 * The trash a mouse uncovers at the row's end: the desktop idiom for a row action.
 *
 * It is the muted ink, not red (`docs/design.md` §7.4): red is for the moment the
 * action is taken, and this icon only opens the question. [alignedToFirstLine]
 * centres it on the row's first line rather than on the whole row, because a word
 * at the top with its translation under it leaves the middle of the row sitting
 * between the two — where a trash reads as somebody else's control.
 */
@Composable
private fun DeleteIcon(delete: DeleteAction, alignedToFirstLine: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .handCursor()
            .testTag(delete.tag)
            .plainClickable(role = Role.Button, onClick = delete.onDelete),
        contentAlignment = if (alignedToFirstLine) Alignment.TopCenter else Alignment.Center,
    ) {
        Icon(
            imageVector = LektoIcons.Trash,
            contentDescription = LocalAppTexts.current[TextKey.DELETE],
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = if (alignedToFirstLine) FIRST_LINE_INSET else 0.dp)
                .size(DELETE_ICON_SIZE),
        )
    }
}

/**
 * The nudge that centres the trash on a row's first line: that line is the
 * vocabulary word's 24 sp, and the icon is 18 dp, so half the difference of the
 * two puts the icon's centre where the word's is.
 */
internal val FIRST_LINE_INSET = 3.dp
