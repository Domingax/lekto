package app.lekto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Search

/** The `Search` icon's size in the field: 18 dp, the size the identity uses inside a control (§5). */
internal val SEARCH_ICON_SIZE = 18.dp

/**
 * The `Search` field the Library and the Vocabulary list share (`docs/design.md`
 * §7.1, §7.4; issues #173, #212): a surface with a hairline and the `Search`
 * icon, not a Material field, so both lists search the same way and the two
 * cannot drift.
 *
 * The placeholder is the muted ink, and the field's accessible name is `Search`
 * too — a placeholder is a sibling node, so a reader must hear what the field is
 * for, not only what it currently says. The caller owns the query and the
 * write-back; [modifier] carries the screen's own tag and its spacing.
 */
@Composable
fun SearchField(query: String, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    val texts = LocalAppTexts.current
    BasicTextField(
        value = query,
        onValueChange = onSearch,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.semantics { contentDescription = texts[TextKey.SEARCH] },
        decorationBox = { field ->
            SearchSurface(query = query, placeholder = texts[TextKey.SEARCH]) { field() }
        },
    )
}

/** The field's frame: the surface, the hairline and the icon, with the placeholder while it is empty. */
@Composable
private fun SearchSurface(query: String, placeholder: String, field: @Composable () -> Unit) {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LektoSpacing.TouchTarget)
            .background(theme.surface, LektoRadii.Control)
            .border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Control)
            .padding(start = LektoSpacing.InsidePanel, end = LektoSpacing.InsideLabelPair),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LektoIcons.Search,
            contentDescription = null,
            tint = theme.onSurfaceVariant,
            modifier = Modifier.size(SEARCH_ICON_SIZE),
        )
        Spacer(Modifier.width(LektoSpacing.BetweenRows))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = theme.onSurfaceVariant)
            }
            field()
        }
    }
}
