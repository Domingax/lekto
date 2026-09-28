// The five modules of Lekto. See docs/build.md and docs/adr/0007.
// core      — the domain (vault, records, merge, tokenisation, sync engine, parsers)
// testkit   — contract suites and in-memory fakes, shared by the other modules' tests
// integrations/webdav — the first sync driver, isolated from the domain
// app       — the Compose Multiplatform application (Android + desktop)
// tools/dictionaries — the offline dictionary-pack pipeline, built by its own CI job
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
