package app.lekto.architecture

import java.io.File

/**
 * Reads a working tree into the pure [Repository] the rules inspect.
 *
 * The scanner is the only part that touches disk: it finds the repository root,
 * reads the two Gradle files that declare the module graph, reduces every Kotlin
 * file to its package and imports, and lists the dependency manifests the
 * repository ships. Everything a rule needs is then a value, so a rule is
 * testable without a working tree (ticket #9).
 */
object RepositoryScanner {

    /** Walks up from [start] to the directory that holds `settings.gradle.kts`. */
    fun repositoryRoot(start: File = File(System.getProperty("user.dir"))): File {
        var directory: File? = start.absoluteFile
        while (directory != null) {
            if (File(directory, SETTINGS_FILE).isFile) return directory
            directory = directory.parentFile
        }
        error("No $SETTINGS_FILE found above ${start.absolutePath}")
    }

    fun scan(root: File): Repository {
        val modules = GradleBuildFile.includedModules(File(root, SETTINGS_FILE).readText())
            .map { path -> module(root, path) }
        return Repository(modules, modules.flatMap { module -> scanSources(root, module.path) })
    }

    private fun module(root: File, path: String): Module {
        val buildFile = File(root, "${GradleBuildFile.moduleDirectory(path)}/build.gradle.kts")
        val dependencies = if (buildFile.isFile) {
            GradleBuildFile.declaredDependencies(buildFile.readText())
        } else {
            emptyList()
        }
        return Module(path, dependencies)
    }

    private fun scanSources(root: File, module: String): List<KotlinSource> {
        val src = File(root, "${GradleBuildFile.moduleDirectory(module)}/src")
        if (!src.isDirectory) return emptyList()
        return src.walkTopDown()
            .filter { file -> file.isFile && file.extension == KOTLIN_EXTENSION }
            .map { file -> source(root, module, file) }
            .sortedBy { it.path }
            .toList()
    }

    /**
     * The dependency manifests the repository ships, each paired with the
     * ecosystem Dependabot reads it as and the directory an entry must name
     * ([PackageManifest]). Only the ecosystems this repository uses are
     * discovered — a Gradle build at the root, every npm project outside
     * `node_modules`, and the Actions workflows — so [DependabotWatch] fails when
     * one of them has no entry in `.github/dependabot.yml` (issue #153).
     */
    fun findManifests(root: File): List<PackageManifest> = buildList {
        if (File(root, SETTINGS_FILE).isFile) {
            add(PackageManifest(GRADLE_ECOSYSTEM, ROOT_DIRECTORY, SETTINGS_FILE))
        }
        addAll(npmManifests(root))
        if (File(root, WORKFLOWS_DIRECTORY).isDirectory) {
            add(PackageManifest(ACTIONS_ECOSYSTEM, ROOT_DIRECTORY, WORKFLOWS_DIRECTORY))
        }
    }

    private fun npmManifests(root: File): List<PackageManifest> = root.walkTopDown()
        .onEnter { directory -> directory.name !in SKIPPED_DIRECTORIES }
        .filter { file -> file.isFile && file.name == NPM_MANIFEST }
        .map { file ->
            val directory = file.parentFile.relativeTo(root).invariantSeparatorsPath
            PackageManifest(NPM_ECOSYSTEM, "/$directory", file.relativeTo(root).invariantSeparatorsPath)
        }
        .sortedBy { manifest -> manifest.path }
        .toList()

    private fun source(root: File, module: String, file: File): KotlinSource {
        val path = file.relativeTo(root).invariantSeparatorsPath
        val text = file.readText()
        return KotlinSource(
            path = path,
            module = module,
            sourceSet = sourceSetOf(path),
            packageName = PACKAGE_PATTERN.find(text)?.groupValues?.get(1).orEmpty(),
            imports = IMPORT_PATTERN.findAll(text).map { match -> match.groupValues[1] }.toList(),
        )
    }

    /** `core/src/commonMain/kotlin/…` → `commonMain`; empty when misplaced. */
    fun sourceSetOf(path: String): String = path.substringAfter("/src/", "").substringBefore('/')

    private const val SETTINGS_FILE = "settings.gradle.kts"
    private const val KOTLIN_EXTENSION = "kt"
    private const val NPM_MANIFEST = "package.json"
    private const val WORKFLOWS_DIRECTORY = ".github/workflows"
    private const val GRADLE_ECOSYSTEM = "gradle"
    private const val NPM_ECOSYSTEM = "npm"
    private const val ACTIONS_ECOSYSTEM = "github-actions"
    private const val ROOT_DIRECTORY = "/"

    /** Directories no manifest is discovered under: dependency and build output. */
    private val SKIPPED_DIRECTORIES = setOf("node_modules", ".git", ".gradle", ".kotlin", ".idea", "build")
    private val PACKAGE_PATTERN = Regex("""(?m)^\s*package\s+([\w.]+)""")
    private val IMPORT_PATTERN = Regex("""(?m)^\s*import\s+([\w.]+)""")
}
