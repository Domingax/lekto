package app.lekto.architecture

/**
 * The repository, reduced to the two things the architecture rules inspect: the
 * Gradle modules and their declared project dependencies, and the Kotlin source
 * files and their packages and imports.
 *
 * The rules are pure functions over this model. Reading it from disk is
 * [RepositoryScanner]'s job, so a rule can be exercised against a hand-built
 * repository — including one that is deliberately broken — without touching the
 * working tree (ticket #9).
 */
data class Repository(val modules: List<Module>, val sources: List<KotlinSource>)

/** A Gradle module declared in `settings.gradle.kts`, e.g. `:core`. */
data class Module(val path: String, val dependencies: List<DeclaredDependency>)

/**
 * A `project(":x")` dependency a module's build file declares, with the scope it
 * was declared in: a test source set may reach for the shared testkit, a
 * production source set may not.
 */
data class DeclaredDependency(val path: String, val testScoped: Boolean)

/** One Kotlin source file, reduced to what the architecture rules inspect. */
data class KotlinSource(
    val path: String,
    val module: String,
    val sourceSet: String,
    val packageName: String,
    val imports: List<String>,
) {
    val fileName: String get() = path.substringAfterLast('/')

    /** True for `commonTest`, `jvmTest`, `desktopTest`, `androidHostTest`, `test`, … */
    val isTest: Boolean get() = sourceSet.endsWith("Test") || sourceSet == "test"

    val isProduction: Boolean get() = !isTest
}

/** A rule the repository breaks, naming the rule and the offending subject. */
data class Violation(val rule: String, val subject: String, val detail: String)
