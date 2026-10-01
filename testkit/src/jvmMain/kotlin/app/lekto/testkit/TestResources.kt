package app.lekto.testkit

import java.io.InputStream

/**
 * Reads a committed test resource — a golden, an EPUB fixture — off the test
 * classpath. It lives in `testkit` because a test source set may hold only
 * `*Test.kt` files (docs/testing.md#naming-and-placement), so a helper shared by
 * two or more tests has nowhere else to go.
 */
object TestResources {

    /** The bytes at [path] (classpath-rooted, e.g. `/epub/book.epub`). */
    fun bytes(path: String): ByteArray = stream(path).use { stream -> stream.readBytes() }

    /** The text at [path], with only the trailing newline trimmed. */
    fun text(path: String): String =
        stream(path).bufferedReader().use { reader -> reader.readText() }.trimEnd('\n', '\r')

    private fun stream(path: String): InputStream =
        checkNotNull(TestResources::class.java.getResourceAsStream(path)) { "Missing test resource $path" }
}
