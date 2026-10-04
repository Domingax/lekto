package app.lekto.core.text

/**
 * The base language of a BCP-47 tag: `en-US` and `en_GB` both become `en`. The
 * dictionary pack and a speech engine are both keyed by the base tag (ADR-0006,
 * issue #21), so the collapse lives once rather than in each.
 */
fun baseLanguage(tag: String): String = tag.substringBefore('-').substringBefore('_').lowercase()
