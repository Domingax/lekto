package app.lekto.core.epub

import app.lekto.testkit.EpubFixtures
import app.lekto.testkit.TestResources
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The extraction golden the spike asks for (ticket #10): the text pulled from the
 * deliberately awkward EPUB is pinned in `resources/golden/awkward-epub.txt`, so
 * a parser change that silently drops a paragraph, an entity or a heading fails
 * here until the golden is deliberately updated.
 */
class EpubGoldenTest :
    FunSpec({

        test("the awkward EPUB extracts to the pinned text") {
            val extracted = EpubParser().parse(EpubFixtures.awkward()).plainText()

            extracted shouldBe TestResources.text("/golden/awkward-epub.txt")
        }
    })
