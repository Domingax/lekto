package app.lekto.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import app.lekto.ui.DeleteAction
import app.lekto.ui.DeletingRow
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.handCursor
import app.lekto.ui.icons.EllipsisVertical
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.plainClickable

/** The test tag on the resume block, so a UI test can read it apart from the shelf. */
internal fun libraryResumeBlockTag(bookId: String): String = "library-resume-$bookId"

/** The test tag on the block's overflow menu, which opens the same delete confirmation as a row. */
internal fun libraryResumeDeleteTag(bookId: String): String = "library-resume-delete-$bookId"

/** The block's cover: 64 dp wide, and at most 92 dp tall so a portrait cover keeps its ratio (§7.1). */
internal val RESUME_COVER_WIDTH = 64.dp
internal val RESUME_COVER_HEIGHT = 92.dp

/** How many lines of the saved paragraph the block shows (docs/design.md §7.1; issue #288). */
private const val SNIPPET_LINES = 2

/**
 * What the block states about its book: the **Reading progress** and the
 * **Chapter** the **Reading position** sits in, or `null` when no chapter is
 * known. Two independent facts, bundled so the block's signatures stay short.
 */
internal data class ResumeFacts(val progress: BookProgress, val chapter: String?)

/** The overflow icon's size: the same 20 dp a control's glyph takes in the identity (§5). */
private val MENU_ICON_SIZE = 20.dp

/**
 * The **Resume block**: the most recently read unfinished book leaves the shelf
 * and leads above it (issue #288; docs/design.md §7.1). Its cover, title,
 * **Author**, current **Chapter** and percentage, the row's own 3 dp rule, a
 * filled `Resume`, then a hairline and the first **Paragraph** at the saved
 * position.
 *
 * The card is one accessible item: its facts merge into a single node and
 * `Resume` is its action, so a screen reader reaches the book in one stop. The
 * `⋮` in the corner is the block's own control — a block that large cannot rely
 * on the row's trailing-edge swipe alone — and it opens the same delete
 * confirmation, as does the swipe. Delete never sits on the shelf as a red word.
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
        // The block carries its own always-visible overflow menu, so the row's
        // hover trash would be a second affordance for the same confirmation.
        hoverDelete = false,
    ) { content ->
        BlockFrame(book, facts, actions, content)
    }
}

/** The block's card, its hairline and its snippet. */
@Composable
private fun BlockFrame(book: Book, facts: ResumeFacts, actions: BookRowActions, modifier: Modifier) {
    val snippets = LocalBookSnippets.current
    val snippet by rememberDerivedAsset(book.id, snippets) { snippets.snippet(book.id) }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            BlockCard(book, facts, actions)
            BlockMenu(book, actions, Modifier.align(Alignment.TopEnd).padding(LektoSpacing.InsideLabelPair))
        }
        if (snippet != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = LektoSpacing.InsidePanel)
                    .height(HAIRLINE_THICKNESS)
                    .background(MaterialTheme.colorScheme.outline),
            )
            Text(
                text = snippet.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = SNIPPET_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
            )
        }
    }
}

/**
 * The bordered card: the cover, the facts and the filled action. It is the
 * block's one accessible item — the children merge into it, a tap anywhere on it
 * resumes, and the `Resume` button is the visible control that says so — while
 * the overflow menu sits beside it in the frame, a control of its own.
 */
@Composable
private fun BlockCard(book: Book, facts: ResumeFacts, actions: BookRowActions) {
    val texts = LocalAppTexts.current
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Surface)
            .padding(LektoSpacing.InsidePanel)
            .semantics(mergeDescendants = true) {}
            // The card is the block's action: a tap anywhere on it resumes, and
            // the filled `Resume` is the visible control that says so.
            .plainClickable(role = Role.Button, onClickLabel = texts[TextKey.RESUME]) { actions.onOpen(book) }
            .testTag(libraryResumeBlockTag(book.id)),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BlockCover(book)
            Column(
                modifier = Modifier
                    .weight(1f)
                    // The overflow menu overlays this end of the first line, so
                    // the title and the state never run under it.
                    .padding(start = LektoSpacing.BetweenRows, end = LektoSpacing.TouchTarget),
                verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
            ) {
                Text(book.title, style = MaterialTheme.typography.titleMedium)
                book.author?.takeIf { name -> name.isNotBlank() }?.let { author ->
                    Text(author, style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                }
                BlockLine(book, facts)
            }
        }
        Button(
            onClick = { actions.onOpen(book) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LektoSpacing.InsidePanel)
                .handCursor()
                // The card owns the block's action, so the button is its visible
                // control, not a second stop for a screen reader.
                .clearAndSetSemantics {},
            shape = LektoRadii.Control,
        ) {
            Text(texts[TextKey.RESUME])
        }
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

/** The `⋮`: the block's always-visible way to the delete confirmation, never a red word. */
@Composable
private fun BlockMenu(book: Book, actions: BookRowActions, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(LektoSpacing.TouchTarget)
            .handCursor()
            .testTag(libraryResumeDeleteTag(book.id))
            .plainClickable(role = Role.Button, onClick = { actions.onDelete(book) }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = LektoIcons.EllipsisVertical,
            contentDescription = LocalAppTexts.current[TextKey.DELETE],
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MENU_ICON_SIZE),
        )
    }
}

/**
 * The block's line: `Chapter N · 28 %`, or `28 %` when the book names no
 * **Chapter**, with the row's own 3 dp rule under it at the text column's width
 * (docs/design.md §7.1; issues #196, #260, #288). A book whose length is still
 * being measured reads `…` rather than a percentage it does not have.
 */
@Composable
private fun BlockLine(book: Book, facts: ResumeFacts) {
    val texts = LocalAppTexts.current
    val progress = facts.progress
    val chapter = facts.chapter
    val message = when (progress) {
        is BookProgress.Measured -> if (chapter == null) {
            AppMessage.of(TextKey.LIBRARY_BLOCK_PROGRESS, progress.percent)
        } else {
            AppMessage.of(TextKey.LIBRARY_BLOCK_CHAPTER_PROGRESS, chapter, progress.percent)
        }

        BookProgress.Unmeasured -> if (chapter == null) {
            AppMessage.of(TextKey.LIBRARY_BLOCK_MEASURING)
        } else {
            AppMessage.of(TextKey.LIBRARY_BLOCK_CHAPTER_MEASURING, chapter)
        }

        BookProgress.NotStarted -> null
    }
    if (message == null) return
    val measuring = progress == BookProgress.Unmeasured
    Text(
        text = texts.message(message),
        style = MaterialTheme.typography.bodySmall,
        // `…` alone is not something a screen reader can read, so the block says
        // what the data is waiting for until the measurement lands.
        modifier = if (measuring) {
            Modifier.semantics { contentDescription = texts[TextKey.LIBRARY_ROW_MEASURING_DESCRIPTION] }
        } else {
            Modifier
        },
    )
    if (progress is BookProgress.Measured) ProgressRule(book.id, progress.percent)
}
