package app.lekto.architecture

/** One `updates` entry in `.github/dependabot.yml`: an ecosystem, and a directory. */
data class DependabotEntry(val ecosystem: String, val directory: String)

/**
 * A dependency manifest the repository ships, paired with the ecosystem that
 * reads it and the directory a Dependabot entry must name for it.
 *
 * [directory] is the directory the entry declares, which is the manifest's own
 * directory except for `github-actions` — Dependabot always names the repository
 * root there, wherever the workflows sit. [path] is the file a reader opens, so a
 * violation names the manifest rather than a bare directory.
 */
data class PackageManifest(val ecosystem: String, val directory: String, val path: String)

/**
 * Every manifest the repository ships must be on Dependabot's watch list (issue
 * #153). An unlisted ecosystem keeps collecting security alerts that no pull
 * request ever drains, which is how 63 of them accumulated on `main`: the
 * repository had no `.github/dependabot.yml` at all, so GitHub raised the alerts
 * and nothing else happened. Adding a second npm project with no entry to match
 * is therefore a [Violation], not a silent gap.
 *
 * The rule spans two artefacts — the manifests on disk and the config — which is
 * the shape of [DictionaryFormatSynchronisationTest], so it lives beside that
 * guard rather than in [LektoArchitecture.check]: that `Repository` models the
 * module graph, and a dependency manifest is not part of it. The scanner knows
 * the ecosystems the repository uses today; an ecosystem it does not know is a
 * deliberate edit to `RepositoryScanner.findManifests`, not a silent gap either.
 */
object DependabotWatchRules {

    /** The manifests in [manifests] that no entry in [entries] watches. */
    fun watch(entries: List<DependabotEntry>, manifests: List<PackageManifest>): List<Violation> = manifests
        .filterNot { manifest -> entries.any { entry -> entry.watches(manifest) } }
        .map { manifest ->
            Violation(
                Rules.DEPENDABOT_WATCH,
                manifest.path,
                "is not watched by a '${manifest.ecosystem}' Dependabot entry for ${manifest.directory}",
            )
        }

    /**
     * The entries a `dependabot.yml` declares. Only the shape this repository
     * writes is read: a `package-ecosystem:` line opens an entry and the
     * `directory:` line that follows closes it. An entry in any other form — the
     * `directories:` list, say — contributes nothing, so its manifest is reported
     * unwatched by [watch] and the gap fails loudly rather than passing silently.
     */
    fun entries(config: String): List<DependabotEntry> {
        val entries = mutableListOf<DependabotEntry>()
        var open: String? = null
        config.lineSequence().forEach { line ->
            ECOSYSTEM.find(line)?.let { match ->
                open = match.groupValues[1]
                return@forEach
            }
            val ecosystem = open ?: return@forEach
            val directory = DIRECTORY.find(line)?.groupValues?.get(1) ?: return@forEach
            entries += DependabotEntry(ecosystem, directory)
            open = null
        }
        return entries
    }

    /** `/guide/` → `/guide`, `guide` → `/guide`, `/` → `/`. */
    private fun normaliseDirectory(directory: String): String {
        val trimmed = directory.trim().trim('"')
        val rooted = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return rooted.removeSuffix("/").ifEmpty { "/" }
    }

    private fun DependabotEntry.watches(manifest: PackageManifest): Boolean = ecosystem == manifest.ecosystem &&
        normaliseDirectory(directory) == normaliseDirectory(manifest.directory)

    private val ECOSYSTEM = Regex("""^\s*-\s*package-ecosystem:\s*["']?([\w-]+)["']?\s*$""")
    private val DIRECTORY = Regex("""^\s*directory:\s*["']?([^"'#\s]+)["']?\s*$""")
}
