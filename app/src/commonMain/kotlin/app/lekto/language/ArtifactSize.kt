package app.lekto.language

import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.TextKey

/**
 * An artifact's published size in the reader's language (docs/design.md §7.6):
 * the manifest carries the compressed asset's byte count, and a download says
 * what it will cost before it starts. Units are decimal and rounded to the
 * nearest whole unit, so a 12.3 MB pack reads `12 MB` and never a long decimal.
 */
internal fun artifactSizeText(texts: AppTexts, bytes: Long): String = when {
    bytes >= GIGABYTE -> texts.message(AppMessage.of(TextKey.SIZE_GIGABYTES, count(bytes, GIGABYTE)))
    bytes >= MEGABYTE -> texts.message(AppMessage.of(TextKey.SIZE_MEGABYTES, count(bytes, MEGABYTE)))
    else -> texts.message(AppMessage.of(TextKey.SIZE_KILOBYTES, count(bytes, KILOBYTE)))
}

/** [bytes] in [unit]s, rounded to the nearest whole unit and never below one. */
private fun count(bytes: Long, unit: Long): String = ((bytes + unit / 2) / unit).coerceAtLeast(1).toString()

private const val KILOBYTE = 1_000L
private const val MEGABYTE = 1_000_000L
private const val GIGABYTE = 1_000_000_000L
