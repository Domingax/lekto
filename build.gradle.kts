import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import dev.detekt.gradle.report.ReportMergeTask
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

// The six modules of Lekto. See docs/build.md and docs/adr/0007.
// core      — the domain (vault, records, merge, tokenisation, sync engine, parsers)
// testkit   — contract suites and in-memory fakes, shared by the other modules' tests
// integrations/webdav — the first sync driver, isolated from the domain
// app       — the Compose Multiplatform application (Android + desktop)
// tools/dictionaries — the offline dictionary-pack pipeline, built by its own CI job
// architecture — the architecture tests (ticket #9), test-only and never shipped
plugins {
    base
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.roborazzi) apply false
    // Applied, not `apply false`: the root project is Kover's merging module and
    // owns the aggregated report tasks. See the measurement section below.
    alias(libs.plugins.kover)
}

// --- Quality gates -------------------------------------------------------------
// Formatting (ktlint) and static analysis (detekt) run in every project and are
// wired into `check`, so a violation fails the build instead of scrolling past
// as a warning. The rule set is tuned for agent-written code — bounded
// complexity and function size, no dead code, no orphaned TODO — in
// config/detekt/detekt.yml; the code style lives in .editorconfig. ktlint is MIT
// and detekt Apache-2.0, so both are AGPL-compatible (ADR-0011). See
// docs/build.md.
//
// `allprojects`, not `subprojects`: the root `build.gradle.kts` and
// `settings.gradle.kts` are scripts too, and ktlint lints them.
//
// Pin the engine so the formatter does not drift under the plugin.
val ktlintEngineVersion: String = extensions.getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("ktlint")
    .get()
    .requiredVersion
val detektConfigFile = rootProject.file("config/detekt/detekt.yml")

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "dev.detekt")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    extensions.configure<KtlintExtension> {
        version.set(ktlintEngineVersion)
        // Emit Checkstyle XML alongside the human-readable report: SonarCloud
        // ingests ktlint findings through it (sonar.kotlin.ktlint.reportPaths in
        // sonar-project.properties). The plain-text report stays for the console.
        reporters {
            reporter(ReporterType.CHECKSTYLE)
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(detektConfigFile)
        parallel.set(true)
        // The ticket's contract, stated rather than inherited: a finding fails
        // the build, it is never just a warning.
        ignoreFailures.set(false)
        failOnSeverity.set(FailOnSeverity.Error)
    }

    // Measurement is a task of its own (ticket #8): no Kover verification rule
    // exists, so `koverVerify` must not ride along on `check`. The plugin still
    // instruments the test runs — that is the price of coverage — but writes a
    // report only when `koverXmlReport` is asked for.
    extensions.configure<KoverProjectExtension> {
        reports {
            total {
                verify {
                    onCheck.set(false)
                }
            }
        }
    }

    // detekt registers the aggregate `detekt` task — which `check` depends on —
    // plus one task per source set and one per Kotlin compilation. Only the
    // compilation tasks carry a classpath, and the Analysis-API rules that
    // matter for dead code (unused private functions, properties and variables,
    // unreachable code) are skipped without one. Hang the compilation tasks off
    // the aggregate so `check` runs them; the source-set tasks are redundant
    // once the compilation tasks cover their files.
    val detektTasks = tasks.withType<Detekt>()
    val compilationDetektTasks = detektTasks.matching { task ->
        task.name != "detekt" && !task.name.endsWith("SourceSet")
    }
    detektTasks.matching { it.name == "detekt" }.configureEach {
        dependsOn(compilationDetektTasks)
    }
}

// --- Sonar report paths --------------------------------------------------------
// SonarCloud's Kotlin importer reads `sonar.kotlin.detekt.reportPaths` and
// `sonar.kotlin.ktlint.reportPaths` as a comma-separated list of report *files*.
// It does not expand wildcards: it turns each entry into a File verbatim and warns
// "The report file(s) can not be found" when one does not exist (sonar-analyzer-
// commons ExternalReportProvider). Both plugins write one report per source set or
// Kotlin compilation, so a single glob cannot name them and listing every file
// would rot as source sets come and go. Merge each tool's reports into one
// Checkstyle XML at a stable path and name that in sonar-project.properties.
//
// Only the modules SonarCloud indexes are merged — the same three named in
// sonar.sources. testkit, architecture and tools/dictionaries are outside the
// Sonar project (they never ship or are standalone), so their findings must not be
// fed to it.
val sonarKotlinModules = listOf(project(":core"), project(":app"), project(":integrations:webdav"))

