import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// The five modules of Lekto. See docs/build.md and docs/adr/0007.
// core      — the domain (vault, records, merge, tokenisation, sync engine, parsers)
// testkit   — contract suites and in-memory fakes, shared by the other modules' tests
// integrations/webdav — the first sync driver, isolated from the domain
// app       — the Compose Multiplatform application (Android + desktop)
// tools/dictionaries — the offline dictionary-pack pipeline, built by its own CI job
plugins {
    base
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
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

    extensions.configure<KtlintExtension> {
        version.set(ktlintEngineVersion)
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
