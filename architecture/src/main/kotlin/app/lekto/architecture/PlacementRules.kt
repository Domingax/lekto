package app.lekto.architecture

/**
 * Where a Kotlin file lives: under `<module>/src/<sourceSet>/kotlin/`, in a
 * package that matches its directory and sits under the module's package root,
 * with test classes named `*Test` and kept in a test source set.
 *
 * Placement is a naming convention the compiler cannot see, so it is asserted
 * here rather than left to review (ticket #9).
 */
object PlacementRules {

    fun check(repository: Repository): List<Violation> = buildList {
        addAll(checkSourcePlacement(repository))
        addAll(checkPackagePlacement(repository))
        addAll(checkPackageOwnership(repository))
        addAll(checkTestPlacement(repository))
    }

    private fun checkSourcePlacement(repository: Repository): List<Violation> = repository.sources
        .filterNot { source -> SOURCE_LAYOUT.matches(source.path) }
        .map { source ->
            Violation(Rules.SOURCE_PLACEMENT, source.path, "must live at <module>/src/<sourceSet>/kotlin/…")
        }

    private fun checkPackagePlacement(repository: Repository): List<Violation> = repository.sources
        .filter { source -> SOURCE_LAYOUT.matches(source.path) }
        .mapNotNull { source ->
            val expected = packageFromPath(source.path)
            if (source.packageName == expected) return@mapNotNull null
            Violation(
                Rules.PACKAGE_PLACEMENT,
                source.path,
                "declares package '${source.packageName}' but its directory means '$expected'",
            )
        }

    private fun checkPackageOwnership(repository: Repository): List<Violation> = repository.sources.flatMap { source ->
        val policy = LektoArchitecture.policies.firstOrNull { it.path == source.module }
            ?: return@flatMap emptyList()
        val root = policy.packageRoot
        if (source.packageName == root || source.packageName.startsWith("$root.")) return@flatMap emptyList()
        listOf(
            Violation(
                Rules.PACKAGE_OWNERSHIP,
                source.path,
                "package '${source.packageName}' is outside the module's '$root'",
            ),
        )
    }

    private fun checkTestPlacement(repository: Repository): List<Violation> = repository.sources.mapNotNull { source ->
        val namedTest = source.fileName.endsWith("Test.kt")
        when {
            source.isTest && !namedTest ->
                Violation(Rules.TEST_PLACEMENT, source.path, "a test source set holds only *Test.kt files")

            source.isProduction && namedTest ->
                Violation(Rules.TEST_PLACEMENT, source.path, "a *Test.kt file belongs in a test source set")

            else -> null
        }
    }

    /** The package a file's directory under `kotlin/` implies, e.g. `app.lekto.core`. */
    fun packageFromPath(path: String): String = path.substringAfter("/kotlin/", "")
        .substringBeforeLast('/', "")
        .replace('/', '.')

    private val SOURCE_LAYOUT = Regex(""".*/src/[^/]+/kotlin/.+\.kt$""")
}
