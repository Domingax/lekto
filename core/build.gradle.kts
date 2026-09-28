import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The domain: vault, records, merge, tokenisation, word identity, sync engine,
// parsers and provider adapters. Pure Kotlin in commonMain, so it runs on the
// JVM in seconds with no emulator and no Docker (see docs/build.md).
//
// The test harness lives in commonTest: `kotlin.test` assertions as the baseline,
// Kotest for the spec style, matchers and property testing, and Turbine for
// flows. On the JVM, Kotest runs on the JUnit Platform (Kotest 6 setup), so the
// jvm target configures `useJUnitPlatform()` and pulls the JUnit 5 runner.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.property)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":testkit"))
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}
