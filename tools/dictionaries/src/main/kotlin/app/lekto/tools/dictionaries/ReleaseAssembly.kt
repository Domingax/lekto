package app.lekto.tools.dictionaries

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Stage two of the two-stage build (ADR-0046, issue #304): reads one pair's
 * content file and the **Reading language**'s shared form index and writes only
 * that pair's **Dictionary pack**, NOTICE and manifest. The provenance is the
 * edition the intermediate recorded, never a flag passed now. The drift gate
 * compares the pair's lemma count with its own previous published count, so a
 * newly added pair and its siblings are unaffected.
 */
internal fun runPair(options: CliOptions.Pair) {
    val content = readEditionContent(options.content)
    val index = readFormIndex(options.formIndex)
    require(index.readingLanguage == content.readingLanguage) {
        "the form index is for ${index.readingLanguage}, not ${content.readingLanguage}"
    }
    require(content.translationLanguage in index.translationLanguages) {
        "the form index for ${index.readingLanguage} is not built for ${content.translationLanguage}"
    }
    require(index.provenance == content.provenance) {
        "the form index and the content file record different editions"
    }
    val pack = PackBuilder(content, index).build()
    runGate(
        pack,
        LanguagePair(content.readingLanguage, content.translationLanguage),
        options.previousLemmaCount,
        options.collapseThreshold,
    )
    Files.createDirectories(options.outputDir)
    val stem = dictionaryStem(content.readingLanguage, content.translationLanguage)
    writePack(
        pack,
        options.outputDir.resolve("$stem.sqlite"),
        options.outputDir.resolve("NOTICE-$stem"),
        options.outputDir.resolve("manifest-$stem.json"),
    )
    println(packManifest(pack))
}

/**
 * The difficulty stage (ADR-0046, issue #304): keys one **Reading language**'s
 * frequency list by its shared form index and writes that language's **Difficulty
 * table**, NOTICE and manifest. The index's recorded edition is the provenance.
 */
internal fun runDifficulty(options: CliOptions.Difficulty) {
    val index = readFormIndex(options.formIndex)
    val content = buildDifficulty(options.frequency, index.forms, index.provenance, index.readingLanguage)
    Files.createDirectories(options.outputDir)
    val stem = difficultyStem(index.readingLanguage)
    writeDifficulty(
        content,
        options.outputDir.resolve("$stem.sqlite"),
        options.outputDir.resolve("NOTICE-$stem"),
        options.outputDir.resolve("manifest-$stem.json"),
    )
    println(difficultyManifest(content))
}

/** Writes a pack and its two release sidecars (attribution and provenance). */
internal fun writePack(pack: PackContent, sqlite: Path, notice: Path, manifest: Path) {
    SqlitePackWriter.write(pack, sqlite)
    Files.writeString(notice, Attribution.notice(pack.provenance))
    Files.writeString(manifest, packManifest(pack))
}

/** Writes a difficulty table and its two release sidecars (attribution and provenance). */
internal fun writeDifficulty(content: DifficultyContent, sqlite: Path, notice: Path, manifest: Path) {
    SqliteDifficultyWriter.write(content, sqlite)
    Files.writeString(notice, DifficultyAttribution.notice(content.provenance))
    Files.writeString(manifest, difficultyManifest(content))
}

/** Checks one artefact against the shared sanity gate and its own previous count, then fails its own run. */
internal fun runGate(pack: PackContent, pair: LanguagePair, previousLemmaCount: Int?, collapseThreshold: Double) {
    val problems = SanityGate.check(
        stats = pack.stats,
        readingLanguage = pair.reading,
        translationLanguage = pair.translation,
        previousLemmaCount = previousLemmaCount,
        collapseThreshold = collapseThreshold,
    )
    if (problems.isNotEmpty()) {
        error(
            "sanity gate failed for ${pair.reading}→${pair.translation}:\n" +
                problems.joinToString("\n") { "  - $it" },
        )
    }
}

internal fun packManifest(content: PackContent): String =
    manifestJson.encodeToString(PackManifest(PackFormat.VERSION, content.provenance, content.stats))

internal fun difficultyManifest(content: DifficultyContent): String = manifestJson.encodeToString(
    DifficultyManifest(
        formatVersion = DifficultyFormat.VERSION,
        provenance = content.provenance,
        calibration = content.calibration,
        stats = content.stats,
    ),
)

/**
 * Builds the reading language's **Difficulty table** (ADR-0029) from its frequency
 * list, keyed by the reading language's form index — the pack's lemmas where one
 * exists, the normalised surface form otherwise.
 */
internal fun buildDifficulty(
    input: Path,
    forms: List<PackForm>,
    provenance: PackProvenance,
    language: String,
): DifficultyContent = openInput(input).use { stream ->
    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
        DifficultyBuilder(forms, provenance, language).build(reader)
    }
}
