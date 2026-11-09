package app.lekto.language

/**
 * The one address the app follows without the **Language manifest**: the
 * manifest itself, at the stable `releases/latest` alias of the data repository
 * (ADR-0047). Dictionary data lives beside the application, never in its
 * releases, so the application's own `latest` keeps meaning its latest version
 * (issues #145, #306).
 * Every pack and difficulty table is fetched from the address the manifest
 * carries, so the manifest is the only moving URL and the bytes behind each
 * artefact URL never change (issue #305, ADR-0047).
 */
object LanguageRelease {
    const val URL: String = "https://github.com/Domingax/lekto-data/releases/latest/download/manifest.json"
}
