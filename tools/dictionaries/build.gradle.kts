import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The offline dictionary-pack pipeline (ticket #17): a standalone JVM CLI that
// turns the raw Wiktextract extract into a trimmed, read-only SQLite pack and
// publishes it as a release artifact. It is deliberately off the application CI
// path (`docs/build.md#dictionary-pipeline`) and depends on no Lekto module, so
// the architecture policy keeps it a leaf.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Keep javac's target in step with Kotlin's: a plain JVM module compiles Java
// too, and the plugin fails the build when the two disagree.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

application {
    mainClass.set("app.lekto.tools.dictionaries.MainKt")
    applicationName = "dictionaries"
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    // Parses one Wiktextract JSON object per line (Apache-2.0).
    implementation(libs.kotlinx.serialization.json)
    // Builds the pre-built, read-only SQLite pack ADR-0017 opens by path
    // (Apache-2.0).
    implementation(libs.sqlite.jdbc)
    testImplementation(kotlin("test"))
}