// detekt writes `<task>.xml` directly under the directory; ktlint nests its report
// one level down, per source set. Both are Checkstyle XML, so the detekt merger
// (which also de-duplicates detekt's overlapping compilation reports) reads either.
// The Kotlin-script report is excluded: `*.gradle.kts` is excluded from the Sonar
// sources, so its findings could never attach to an indexed file.
fun Project.sonarDetektReports(): Provider<FileTree> = layout.buildDirectory.dir("reports/detekt")
    .map { dir -> dir.asFileTree.matching { include("*.xml") } }

fun Project.sonarKtlintReports(): Provider<FileTree> = layout.buildDirectory.dir("reports/ktlint")
    .map { dir -> dir.asFileTree.matching { include("ktlint*SourceSetCheck/*.xml") } }

val sonarReportsDir = layout.buildDirectory.dir("reports/sonar")

val mergeDetektReports by tasks.registering(ReportMergeTask::class) {
    group = "verification"
    description = "Merge the per-compilation detekt reports into one Checkstyle XML for SonarCloud."
    input.from(sonarKotlinModules.map { it.sonarDetektReports() })
    output.set(sonarReportsDir.map { it.file("detekt.xml") })
    dependsOn(sonarKotlinModules.flatMap { module -> module.tasks.withType<Detekt>() })
}

val mergeKtlintReports by tasks.registering(ReportMergeTask::class) {
    group = "verification"
    description = "Merge the per-source-set ktlint reports into one Checkstyle XML for SonarCloud."
    input.from(sonarKotlinModules.map { it.sonarKtlintReports() })
    output.set(sonarReportsDir.map { it.file("ktlint.xml") })
    dependsOn(
        sonarKotlinModules.flatMap { module ->
            module.tasks.matching { it.name.startsWith("ktlint") && it.name.endsWith("SourceSetCheck") }
        },
    )
}

// The reports above are the contract with SonarCloud, so assert it the way the
// scanner will read it: every path in the two properties is a concrete file that
// exists and parses as Checkstyle XML. A wildcard here is the exact defect this
// guards against, so it is reported by name. Runs from `check`, so a misconfigured
// sonar-project.properties fails the fast lane instead of the sonar lane.
val verifySonarReportPaths by tasks.registering {
    group = "verification"
    description = "Fail when sonar-project.properties names a SonarCloud report it cannot read."
    dependsOn(mergeDetektReports, mergeKtlintReports)
    val propertiesFile = rootProject.file("sonar-project.properties")
    val projectBaseDir = rootProject.layout.projectDirectory.asFile
    inputs.file(propertiesFile)
    doLast {
        val properties = java.util.Properties().apply {
            propertiesFile.inputStream().use { load(it) }
        }
        val problems = mutableListOf<String>()
        for (key in listOf("sonar.kotlin.detekt.reportPaths", "sonar.kotlin.ktlint.reportPaths")) {
            val value = properties.getProperty(key) ?: continue
            for (entry in value.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
                val report = projectBaseDir.resolve(entry)
                when {
                    entry.any { it in "*?[" } ->
                        problems += "$key: '$entry' is a wildcard; SonarCloud does not expand it"

                    !report.isFile ->
                        problems += "$key: '$entry' does not exist"

                    else -> {
                        val root = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder().parse(report).documentElement
                        if (root.tagName != "checkstyle") {
                            problems += "$key: '$entry' is not a Checkstyle report (root is <${root.tagName}>)"
                        }
                    }
                }
            }
        }
        check(problems.isEmpty()) {
            "sonar-project.properties names reports SonarCloud cannot import:\n" +
                problems.joinToString("\n") { "  - $it" }
        }
    }
}

// The guard rides on `check` so a report-path regression fails where a developer
// sees it, not only in the sonar lane.
tasks.named("check") {
    dependsOn(verifySonarReportPaths)
}

// --- Coverage (ticket #8) ------------------------------------------------------
// Kover measures line coverage for the JVM/desktop test runs and writes a single
// JaCoCo-compatible XML report that SonarCloud ingests as the project's coverage
// (ADR-0012). The root project is the merging module, so `:koverXmlReport`
// produces one report covering every module a shipped line lives in, and
// triggers those modules' tests first. `testkit` and `tools/dictionaries` are
// deliberately not aggregated: the first is test scaffolding and never ships,
// the second is standalone and off the application CI path. See
// docs/build.md#coverage-and-the-quality-gate.
dependencies {
    kover(project(":core"))
    kover(project(":integrations:webdav"))
    kover(project(":app"))
}

