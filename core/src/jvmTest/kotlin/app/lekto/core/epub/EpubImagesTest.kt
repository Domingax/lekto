package app.lekto.core.epub

import app.lekto.core.text.BlockKind
import app.lekto.core.text.IMAGE_PLACEHOLDER
import app.lekto.testkit.EpubFixtures
import app.lekto.testkit.parseText
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Pictures through the parser (issue #263): a figure becomes a block of its own,
 * an image inside a paragraph splits it, and a picture the build cannot show
 * costs the picture and never the chapter.
 */
class EpubImagesTest :
    FunSpec({

        val parsed = EpubParser().parse(EpubFixtures.figures())

        test("a figure becomes an image block carrying its alt and its ratio") {
            val image = parsed.text.blocks.first { block -> block.kind == BlockKind.IMAGE }

            image.image shouldNotBe null
            image.image?.alt shouldBe "A bar chart"
            image.image?.ratio shouldBe 2.0f
            image.text shouldBe IMAGE_PLACEHOLDER
        }

        test("an image inside a paragraph splits it around the picture") {
            parsed.text.blocks.map { block -> block.kind } shouldContainExactly listOf(
                BlockKind.HEADING,
                BlockKind.IMAGE,
                BlockKind.PARAGRAPH,
                BlockKind.IMAGE,
                BlockKind.PARAGRAPH,
                BlockKind.PARAGRAPH,
                BlockKind.PARAGRAPH,
                BlockKind.PARAGRAPH,
            )
            parsed.text.blocks.map { block -> block.text } shouldContainExactly listOf(
                "Pictures",
                IMAGE_PLACEHOLDER,
                "Before",
                IMAGE_PLACEHOLDER,
                "after.",
                "Remote picture",
                "Missing picture",
                "Not a picture",
            )
        }

        test("the picture's bytes are extracted once, under a safe asset name") {
            parsed.assets.size shouldBe 1
            val asset = parsed.assets.single()
            asset.name.matches(Regex("[0-9a-f]{8}\\.png")) shouldBe true
            ImageHeader.sizeOf(asset.bytes) shouldBe ImageSize(40, 20)
            parsed.text.blocks
                .mapNotNull { block -> block.image?.name }
                .distinct() shouldContainExactly listOf(asset.name)
        }

        test("a picture it cannot show falls back to its alt, one paragraph each") {
            parsed.text.blocks
                .dropLast(3)
                .map { block -> block.text } shouldContainExactly listOf(
                "Pictures",
                IMAGE_PLACEHOLDER,
                "Before",
                IMAGE_PLACEHOLDER,
                "after.",
            )
            parsed.text.blocks.takeLast(3).all { block -> block.kind == BlockKind.PARAGRAPH } shouldBe true
        }

        test("an asset name is stable and vault-safe whatever the book calls its file") {
            val first = imageAssetName("OEBPS/images/Planche 1.jpeg")
            val second = imageAssetName("OEBPS/images/Planche 1.jpeg")

            first shouldBe second
            first.matches(Regex("[0-9a-f]{8}\\.jpeg")) shouldBe true
            imageAssetName("OEBPS/images/no-extension") shouldBe imageAssetName("OEBPS/images/no-extension")
        }

        test("a book with no picture extracts no asset") {
            val blocks = EpubParser().parseText(EpubFixtures.awkward()).blocks

            blocks.none { block -> block.kind == BlockKind.IMAGE } shouldBe true
        }
    })
