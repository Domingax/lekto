import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The architecture suite (ticket #9): the module boundaries and naming
// conventions expressed as tests, so an architectural violation fails `check`
// instead of waiting for a human review. The rules read the repository's
// sources and build files from disk rather than depending on the modules they
// police, so the module stays a leaf: it declares no project dependency and is
// never shipped. The checker itself lives in `src/main` and its tests in
// `src/test` (only `*Test.kt` lives in a test source set), which is the naming
// convention the suite asserts. See AGENTS.md, "Module boundaries".
//
// `kotlin.test` + Kotest, exactly as the domain suite (docs/testing.md).
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Keep javac's target in step with Kotlin's: a plain JVM module compiles Java
// too, and the plugin fails the build when the two disagree. KMP modules have no
// Java compilation, which is why the other modules need only the Kotlin setting.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // The suite reads the repository from disk by path, which Gradle cannot see:
    // without this input, editing a workflow or a build file and running `check`
    // reports the architecture tests UP-TO-DATE and verifies the tree that was
    // there before. Declaring the trees the rules inspect makes a change rerun
    // them, at the cost of rerunning on a source edit, which is the point.
    inputs.files(
        fileTree(rootDir) {
            include(
                "**/*.gradle.kts",
                "**/gradle.properties",
                "gradle/**/*.toml",
                "**/src/**/*.kt",
                "**/package.json",
                ".github/**",
                "tools/packaging/**",
            )
            exclude("**/build/**", "**/node_modules/**", "**/.gradle/**", "**/.kotlin/**")
        },
    ).withPropertyName("repositoryTree").withPathSensitivity(PathSensitivity.RELATIVE)
}
