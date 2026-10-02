package app.lekto.core.text

import java.text.Normalizer

/**
 * The JVM's [normaliseSurface]: `java.text.Normalizer` gives the NFC form, and
 * `lowercase()` folds case. The JVM locale is deliberately not used — the fold
 * must be locale-independent (see [normaliseSurface]).
 */
actual fun normaliseSurface(surface: String): String = Normalizer.normalize(surface.lowercase(), Normalizer.Form.NFC)
