package app.lekto.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookProgress
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.ReadingSnippet
import app.lekto.theme.ResumeLine
import app.lekto.ui.DELETE_ICON_SIZE
import app.lekto.ui.DeleteAction
import app.lekto.ui.DeletingRow
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.handCursor
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Trash
import app.lekto.ui.plainClickable
import app.lekto.ui.touchClient

/** The test tag on the resume block, so a UI test can read it apart from the shelf. */
internal fun libraryResumeBlockTag(bookId: String): String = "library-resume-$bookId"

/** The test tag on the block's delete control, which opens the same confirmation as a row. */
internal fun libraryResumeDeleteTag(bookId: String): String = "library-resume-delete-$bookId"

/** The block's cover: 64 dp wide, and at most 92 dp tall so a portrait cover keeps its ratio (§7.1). */
internal val RESUME_COVER_WIDTH = 64.dp
internal val RESUME_COVER_HEIGHT = 92.dp

/** How many lines of the saved paragraph the block shows (docs/design.md §7.1; issue #288). */
private const val SNIPPET_LINES = 2

/** How many lines the block's title takes before it truncates, so the card's height stays bounded. */
private const val TITLE_LINES = 2

/** The gap between the block's title and the **Author** under it. */
private val TITLE_TO_AUTHOR = 2.dp

/**
 * What the block states about its book: the **Reading progress**, the
 * **Chapter** the **Reading position** sits in (or `null` when no chapter is
 * known), and the **Resume snippet** once it is read. Independent facts, bundled
 * so the block's signatures stay short.
 */
internal data class ResumeFacts(val progress: BookProgress, val chapter: String?, val snippet: String? = null)

/**
 * The **Resume block**: the most recently read unfinished book leaves the shelf
 * and leads above it (issue #288; docs/design.md §7.1). One card holds its
 * cover, title, **Author**, current **Chapter** and percentage, the row's own
 * 3 dp rule, a filled `Resume`, a hairline and the first **Paragraph** at the
 * saved position.
 *
 * The facts and the action are one accessible item: the children merge into a
 * single node, a tap anywhere on the card resumes, and `Resume` is the action a
 * screen reader invokes. The snippet is the page below it, its own text. Delete
 * is the row's trailing-edge swipe on a touch screen, and a trash in the card's
 * corner on hover with a mouse, because a pointer has no swipe. Delete never
 * sits on the shelf as a red word.
 *
 * Each missing piece drops on its own: no snippet ends the block at its button,
 * no chapter leaves the percentage, and no cover keeps the **Reading language**'s
 * plaque. The block itself only exists for a book with a **Reading position**, so
 * a shelf with none is exactly what it was.
 */
@Composable
internal fun ResumeBlock(book: Book, facts: ResumeFacts, actions: BookRowActions, modifier: Modifier = Modifier) {
    DeletingRow(
        delete = DeleteAction(tag = libraryResumeDeleteTag(book.id), onDelete = { actions.onDelete(book) }),
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        // The card draws its own hover trash in the corner, so the row's reserved
        // slot would only narrow the card.
        hoverDelete = false,
    ) { content ->
        val snippets = LocalBookSnippets.current
        val snippet by rememberDerivedAsset(book.id, snippets) { snippets.snippet(book.id) }
        BlockCard(book, facts.copy(snippet = snippet), actions, content)
    }
}

/**
 * The block's one card: the cover, the facts, the filled action, then the
 * hairline and the snippet, all on the theme's surface (docs/design.md §7.1).
 */
@Composable
private fun BlockCard(book: Book, facts: ResumeFacts, actions: BookRowActions, modifier: Modifier) {
    val theme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surface, LektoRadii.Surface)
            .border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Surface)
            .padding(LektoSpacing.InsidePanel)
            .hoverable(interaction)
            .plainClickable(role = Role.Button) { actions.onOpen(book) },
    ) {
        Box(Modifier.fillMaxWidth()) {
            BlockFacts(book, facts, actions)
            // A thumb swipes the card; a mouse gets the row's hover trash, which
            // is the only way a pointer can reach the delete (issue #288).
            if (!touchClient && hovered) {
                BlockDelete(book, actions, Modifier.align(Alignment.TopEnd))
            }
        }
        facts.snippet?.let { snippet ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = LektoSpacing.InsidePanel)
                    .height(HAIRLINE_THICKNESS)
                    .background(theme.outline),
            )
            Text(
                text = snippet,
                style = ReadingSnippet,
                maxLines = SNIPPET_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
            )
        }
    }
}

/**
 * The block's accessible item: the cover, the title, the **Author**, the chapter
 * and percentage, the rule and the `Resume` action, merged so a screen reader
 * reaches the book in one stop.
 */