kover {
    reports {
        total {
            xml {
                // Name the merged report. Its default path,
                // build/reports/kover/report.xml, is the one
                // sonar-project.properties points SonarCloud at; it is written
                // only when `koverXmlReport` is invoked.
                title.set("Lekto")
            }
        }
    }
}

// --- Dependency licences -------------------------------------------------------
// ADR-0011 requires every dependency, test-scope included, to be AGPL-compatible.
// This gate resolves each module's runtime classpaths, reads the licences the
// dependencies declare in their POMs, and fails the build on a licence that is not
// in config/dependency-licences.txt. It needs dependency metadata at execution
// time, which the configuration cache cannot store, so it is a task of its own
// (run without the cache) rather than part of `check`; CI runs it as the
// `licences` job. See docs/build.md.
//
// The policy is data, not code: config/dependency-licences.txt holds the allowed
// ids and the hand-reviewed overrides for components whose POMs declare none.
//
// Gradle reads a module's own POM, not Maven's effective one, so a dependency
// that declares its licence in a parent POM arrives with none. A licence-less
// dependency on a production classpath therefore fails (it ships, so it must be
// reviewed), while one on a test classpath warns: it is never conveyed, and the
// override list stays a record of decisions rather than a gate on every bump.

data class LicencePolicy(val allowed: Set<String>, val testAllowed: Set<String>, val overrides: Map<String, String>)

fun readLicencePolicy(file: File): LicencePolicy {
    val allowed = mutableSetOf<String>()
    val testAllowed = mutableSetOf<String>()
    val overrides = mutableMapOf<String, String>()
    file.readLines().forEach { raw ->
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) return@forEach
        val parts = line.split(Regex("\\s+"))
        when (parts[0]) {
            "licence" -> allowed += parts[1]
            "test-licence" -> testAllowed += parts[1]
            "override" -> overrides[parts[1]] = parts[2]
            else -> error("${file.name}: unknown instruction '${parts[0]}'")
        }
    }
    return LicencePolicy(allowed, testAllowed, overrides)
}

fun readPomLicences(pom: File): List<Pair<String, String>> =
    Regex("<license>(.*?)</license>", RegexOption.DOT_MATCHES_ALL).findAll(pom.readText()).map { match ->
        val block = match.groupValues[1]
        fun inner(tag: String): String =
            Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1)?.trim().orEmpty()
        inner("name") to inner("url")
    }.toList()

fun normaliseLicence(name: String, url: String): String? {
    val text = "$name $url".lowercase()
    return when {
        "apache" in text -> "Apache-2.0"
        "bouncy castle" in text -> "MIT"
        "agpl" in text -> "AGPL-3.0"
        "eclipse public license" in text && "2.0" in text -> "EPL-2.0"
        "eclipse public license" in text -> "EPL-1.0"
        "lgpl" in text && "2.1" in text -> "LGPL-2.1-or-later"
        "lgpl" in text -> "LGPL-3.0"
        "gpl" in text && "2" in text -> "GPL-2.0"
        "gpl" in text -> "GPL-3.0"
        "bsd" in text && ("3" in text || "three" in text) -> "BSD-3-Clause"
        "bsd" in text -> "BSD-2-Clause"
        "zlib" in text -> "Zlib"
        Regex("\\bmit\\b").containsMatchIn(text) -> "MIT"
        else -> null
    }
}

val licencePolicyFile = file("config/dependency-licences.txt")
val licenceReportFile = layout.buildDirectory.file("reports/dependency-licences.txt")

