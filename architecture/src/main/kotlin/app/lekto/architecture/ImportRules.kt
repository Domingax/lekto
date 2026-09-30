package app.lekto.architecture

/**
 * What a source file may name: the domain never reaches for the application, an
 * integration or Android; an integration is invisible outside the composition
 * root; and the testkit never reaches production code (AGENTS.md, "Module
 * boundaries you must not cross"; ticket #9).
 *
 * These are the same boundaries as [LektoArchitecture]'s module policies, but
 * checked at the import, so a leak is caught even when the module dependency
 * graph would still resolve.
 */
object ImportRules {

    fun check(repository: Repository): List<Violation> = buildList {
        addAll(checkProductionImports(repository))
        addAll(checkTestkitIsTestOnly(repository))
    }

    private fun checkProductionImports(repository: Repository): List<Violation> = repository.sources
        .filter { source -> source.isProduction }
        .flatMap { source ->
            val policy = LektoArchitecture.policies.firstOrNull { it.path == source.module }
                ?: return@flatMap emptyList()
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

    private fun allowedLektoRoots(policy: ModulePolicy): Set<String> = buildSet {
        add(policy.packageRoot)
        when (policy.kind) {
            ModuleKind.DOMAIN -> Unit

            ModuleKind.INTEGRATION -> {
                add("app.lekto.core")
                add("app.lekto.integrations")
            }

            ModuleKind.TEST_SUPPORT -> add("app.lekto.core")

            ModuleKind.APPLICATION -> add("app.lekto")

            ModuleKind.TOOL -> Unit
        }
    }

    private fun isAndroid(import: String): Boolean = import.startsWith("android.") || import.startsWith("androidx.")

    private const val LEKTO_PACKAGE = "app.lekto"
    private const val TESTKIT_PACKAGE = "app.lekto.testkit"
    private const val TESTKIT_MODULE = ":testkit"
}
