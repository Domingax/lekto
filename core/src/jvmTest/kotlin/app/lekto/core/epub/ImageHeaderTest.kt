package app.lekto.core.epub

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The header reader the paginator depends on: an image reserves its rectangle
 * before it is decoded, so the size must come from the first bytes of the file,
 * never from a decoder (issue #263).
 */
class ImageHeaderTest :
    FunSpec({

        test("a PNG declares its size in the IHDR chunk") {
            ImageHeader.sizeOf(png(40, 20)) shouldBe ImageSize(40, 20)
        }

        test("a GIF declares its screen size") {
            ImageHeader.sizeOf(gif(12, 7)) shouldBe ImageSize(12, 7)
        }

        test("a JPEG declares its size in its start of frame") {
            ImageHeader.sizeOf(jpeg(1024, 768)) shouldBe ImageSize(1024, 768)
        }

        test("a WebP declares its size in its VP8X chunk") {
            ImageHeader.sizeOf(webp(640, 480)) shouldBe ImageSize(640, 480)
        }

        test("the ratio is width over height, and square when a header declares none") {
            ImageSize(40, 20).ratio shouldBe 2.0f
            ImageSize(40, 0).ratio shouldBe 1.0f
        }

        test("anything that is not a picture this build reads has no size") {
            ImageHeader.sizeOf("not an image at all".encodeToByteArray()) shouldBe null
            ImageHeader.sizeOf(ByteArray(0)) shouldBe null
            ImageHeader.sizeOf(png(0, 0)) shouldBe null
        }
    })

private fun png(width: Int, height: Int): ByteArray = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0, 0, 0, 13,
    'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
    (width ushr 24).toByte(), (width ushr 16).toByte(), (width ushr 8).toByte(), width.toByte(),
    (height ushr 24).toByte(), (height ushr 16).toByte(), (height ushr 8).toByte(), height.toByte(),
    8, 2, 0, 0, 0,
)

private fun gif(width: Int, height: Int): ByteArray = byteArrayOf(
    'G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte(),
    (width and 0xFF).toByte(), (width ushr 8).toByte(),
    (height and 0xFF).toByte(), (height ushr 8).toByte(),
)

private fun jpeg(width: Int, height: Int): ByteArray {
    // An APP0 segment the walk must skip, then the start of frame it reads.
    val app0 = byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00)
    val frame = byteArrayOf(
        0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
        (height ushr 8).toByte(), (height and 0xFF).toByte(),
        (width ushr 8).toByte(), (width and 0xFF).toByte(),
        0x03, 0x01, 0x11, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
    )
    return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + app0 + frame
}

private fun webp(width: Int, height: Int): ByteArray {
    fun u24(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
    )
    return "RIFF".encodeToByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".encodeToByteArray() +
        "VP8X".encodeToByteArray() + byteArrayOf(10, 0, 0, 0) + byteArrayOf(0, 0, 0, 0) +
        u24(width - 1) + u24(height - 1)
}