val checkDependencyLicences by tasks.registering {
    group = "verification"
    description = "Fail the build when a dependency's licence is not AGPL-compatible (ADR-0011)."
    notCompatibleWithConfigurationCache("reads resolved dependency metadata at execution time")
    inputs.file(licencePolicyFile)
    outputs.file(licenceReportFile)
    // The dependency graph is not a declared input (it cannot be captured without
    // resolving at configuration time), so the gate always evaluates rather than
    // risk reporting a stale `UP-TO-DATE` after a dependency change.
    outputs.upToDateWhen { false }
    doLast {
        val policy = readLicencePolicy(licencePolicyFile)
        val declared = mutableMapOf<String, List<Pair<String, String>>>()
        val origins = mutableMapOf<String, MutableSet<String>>()
        val production = mutableSetOf<String>()

        for (target in project.allprojects.sortedBy { it.path }) {
            // `tools/` is standalone and never ships with the app, so its
            // dependencies are outside the application licence gate.
            if (target.path.startsWith(":tools")) continue
            val configs = target.configurations.filter {
                it.isCanBeResolved && it.name.endsWith("RuntimeClasspath") && !it.name.startsWith("composeHotReload")
            }
            for (config in configs) {
                val isTest = "test" in config.name.lowercase()
                val components = config.incoming.resolutionResult.allComponents
                    .filterNot { it.id is ProjectComponentIdentifier }
                    .distinctBy { it.id.displayName }
                if (components.isEmpty()) continue
                for (component in components) {
                    origins.getOrPut(component.id.displayName) { mutableSetOf() } += "${target.path}:${config.name}"
                    if (!isTest) production += component.id.displayName
                }

                val unknown = components.filter { it.id.displayName !in declared }
                if (unknown.isEmpty()) continue
                val query = target.dependencies.createArtifactResolutionQuery()
                query.forComponents(unknown.map { it.id })
                    .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                for (component in query.execute().resolvedComponents) {
                    val pom = component.getArtifacts(MavenPomArtifact::class.java)
                        .filterIsInstance<ResolvedArtifactResult>().firstOrNull()?.file
                    val licences = pom?.takeIf { it.isFile }?.let(::readPomLicences).orEmpty()
                    declared[component.id.displayName] = licences
                }
                for (component in unknown) declared.getOrPut(component.id.displayName) { emptyList() }
            }
        }

        val lines = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        for ((coordinate, licences) in declared.entries.sortedBy { it.key }) {
            val onProduction = coordinate in production
            val ids = licences.mapNotNull { (name, url) -> normaliseLicence(name, url) }
            val override = policy.overrides[coordinate.substringBeforeLast(':')]
            val effective = ids.ifEmpty { listOfNotNull(override) }
            val allowlist = if (onProduction) policy.allowed else policy.allowed + policy.testAllowed
            val names = licences.map { it.first }.ifEmpty { listOf(override ?: "<none declared>") }
            val verdict = when {
                effective.any { it in allowlist } -> "ok  "
                licences.isNotEmpty() || override != null -> "FAIL"
                onProduction -> "FAIL"
                else -> "warn"
            }
            val origin = origins[coordinate].orEmpty().joinToString()
            val classpath = if (onProduction) "production" else "test"
            lines += "$verdict  $coordinate  ${names.joinToString()}  [$classpath]  $origin"
            when (verdict) {
                "FAIL" -> failures += "$coordinate (${names.joinToString()})"
                "warn" -> warnings += coordinate
            }
        }

        val report = licenceReportFile.get().asFile
        report.parentFile.mkdirs()
        report.writeText(lines.joinToString("\n", postfix = "\n"))
        lines.forEach { logger.lifecycle("dependency-licences: $it") }
        warnings.forEach { coordinate ->
            logger.warn(
                "dependency-licences: $coordinate declares no licence of its own and is test-only; add an override",
            )
        }

        if (failures.isNotEmpty()) {
            throw GradleException(
                "Dependencies with a licence outside the AGPL-compatible policy (ADR-0011):\n" +
                    failures.joinToString("\n") { "  - $it" } +
                    "\nReview config/dependency-licences.txt or replace the dependency. " +
                    "Full verdicts: ${licenceReportFile.get().asFile}",
            )
        }
    }
}

// --- Git hooks -----------------------------------------------------------------
// The commit-msg hook lives in .githooks/ so it is reviewed and versioned like
// any other file. Git only runs it once core.hooksPath points there; `check`
// installs it, so a fresh clone is guarded from its first build. See AGENTS.md,
// "Commits". A source tarball with no git metadata skips the task entirely.
if (rootDir.resolve(".git").exists()) {
    val installGitHooks by tasks.registering(Exec::class) {
        group = "setup"
        description = "Point git at the committed .githooks so non-conforming commits are rejected."
        workingDir(rootDir)
        commandLine("git", "config", "--local", "core.hooksPath", ".githooks")
    }

    tasks.named("check") {
        dependsOn(installGitHooks)
    }
}
