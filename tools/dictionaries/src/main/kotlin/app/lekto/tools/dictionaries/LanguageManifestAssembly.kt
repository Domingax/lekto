package app.lekto.tools.dictionaries

import java.nio.file.Files
import java.nio.file.Path

/**
 * The 2,000-lemma floor a published pair must clear (issue #301, D3; ADR-0047).
 * A pair below it stays out of the **Language manifest** rather than shipping a
 * pack too thin to read with. A published pair that *loses* coverage is a
 * different rule: its own job's drift gate fails it (issue #304).
 */
internal const val MINIMUM_PUBLISHED_LEMMAS: Int = 2_000

/** The one movable file the app follows at `releases/latest/download/`. */
internal const val RELEASE_MANIFEST_NAME: String = "manifest.json"

/**
 * Assembles the run's **Language manifest** from what its jobs produced (issue
 * #306, ADR-0047). The decomposed pipeline hands the assembly one directory
 * holding every successful pair's and table's per-artefact manifest beside its
 * compressed asset; this reads those files, drops a pack below [minimumLemmas],
 * and completes each survivor with the asset's address, digest and size.
 *
 * It is deliberately not built from `languages.json`: a pair that failed, drifted
 * or was never built is simply absent, which is the same rule that drops a thin
 * pair. The caller compresses the assets first, so the manifest names the exact
 * bytes it measured.
 */
internal fun assembleLanguageManifest(
    assetDir: Path,
    assetBaseUrl: String,
    minimumLemmas: Int = MINIMUM_PUBLISHED_LEMMAS,
): LanguageManifest {
    val pairs = producedPacks(assetDir)
        .filter { (_, manifest) -> manifest.stats.lemmaCount >= minimumLemmas }
        .map { (pair, _) -> pair }
    val difficulties = producedDifficulties(assetDir)
    return publishLanguageManifest(buildLanguageManifest(pairs, difficulties), assetDir, assetBaseUrl)
}

/** Writes the run's movable manifest into its own asset directory, after the bytes it names. */
internal fun runAssemble(options: CliOptions.Assemble) {
    val manifest = assembleLanguageManifest(options.assetDir, options.assetBaseUrl)
    val json = manifest.canonicalJson()
    Files.writeString(options.assetDir.resolve(RELEASE_MANIFEST_NAME), json)
    println(json)
}

/** The pairs a run published, with the per-artefact manifest that records their counts. */
private fun producedPacks(assetDir: Path): List<Pair<LanguagePair, PackManifest>> =
    producedManifests(assetDir, PACK_MANIFEST_PREFIX).map { file ->
        val pair = dictionaryPairOf(stemOf(file))
        pair to manifestJson.decodeFromString(PackManifest.serializer(), Files.readString(file))
    }.sortedWith(compareBy({ (pair, _) -> pair.reading }, { (pair, _) -> pair.translation }))

/** The reading languages whose Difficulty table the run published. */
private fun producedDifficulties(assetDir: Path): List<String> =
    producedManifests(assetDir, DIFFICULTY_MANIFEST_PREFIX).map { file ->
        stemOf(file).removePrefix("$DIFFICULTY_PREFIX-")
    }.sorted()

/**
 * `manifest-lekto-dictionary-<reading>-<translation>.json` → the pair. The asset
 * naming convention is the contract, so a language code containing a hyphen is
 * refused rather than silently split wrong.
 */
private fun dictionaryPairOf(stem: String): LanguagePair {
    val parts = stem.removePrefix("$DICTIONARY_PREFIX-").split('-')
    require(parts.size == PAIR_PARTS && parts.all(String::isNotBlank)) {
        "cannot read a reading and translation language from '$stem'"
    }
    return LanguagePair(parts.first(), parts.last())
}

/** `manifest-lekto-<kind>-<rest>.json` → `lekto-<kind>-<rest>`. */
private fun stemOf(file: Path): String = file.fileName.toString().removePrefix("manifest-").removeSuffix(".json")

private fun producedManifests(assetDir: Path, prefix: String): List<Path> {
    require(Files.isDirectory(assetDir)) { "the asset directory $assetDir does not exist" }
    return Files.list(assetDir).use { stream ->
        stream.filter { file ->
            val name = file.fileName.toString()
            name.startsWith(prefix) && name.endsWith(".json") && Files.isRegularFile(file)
        }.toList()
    }
}

private const val DICTIONARY_PREFIX = "lekto-dictionary"
private const val DIFFICULTY_PREFIX = "lekto-difficulty"
private const val PACK_MANIFEST_PREFIX = "manifest-$DICTIONARY_PREFIX-"
private const val DIFFICULTY_MANIFEST_PREFIX = "manifest-$DIFFICULTY_PREFIX-"
private const val PAIR_PARTS = 2
