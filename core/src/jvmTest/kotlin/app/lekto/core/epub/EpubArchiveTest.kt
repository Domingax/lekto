package app.lekto.core.epub

import app.lekto.testkit.EpubFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The container helpers behind the parser (ticket #10): ZIP reading and href
 * resolution, including the `..` and fragment cases a real book's OPF uses.
 */
class EpubArchiveTest :
    FunSpec({

        test("resolves an href against the OPF's directory") {
            EpubArchive.resolve("OEBPS", "text/chapter.xhtml") shouldBe "OEBPS/text/chapter.xhtml"
        }

        test("collapses a parent-directory segment") {
            EpubArchive.resolve("OEBPS/text", "../images/cover.png") shouldBe "OEBPS/images/cover.png"
        }

        test("drops a fragment and a leading directory from the base") {
            EpubArchive.resolve("", "chapter.xhtml#heading") shouldBe "chapter.xhtml"
        }

        test("reads an archive entry") {
            val archive = EpubArchive.of(EpubFixtures.minimal())

            String(archive.read("mimetype")) shouldBe "application/epub+zip"
        }

        test("reports a missing entry by name") {
            val archive = EpubArchive.of(EpubFixtures.minimal())

            shouldThrow<EpubParseException> { archive.read("nope.xhtml") }.message shouldContain "nope.xhtml"
        }

        test("rejects bytes that are not a ZIP archive") {
            shouldThrow<EpubParseException> { EpubArchive.of(byteArrayOf(1, 2, 3)) }
        }
    })
