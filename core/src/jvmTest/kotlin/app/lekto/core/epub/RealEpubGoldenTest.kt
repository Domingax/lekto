package app.lekto.core.epub

import app.lekto.testkit.TestResources
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The golden over a **real** book (ticket #10): *The Yellow Wallpaper* by
 * Charlotte Perkins Gilman, from Project Gutenberg eBook #1952. It is an EPUB 2
 * produced by Ebookmaker — a Project Gutenberg boilerplate chunk in the spine, a
 * DTD reference, a cover item, nested `div`s and a `<br/>` — exactly the kind of
 * real-world input the generated fixture cannot fully imitate. The archive is
 * committed (`resources/epub/pg1952.epub`; public domain in the US) so the
 * golden is deterministic and needs no network; see the README beside it.
 */
class RealEpubGoldenTest :
    FunSpec({

        val archive = TestResources.bytes("/epub/pg1952.epub")

        test("a real book parses with its metadata and reading order") {
            val book = EpubParser().parse(archive)

            book.title shouldBe "The Yellow Wallpaper"
            book.language shouldBe "en"
            book.blocks.size shouldBe 287
        }

        test("the real book extracts to the pinned text") {
            val extracted = EpubParser().parse(archive).plainText()

            extracted shouldBe TestResources.text("/golden/pg1952.txt")
        }
    })
