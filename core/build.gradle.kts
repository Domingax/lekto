@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The domain: vault, records, merge, tokenisation, word identity, sync engine,
// parsers and provider adapters. Pure Kotlin in commonMain, so it runs on the
// JVM in seconds with no emulator and no Docker (see docs/build.md).
//
// The platform-backed implementation (the EPUB parser, the ICU segmenter, the
// directory-backed vault) lives in `jvmSharedMain`, shared by the JVM target and
// an optional Android target. The Android target exists so those implementations
// can be run on a simulated Android runtime by Robolectric host tests (ticket
// #49); without an Android SDK the module still builds as a JVM/KMP project.
//
// The test harness lives in commonTest: `kotlin.test` assertions as the baseline,
// Kotest for the spec style, matchers and property testing, and Turbine for
// flows. On the JVM, Kotest runs on the JUnit Platform (Kotest 6 setup), so the
// jvm target configures `useJUnitPlatform()` and pulls the JUnit 5 runner.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
}

// The Android target is only applied when an Android SDK is discoverable (or
// forced with -Plekto.android=true|false); see settings.gradle.kts and
// docs/build.md. The KMP Android library plugin gives the module the
// `androidHostTest` source set AGP's built-in KMP support does not create until
// `withHostTest` is called (docs/testing.md#android-host-lane).
val androidEnabled: Boolean =
    gradle.extensions.extraProperties.get("lekto.androidEnabled") as Boolean

if (androidEnabled) {
    apply(plugin = libs.plugins.androidKotlinMultiplatformLibrary.get().pluginId)
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

    if (androidEnabled) {
        androidLibrary {
            namespace = "app.lekto.core"
            compileSdk = libs.versions.android.compileSdk.get().toInt()
            minSdk = libs.versions.android.minSdk.get().toInt()
            // Enables the `androidHostTest` source set and runs it on the host
            // JVM through Robolectric (docs/testing.md#android-host-lane).
            withHostTest {
                isIncludeAndroidResources = true
            }
        }
    }

    // Share `jvmSharedMain`/`jvmSharedTest` between the JVM and Android targets.
    // The default hierarchy template keeps `jvmMain` and `androidMain` as
    // siblings, so the platform-backed code would otherwise compile for one and
    // not the other. The AGP Android target is an external KMP target named
    // "android", so it is matched by name rather than by `withAndroidTarget()`
    // (which matches a KGP `KotlinAndroidTarget`).
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withCompilations { compilation -> compilation.target.name == "android" }
            }
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
        // The EPUB parser and the ICU segmenter are JVM-only: java.util.zip
        // and jsoup build the [StructuredText], ICU4J segments it. They are
        // shared by the jvm and Android targets so both run the same code.
        named("jvmSharedMain").configure {
            dependencies {
                implementation(libs.jsoup)
                implementation(libs.icu4j)
            }
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
        // The JVM dictionary reader opens the pre-built SQLite pack in place
        // (ADR-0017); xerial sqlite-jdbc (Apache-2.0, AGPL-compatible) is
        // desktop-only, because its bundled natives do not run on Android — the
        // Android app supplies the framework SQLite behind the same seam.
        named("jvmMain").configure {
            dependencies {
                implementation(libs.sqlite.jdbc)
            }
        }
        if (androidEnabled) {
            // Robolectric runs the platform code on a simulated Android runtime
            // (ticket #49). Test-scope only; JUnit 4 drives the Robolectric
            // runner. The real EPUB fixture it reads lives in commonTest
            // resources, so the host test sees it too.
            named("androidHostTest").configure {
                dependencies {
                    implementation(libs.junit4)
                    implementation(libs.robolectric)
                }
            }
        }
    }
}
