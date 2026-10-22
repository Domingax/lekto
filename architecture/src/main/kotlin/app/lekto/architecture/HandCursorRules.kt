package app.lekto.architecture

/**
 * Every clickable surface in `app/src/commonMain` carries the hand cursor
 * (issue #217; docs/design.md §8). A pointer lives in the platform window, not in
 * the semantics tree, so no Compose UI test can see it; this rule is the
 * automated net, and the pointer itself is checked by hand on desktop.
 *
 * The two shapes are the ones the ticket names: a Material control call (a
 * button, an icon button, a menu item) carries `handCursor` inside its arguments
 * (its `modifier`), and a `.clickable`, `.toggleable` or `.selectable` carries it
 * in the same modifier chain. The reader's page overrides descendants with
 * `pointerHoverIcon` directly, because its `SelectionContainer` would otherwise
 * keep the text cursor; that surface is not one of these markers.
 */
object HandCursorRules {

    fun check(repository: Repository): List<Violation> = repository.sources
        .filter { source -> source.module == Modules.APP && source.sourceSet == COMMON_MAIN }
        .flatMap { source -> missingCursor(source) }

    private fun missingCursor(source: KotlinSource): List<Violation> {
        if (source.text.isEmpty()) return emptyList()
        val masked = CursorScan.masked(source.text)
        return (controlMarkers(masked) + modifierMarkers(masked))
            .filterNot { marker -> marker.carries }
            .map { marker ->
                Violation(
                    Rules.HAND_CURSOR,
                    source.path,
                    "the ${marker.name} at line ${lineOf(masked, marker.offset)} does not carry handCursor",
                )
            }
    }

    private fun controlMarkers(masked: String): List<Marker> = CONTROL_PATTERN.findAll(masked).map { match ->
        val open = match.range.last
        val close = CursorScan.matchingDelimiter(masked, open, '(', ')')
        Marker(
            name = match.value.substringBefore('(').trim(),
            offset = match.range.first,
            carries = masked.substring(open, close + 1).contains(HAND_CURSOR),
        )
    }.toList()

    private fun modifierMarkers(masked: String): List<Marker> = MODIFIER_PATTERN.findAll(masked).map { match ->
        val open = match.range.last
        val close = if (masked[open] == '{') {
            CursorScan.matchingDelimiter(masked, open, '{', '}')
        } else {
            CursorScan.matchingDelimiter(masked, open, '(', ')')
        }
        val start = ChainScan.start(masked, match.range.first)
        val end = ChainScan.end(masked, close)
        Marker(
            name = ".${match.groupValues[1]}",
            offset = match.range.first,
            carries = masked.substring(start, end + 1).contains(HAND_CURSOR),
        )
    }.toList()

    private fun lineOf(masked: String, offset: Int): Int =
        masked.substring(0, offset).count { char -> char == '\n' } + 1

    private data class Marker(val name: String, val offset: Int, val carries: Boolean)

    private const val COMMON_MAIN = "commonMain"
    private const val HAND_CURSOR = "handCursor"

    private val CONTROL_PATTERN = Regex(
        """(?<![\w.])(Button|OutlinedButton|TextButton|FilledTonalButton|ElevatedButton|IconButton|""" +
            """DropdownMenuItem)\s*\(""",
    )
    private val MODIFIER_PATTERN = Regex("""\.\s*(clickable|toggleable|selectable)\s*[({]""")
}
