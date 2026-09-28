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