@Composable
private fun BlockFacts(book: Book, facts: ResumeFacts, actions: BookRowActions) {
    val texts = LocalAppTexts.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                onClick(label = texts[TextKey.RESUME]) {
                    actions.onOpen(book)
                    true
                }
            }
            .testTag(libraryResumeBlockTag(book.id)),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BlockCover(book)
            BlockTexts(book, facts)
        }
        Button(
            onClick = { actions.onOpen(book) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LektoSpacing.InsidePanel)
                .handCursor()
                // The facts own the block's action, so the button is its visible
                // control, not a second stop for a screen reader.
                .clearAndSetSemantics {},
            shape = LektoRadii.Control,
        ) {
            Text(texts[TextKey.RESUME])
        }
    }
}

/** The block's title, **Author** and line, beside the cover. */
@Composable
private fun RowScope.BlockTexts(book: Book, facts: ResumeFacts) {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .weight(1f)
            // A mouse's hover trash overlays this end of the first line, so the
            // title and the state never run under it; a thumb has no trash there.
            .padding(start = LektoSpacing.BetweenRows, end = if (touchClient) 0.dp else LektoSpacing.TouchTarget),
    ) {
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        book.author?.takeIf { name -> name.isNotBlank() }?.let { author ->
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.onSurfaceVariant,
                modifier = Modifier.padding(top = TITLE_TO_AUTHOR),
            )
        }
        BlockLine(book, facts)
    }
}

/**
 * The block's cover: the art at its own ratio, never cropped, or the **Reading
 * language**'s plaque where a book has none (docs/design.md §7.1; issues #259,
 * #288).
 */
@Composable
private fun BlockCover(book: Book) {
    val cover = rememberCover(book.id, LocalBookCovers.current)
    if (cover == null) {
        LanguageVignette(book.language, width = RESUME_COVER_WIDTH, height = RESUME_COVER_HEIGHT)
        return
    }
    Image(
        bitmap = cover,
        contentDescription = languageCode(book.language).ifEmpty { null },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .width(RESUME_COVER_WIDTH)
            .heightIn(max = RESUME_COVER_HEIGHT)
            .clip(LektoRadii.Control)
            .testTag(libraryCoverTag(book.id)),
    )
}

/** The block's delete: the row's trash, always on a thumb and on hover with a mouse. */
@Composable
private fun BlockDelete(book: Book, actions: BookRowActions, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(LektoSpacing.TouchTarget)
            .handCursor()
            .testTag(libraryResumeDeleteTag(book.id))
            .plainClickable(role = Role.Button, onClick = { actions.onDelete(book) }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = LektoIcons.Trash,
            contentDescription = LocalAppTexts.current[TextKey.DELETE],
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(DELETE_ICON_SIZE),
        )
    }
}

/**
 * The block's line: `Chapter N · 28 %`, or `28 %` when the book names no
 * **Chapter**, with the row's own 3 dp rule under it at the text column's width
 * (docs/design.md §7.1; issues #196, #260, #288). A book whose length is still
 * being measured reads `…` rather than a percentage it does not have.
 *
 * The chapter is the part that gives way: it truncates on one line so the
 * percentage beside it never wraps away from its number.
 */
@Composable
private fun BlockLine(book: Book, facts: ResumeFacts) {
    val texts = LocalAppTexts.current
    val message = blockLineMessage(facts) ?: return
    val measuring = facts.progress == BookProgress.Unmeasured
    Row(
        modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
        verticalAlignment = Alignment.Bottom,
    ) {
        facts.chapter?.let { chapter ->
            Text(
                text = chapter,
                style = ResumeLine,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(LektoSpacing.InsideLabelPair))
        }
        Text(
            text = texts.message(message),
            style = ResumeLine,
            maxLines = 1,
            // `…` alone is not something a screen reader can read, so the block
            // says what the data is waiting for until the measurement lands.
            modifier = if (measuring) {
                Modifier.semantics { contentDescription = texts[TextKey.LIBRARY_ROW_MEASURING_DESCRIPTION] }
            } else {
                Modifier
            },
        )
    }
    if (facts.progress is BookProgress.Measured) ProgressRule(book.id, facts.progress.percent)
}

/**
 * The message the block's line carries: the percentage alone, or the same
 * percentage after the chapter, or the measuring placeholder. `null` is a book
 * with no **Reading position**, which the block never leads with.
 */
private fun blockLineMessage(facts: ResumeFacts): AppMessage? = when (val progress = facts.progress) {
    is BookProgress.Measured ->
        if (facts.chapter == null) {
            AppMessage.of(TextKey.LIBRARY_BLOCK_PROGRESS, progress.percent)
        } else {
            AppMessage.of(TextKey.LIBRARY_BLOCK_PROGRESS_AFTER_CHAPTER, progress.percent)
        }

    BookProgress.Unmeasured ->
        if (facts.chapter == null) {
            AppMessage.of(TextKey.LIBRARY_BLOCK_MEASURING)
        } else {
            AppMessage.of(TextKey.LIBRARY_BLOCK_MEASURING_AFTER_CHAPTER)
        }

    BookProgress.NotStarted -> null
}
