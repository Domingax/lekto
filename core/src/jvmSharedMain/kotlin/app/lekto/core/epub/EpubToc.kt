package app.lekto.core.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import org.w3c.dom.Element as XmlElement

/**
 * One entry of an EPUB's table of contents, before it is resolved to a place in
 * the book: the author's [label], the [href] it points at, relative to the
 * document that declared it, and any entries nested under it (ADR-0037).
 */
internal data class TocEntry(val label: String, val href: String?, val children: List<TocEntry>)

/**
 * Reads the two tables of contents an EPUB carries: the EPUB 3 navigation
 * document and the EPUB 2 NCX. Both produce the same [TocEntry] tree, so the
 * parser resolves and orders them once.
 *
 * The navigation document is HTML, so jsoup reads it tolerantly; the NCX is XML,
 * so the hardened [OpfXmlReader] does. Neither decides what is usable — an entry
 * that points outside the spine is dropped by the parser, not here.
 */
internal object EpubToc {

    /** Reads the `toc` nav in [bytes], whose hrefs are relative to `baseDirectory`. */
    fun nav(bytes: ByteArray, baseDirectory: String): List<TocEntry> {
        val document = Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        val nav = document.getElementsByTag("nav").firstOrNull { element -> element.isToc }
        val list = nav?.getElementsByTag("ol")?.firstOrNull()
        return list?.let { ordered -> entriesOf(ordered, baseDirectory) }.orEmpty()
    }

    /** Whether a `<nav>` declares itself the table of contents. */
    private val Element.isToc: Boolean
        get() = type().split(' ').contains("toc")

    /** `epub:type` on an EPUB 3 nav, `type` on an older or hand-written one. */
    private fun Element.type(): String = attr("epub:type").ifEmpty { attr("type") }

    private fun entriesOf(list: Element, baseDirectory: String): List<TocEntry> = list.children()
        .filter { child -> child.normalName() == "li" }
        .map { item -> entryOf(item, baseDirectory) }

    private fun entryOf(item: Element, baseDirectory: String): TocEntry {
        val anchor = item.children().firstOrNull { child -> child.normalName() in LABELS }
        val nested = item.children().firstOrNull { child -> child.normalName() == "ol" }
        return TocEntry(
            label = anchor?.text()?.trim().orEmpty(),
            href = anchor?.attr("href")?.takeIf { href -> href.isNotBlank() }
                ?.let { href -> EpubArchive.resolveWithFragment(baseDirectory, href) },
            children = nested?.let { list -> entriesOf(list, baseDirectory) }.orEmpty(),
        )
    }

    /** Reads the NCX in [bytes], whose `src` values are relative to `baseDirectory`. */
    fun ncx(bytes: ByteArray, baseDirectory: String, reader: OpfXmlReader = OpfXmlReader()): List<TocEntry> {
        val root = reader.read(bytes).documentElement
        val navMap = root.childElements().firstOrNull { child -> child.localNameOrName() == "navMap" } ?: root
        return pointsOf(navMap, baseDirectory)
    }

    private fun pointsOf(parent: XmlElement?, baseDirectory: String): List<TocEntry> {
        val element = parent ?: return emptyList()
        return element.childElements()
            .filter { child -> child.localNameOrName() == "navPoint" }
            .map { point -> pointOf(point, baseDirectory) }
    }

    private fun pointOf(point: XmlElement, baseDirectory: String): TocEntry {
        val label = point.childElements()
            .firstOrNull { child -> child.localNameOrName() == "navLabel" }
            ?.textContent
            ?.trim()
            .orEmpty()
        val src = point.childElements()
            .firstOrNull { child -> child.localNameOrName() == "content" }
            ?.getAttribute("src")
            ?.takeIf { href -> href.isNotBlank() }
        return TocEntry(
            label = label,
            href = src?.let { href -> EpubArchive.resolveWithFragment(baseDirectory, href) },
            children = pointsOf(point, baseDirectory),
        )
    }

    private fun XmlElement.childElements(): List<XmlElement> = buildList {
        val children = childNodes
        for (index in 0 until children.length) {
            (children.item(index) as? XmlElement)?.let { child -> add(child) }
        }
    }

    /** A namespace-aware parser reports the local name; a tolerant one may only know the qualified name. */
    private fun XmlElement.localNameOrName(): String = localName ?: tagName

    private val LABELS = setOf("a", "span")
}
