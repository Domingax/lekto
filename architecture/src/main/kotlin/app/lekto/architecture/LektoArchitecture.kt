package app.lekto.architecture

/** What a module is for, which decides the packages its sources may name. */
enum class ModuleKind { DOMAIN, INTEGRATION, TEST_SUPPORT, APPLICATION, TOOL }

/**
 * The architecture a module must satisfy: where its packages live, and which
 * sibling modules it may depend on, in production and in test.
 */
data class ModulePolicy(
    val path: String,
    val kind: ModuleKind,
    val packageRoot: String,
    val mainDependencies: Set<String>,
    val testDependencies: Set<String>,
)

/** The stable names a [Violation] carries, so a test can assert which rule broke. */
object Rules {
    const val MODULE_POLICY = "module-policy"
    const val MODULE_DEPENDENCY = "module-dependency"
    const val IMPORT_PURITY = "import-purity"
    const val TESTKIT_LEAK = "testkit-leak"
    const val PACKAGE_PLACEMENT = "package-placement"
    const val PACKAGE_OWNERSHIP = "package-ownership"
    const val SOURCE_PLACEMENT = "source-placement"
    const val TEST_PLACEMENT = "test-placement"
}

/**
 * Lekto's architecture, expressed as checks over a [Repository] (ticket #9;
 * AGENTS.md, "Module boundaries you must not cross").
 *
 * The policies are the single source of truth: a module not named here, a
 * module dependency outside a policy — each is a [Violation]. Adding a module
 * or a dependency is therefore a deliberate edit to this list, not an accident
 * the build lets through. The source-level rules live in [PlacementRules] and
 * [ImportRules].
 *
 * The graph is the one in AGENTS.md: the application is the only composition
 * root and depends on `core` and the integrations; the integrations depend on
 * `core` alone; `core` depends on no other module; `tools` and the test-only
 * modules are leaves. Nothing depends on the application.
 */
object LektoArchitecture {

    /** Every module the repository is allowed to have, and what it may reach. */
    val policies: List<ModulePolicy> = listOf(
        ModulePolicy(
            path = ":core",
            kind = ModuleKind.DOMAIN,
            packageRoot = "app.lekto.core",
            mainDependencies = emptySet(),
            testDependencies = setOf(":testkit"),
        ),
        ModulePolicy(
            path = ":testkit",
            kind = ModuleKind.TEST_SUPPORT,
            packageRoot = "app.lekto.testkit",
            mainDependencies = setOf(":core"),
            testDependencies = emptySet(),
        ),
        ModulePolicy(
            path = ":integrations:webdav",
            kind = ModuleKind.INTEGRATION,
            packageRoot = "app.lekto.integrations.webdav",
            mainDependencies = setOf(":core"),
            testDependencies = setOf(":testkit"),
        ),
        ModulePolicy(
            path = ":app",
            kind = ModuleKind.APPLICATION,
            packageRoot = "app.lekto",
            mainDependencies = setOf(":core", ":integrations:webdav"),
            testDependencies = setOf(":testkit"),
        ),
        ModulePolicy(
            path = ":tools:dictionaries",
            kind = ModuleKind.TOOL,
            packageRoot = "app.lekto.tools.dictionaries",
            mainDependencies = emptySet(),
            testDependencies = emptySet(),
        ),
        ModulePolicy(
            path = ":architecture",
            kind = ModuleKind.TEST_SUPPORT,
            packageRoot = "app.lekto.architecture",
            mainDependencies = emptySet(),
            testDependencies = emptySet(),
        ),
    )

    /** Runs every rule over [repository]; an empty result means it conforms. */
    fun check(repository: Repository): List<Violation> = buildList {
        addAll(checkModulePolicies(repository))
        addAll(checkModuleDependencies(repository))
        addAll(PlacementRules.check(repository))
        addAll(ImportRules.check(repository))
    }

    private fun checkModulePolicies(repository: Repository): List<Violation> {
        val declared = repository.modules.map { module -> module.path }.toSet()
        val known = policies.map { policy -> policy.path }.toSet()
        return buildList {
            repository.modules
                .filter { module -> module.path !in known }
                .forEach { module ->
                    add(Violation(Rules.MODULE_POLICY, module.path, "is declared but has no architecture policy"))
                }
            policies
                .filter { policy -> policy.path !in declared }
                .forEach { policy ->
                    add(
                        Violation(
                            Rules.MODULE_POLICY,
                            policy.path,
                            "has a policy but the repository has no such module",
                        ),
                    )
                }
        }
    }

    private fun checkModuleDependencies(repository: Repository): List<Violation> =
        repository.modules.flatMap { module ->
            val policy = policy(module.path) ?: return@flatMap emptyList()
            module.dependencies.mapNotNull { dependency -> dependencyViolation(policy, dependency) }
        }

    private fun dependencyViolation(policy: ModulePolicy, dependency: DeclaredDependency): Violation? {
        val allowed = if (dependency.testScoped) policy.testDependencies else policy.mainDependencies
        if (dependency.path in allowed) return null
        val scope = if (dependency.testScoped) "test" else "production"
        return Violation(
            Rules.MODULE_DEPENDENCY,
            policy.path,
            "$scope dependency on ${dependency.path} is not allowed",
        )
    }

    /** The policy for [module], or null if the module is not part of the architecture. */
    fun policy(module: String): ModulePolicy? = policies.firstOrNull { it.path == module }
}
