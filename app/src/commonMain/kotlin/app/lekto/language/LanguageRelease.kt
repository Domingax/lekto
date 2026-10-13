package app.lekto.language

/**
 * The **Language manifest** the published workflow produces, at the stable
 * `releases/latest` alias it uploads the pack and the difficulty table to as
 * well (`.../releases/latest/download/<name>`). Pinning it here keeps the fetch
 * address in one named place (issue #145, issue #146).
 */
object LanguageRelease {
    const val URL: String = "https://github.com/Domingax/lekto/releases/latest/download/language-manifest.json"
}
