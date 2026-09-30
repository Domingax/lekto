package app.lekto.architecture

/**
 * What a **production** source file may name: the domain never reaches for the
 * application, an integration or Android; an integration may reach `core` but
 * not another integration (only the composition root wires them); and the
 * testkit never reaches production code (AGENTS.md, "Module boundaries you must
 * not cross"; ticket #9).
 *
 * Test sources are governed by the module policies instead: `core`'s tests may
 * depend on `testkit`, and may not depend on `app`, because the build file says
 * so. These rules are the same boundaries checked at the import, so a leak is
 * caught even where the module dependency graph would still resolve.
 */
object ImportRules {

    fun check(repository: Repository): List<Violation> = buildList {
        addAll(checkProductionImports(repository))
        addAll(checkTestkitIsTestOnly(repository))
    }

    private fun checkProductionImports(repository: Repository): List<Violation> = repository.sources
        .filter { source -> source.isProduction }
        .flatMap { source ->
            val policy = LektoArchitecture.policy(source.module) ?: return@flatMap emptyList()
            source.imports.mapNotNull { import -> purityViolation(policy, source, import) }
        }

    private fun purityViolation(policy: ModulePolicy, source: KotlinSource, import: String): Violation? = when {
        isAndroid(import) && policy.kind == ModuleKind.DOMAIN ->
            Violation(Rules.IMPORT_PURITY, source.path, "the domain must not depend on Android ('$import')")

        import.startsWith(LEKTO_PACKAGE) && !withinAllowedPackages(policy, import) ->
            Violation(Rules.IMPORT_PURITY, source.path, "'$import' is outside the packages ${policy.path} may import")

        else -> null
    }

    private fun checkTestkitIsTestOnly(repository: Repository): List<Violation> = repository.sources
        .filter { source -> source.isProduction && source.module != TESTKIT_MODULE }
        .flatMap { source ->
            source.imports
                .filter { import -> import == TESTKIT_PACKAGE || import.startsWith("$TESTKIT_PACKAGE.") }
                .map { import ->
                    Violation(
                        Rules.TESTKIT_LEAK,
                        source.path,
                        "$TESTKIT_PACKAGE is test scaffolding and must never reach production ('$import')",
                    )
                }
        }

    private fun withinAllowedPackages(policy: ModulePolicy, import: String): Boolean =
        allowedLektoRoots(policy).any { root -> import == root || import.startsWith("$root.") }

    /**
     * The module's own package root, plus the package root of every module it
     * may depend on. Deriving the reachable packages from the module policies
     * keeps this in step with them: `testkit` may reach `core`, and `architecture`
     * — which depends on nothing — may reach nothing but itself.
     */
    private fun allowedLektoRoots(policy: ModulePolicy): Set<String> = buildSet {
        add(policy.packageRoot)
        policy.mainDependencies.mapNotNullTo(this) { module -> LektoArchitecture.policy(module)?.packageRoot }
    }

    private fun isAndroid(import: String): Boolean = import.startsWith("android.") || import.startsWith("androidx.")

    private const val LEKTO_PACKAGE = "app.lekto"

    private val TESTKIT_MODULE: String = Modules.TESTKIT
    private val TESTKIT_PACKAGE: String = requireNotNull(LektoArchitecture.policy(Modules.TESTKIT)).packageRoot
}
