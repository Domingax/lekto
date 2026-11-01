package app.lekto.testkit

import app.lekto.core.text.BookTextParser
import app.lekto.core.text.StructuredText

/**
 * The parsed text alone. A parser also returns the binaries it extracted from
 * the original — an EPUB's images — and a test that only checks the words never
 * needs them, so this keeps the assertions about text readable.
 */
fun BookTextParser.parseText(bytes: ByteArray): StructuredText = parse(bytes).text
