@file:Suppress("MagicNumber") // A binary format is a table of byte offsets and masks; naming each one hides the layout.

package app.lekto.core.epub

/** An image's pixel size, as its own header declares it. */
internal data class ImageSize(val width: Int, val height: Int) {

    /** Width ÷ height, or `1` when a header declares no height, so a placeholder stays square. */
    val ratio: Float get() = if (height > 0 && width > 0) width.toFloat() / height else 1f
}

/**
 * The pixel size a raster image declares in the first bytes of its header.
 *
 * The reader reserves an image's rectangle **before** decoding it, so pagination
 * knows where the picture goes without ever paying a decode: a placeholder one
 * glyph wide and `width ÷ height` tall is enough. Only the header is read, and a
 * format this does not know — or a corrupt one — returns `null`, which the
 * caller turns into the source's `alt` (issue #263).
 *
 * Raster only, by decision: JPEG, PNG, GIF and WebP. SVG needs a renderer and is
 * #269.
 */
@Suppress("TooManyFunctions") // One reader per format, plus the little-endian accessors they share.
internal object ImageHeader {
    /** The size [bytes] declares, or `null` when they are not an image this reads. */
    fun sizeOf(bytes: ByteArray): ImageSize? = when {
        startsWith(bytes, PNG) -> png(bytes)
        startsWith(bytes, JPEG) -> jpeg(bytes)
        startsWith(bytes, GIF) -> gif(bytes)
        startsWith(bytes, RIFF) && ascii(bytes, 8, 4) == "WEBP" -> webp(bytes)
        else -> null
    }

    /** PNG puts its size in the IHDR chunk, which a valid file always writes first. */
    private fun png(bytes: ByteArray): ImageSize? {
        if (ascii(bytes, 12, 4) != "IHDR") return null
        val width = u32be(bytes, 16)
        val height = u32be(bytes, 20)
        return sizeOf(width, height)
    }

    /** GIF's screen descriptor carries the size as two little-endian words. */
    private fun gif(bytes: ByteArray): ImageSize? = sizeOf(u16le(bytes, 6), u16le(bytes, 8))

    /**
     * JPEG walks its marker segments to the first start-of-frame, which is the
     * only place the size is written. Every other segment declares its own
     * length, so the walk skips them without decoding anything.
     */
    @Suppress("CyclomaticComplexMethod") // One branch per marker class; splitting it hides the segment walk.
    private fun jpeg(bytes: ByteArray): ImageSize? {
        var index = 2 // past SOI
        var size: ImageSize? = null
        while (size == null && index + 3 < bytes.size) {
            if (u8(bytes, index) != 0xFF) {
                index++
                continue
            }
            when (u8(bytes, index + 1)) {
                0xFF -> index++

                // fill byte
                0xD8, 0x01, in 0xD0..0xD7 -> index += 2

                // a marker with no length
                0xD9, 0xDA -> return null

                // end of image, or start of scan
                in SOF_MARKERS -> size = sizeOf(width = u16be(bytes, index + 7), height = u16be(bytes, index + 5))

                else -> index += 2 + u16be(bytes, index + 2)
            }
        }
        return size
    }

    /** WebP's three chunk shapes each pack the size differently. */
    private fun webp(bytes: ByteArray): ImageSize? = when (ascii(bytes, 12, 4)) {
        "VP8X" -> sizeOf(u24le(bytes, 24) + 1, u24le(bytes, 27) + 1)

        "VP8L" -> {
            val bits = u32le(bytes, 21)
            sizeOf((bits and 0x3FFF) + 1, ((bits ushr 14) and 0x3FFF) + 1)
        }

        "VP8 " -> if (u24le(bytes, 23) == 0x2A019D) {
            sizeOf(u16le(bytes, 26) and 0x3FFF, u16le(bytes, 28) and 0x3FFF)
        } else {
            null
        }

        else -> null
    }

    private fun sizeOf(width: Int, height: Int): ImageSize? =
        ImageSize(width, height).takeIf { size -> size.width > 0 && size.height > 0 }

    private fun startsWith(bytes: ByteArray, magic: ByteArray): Boolean =
        bytes.size >= magic.size && magic.indices.all { index -> bytes[index] == magic[index] }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String? {
        if (offset < 0 || offset + length > bytes.size) return null
        return buildString(length) {
            for (index in offset until offset + length) append(u8(bytes, index).toChar())
        }
    }

    private fun u8(bytes: ByteArray, index: Int): Int = if (index in bytes.indices) bytes[index].toInt() and 0xFF else 0

    private fun u16be(bytes: ByteArray, index: Int): Int = (u8(bytes, index) shl 8) or u8(bytes, index + 1)

    private fun u16le(bytes: ByteArray, index: Int): Int = u8(bytes, index) or (u8(bytes, index + 1) shl 8)

    private fun u24le(bytes: ByteArray, index: Int): Int =
        u8(bytes, index) or (u8(bytes, index + 1) shl 8) or (u8(bytes, index + 2) shl 16)

    private fun u32be(bytes: ByteArray, index: Int): Int =
        (u8(bytes, index) shl 24) or (u8(bytes, index + 1) shl 16) or (u8(bytes, index + 2) shl 8) or
            u8(bytes, index + 3)

    private fun u32le(bytes: ByteArray, index: Int): Int =
        u8(bytes, index) or (u8(bytes, index + 1) shl 8) or (u8(bytes, index + 2) shl 16) or
            (u8(bytes, index + 3) shl 24)

    private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val GIF = "GIF8".encodeToByteArray()
    private val RIFF = "RIFF".encodeToByteArray()

    /** Every start-of-frame marker; C4, C8 and CC are not frames despite the pattern. */
    private val SOF_MARKERS = setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)
}
