@file:Suppress("MagicNumber") // The offsets below are the ICO, ICNS and PNG layouts;
// naming a byte offset as a constant would hide the format instead of explaining it.

package app.lekto.tools

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop icon files (issue #177): the hand-written ICO and ICNS containers
 * and the PNG beside them.
 *
 * The formats are fixed-field binary ones, so a wrong length or offset produces a
 * file that a desktop silently refuses. These tests read the output back — header,
 * directory entries, chunk sizes, PNG signature — rather than trusting the writer,
 * and they are also what keeps the renderer covered on the Sonar gate.
 */
class RenderAppIconTest {

    private val directory = Files.createTempDirectory("lekto-icon").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun theIcoListsEveryFrameWithItsPngAtTheDeclaredOffset() {
        val file = File(directory, "icon.ico")

        writeIco(file, listOf(16 to MarkLayer.LETTER, 48 to MarkLayer.MARK, 256 to MarkLayer.MARK))

        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, buffer.short.toInt(), "reserved")
        assertEquals(1, buffer.short.toInt(), "type: icon")
        assertEquals(3, buffer.short.toInt(), "one entry per frame")

        val entries = (1..3).map {
            Entry(
                width = buffer.get().toInt() and 0xFF,
                height = buffer.get().toInt() and 0xFF,
                colours = buffer.get().toInt(),
                reserved = buffer.get().toInt(),
                planes = buffer.short.toInt(),
                bits = buffer.short.toInt(),
                size = buffer.int,
                offset = buffer.int,
            )
        }

        assertEquals(listOf(16, 48, 0), entries.map { it.width }, "0 stands for 256 in an ICO")
        assertTrue(entries.all { it.height == it.width })
        assertTrue(entries.all { it.colours == 0 && it.reserved == 0 && it.planes == 1 && it.bits == 32 })
        entries.forEach { entry ->
            assertEquals(PNG, pngAt(bytes, entry.offset), "a PNG payload at ${entry.offset}")
        }
        assertEquals(bytes.size, entries.last().offset + entries.last().size, "the last frame ends the file")
    }

    @Test
    fun theIcnsWrapsEveryFrameInItsOwnChunk() {
        val file = File(directory, "icon.icns")

        writeIcns(file, listOf(16 to MarkLayer.LETTER, 64 to MarkLayer.MARK, 1024 to MarkLayer.MARK))

        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals("icns", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(bytes.size, buffer.getInt(4), "the header declares the whole file")

        var offset = 8
        val chunks = mutableListOf<String>()
        while (offset < bytes.size) {
            val type = String(bytes, offset, 4, Charsets.US_ASCII)
            val length = buffer.getInt(offset + 4)
            assertEquals(PNG, pngAt(bytes, offset + 8), "a PNG payload inside $type")
            chunks += type
            offset += length
        }

        assertEquals(bytes.size, offset, "the chunks tile the file exactly")
        assertEquals(listOf("icp4", "icp6", "ic10"), chunks, "one Apple code per size")
    }

    @Test
    fun theWindowIconIsAPngOfItsSize() {
        val file = File(directory, "icon.png")

        writePng(file, markPng(128, MarkLayer.MARK))

        val bytes = file.readBytes()
        assertEquals(PNG, pngAt(bytes, 0))
        val width = ByteBuffer.wrap(bytes, 16, 4).order(ByteOrder.BIG_ENDIAN).int
        val height = ByteBuffer.wrap(bytes, 20, 4).order(ByteOrder.BIG_ENDIAN).int
        assertEquals(128, width)
        assertEquals(128, height)
    }

    @Test
    fun theWindowIconAndBothContainersAreWrittenTogether() {
        renderInto(directory)

        listOf("app-icon.png", "app-icon.ico", "app-icon.icns").forEach { name ->
            val file = File(directory, name)
            assertTrue(file.isFile, "missing $name")
            assertTrue(file.length() > 0, "$name is empty")
        }
    }

    private fun pngAt(bytes: ByteArray, offset: Int): List<Byte> = bytes.copyOfRange(offset, offset + PNG.size).toList()

    private data class Entry(
        val width: Int,
        val height: Int,
        val colours: Int,
        val reserved: Int,
        val planes: Int,
        val bits: Int,
        val size: Int,
        val offset: Int,
    )

    private companion object {
        /** The eight bytes every PNG starts with. */
        val PNG: List<Byte> = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).toList()
    }
}
