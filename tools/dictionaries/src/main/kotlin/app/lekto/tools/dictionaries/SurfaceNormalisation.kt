package app.lekto.tools.dictionaries

import java.text.Normalizer

/**
 * Normalises a surface form exactly as `core`'s `normaliseSurface` does: NFC
 * first, then a locale-independent lower-case fold (ADR-0006).
 *
 * The rule is duplicated on purpose: `tools/dictionaries` is a leaf that may not
 * depend on `core` (architecture policy), and the pack's keys must match the
 * app's lookup keys bit for bit or composed and decomposed accents would miss.
 */
internal fun normaliseSurface(surface: String): String = Normalizer.normalize(surface.lowercase(), Normalizer.Form.NFC)

/**
 * Whether [surface] is a single token: non-blank and free of Unicode whitespace.
 *
 * The trim drops multi-word surfaces — Wiktextract's `"avoir + past participle"`
 * style forms and multi-word headwords — while keeping hyphenated and
 * apostrophised single tokens.
 */
internal fun isSingleToken(surface: String): Boolean = surface.isNotBlank() && surface.none(Char::isWhitespace)
