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
    alias(libs.plugins.kotlinSerialization)
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
        commonMain.dependencies {
            // Records are one JSON file each (ADR-0003), so the domain encodes
            // and decodes records with kotlinx.serialization. `api`, because a
            // record's body is a `JsonObject` and therefore part of the public
            // model the testkit and the application both handle.
            api(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            // The EPUB parser and the ICU segmenter are JVM-only: java.util.zip
            // and jsoup build the [StructuredText], ICU4J segments it. They live
            // in jvmMain so commonMain stays pure Kotlin (see the spike report).
            implementation(libs.jsoup)
            implementation(libs.icu4j)
        }
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
