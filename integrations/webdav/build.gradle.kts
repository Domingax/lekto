@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The WebDAV sync driver, isolated so drivers can be added without touching the
// domain's test compile. The `SyncTarget` seam it implements arrives in the
// domain; ticket #27 is the driver itself.
//
// The driver is Kotlin Multiplatform with a JVM target (desktop) and an Android
// target (the first-class client, issue #116). Its platform-independent logic
// lives in `jvmSharedMain`, shared by both; only the HTTP transport differs —
// `java.net.http` on the JVM, OkHttp on Android — behind the `WebDavTransport`
// seam (ADR-0026). The Android target is applied only when an Android SDK is
// discoverable, the same rule as `core` (docs/build.md#prerequisites).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
}

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
        // The WebDAV integration lane (ticket #7) drives a real server through
        // Testcontainers' JUnit 5 support, so the JVM test run uses the JUnit
        // Platform. See docs/testing.md.
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    if (androidEnabled) {
        androidLibrary {
            namespace = "app.lekto.integrations.webdav"
            compileSdk = libs.versions.android.compileSdk.get().toInt()
            minSdk = libs.versions.android.minSdk.get().toInt()
            // Enables the `androidHostTest` source set, where the driver contract
            // runs against the in-process fake server under Robolectric
            // (docs/testing.md#android-host-lane).
            withHostTest {
                isIncludeAndroidResources = true
            }
        }
    }

    // Share `jvmSharedMain` between the JVM and Android targets, so the driver's
    // logic compiles once for both and the transports cannot drift. The AGP
    // Android target is an external KMP target named "android", so it is matched
    // by name rather than by `withAndroidTarget()`.
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
            api(project(":core"))
        }
        if (androidEnabled) {
            // Android ships no `java.net.http` and `HttpURLConnection` rejects
            // `PROPFIND`/`MKCOL`, so OkHttp (Apache-2.0, AGPL-compatible;
            // ADR-0011) is the Android transport (ADR-0026). Desktop keeps the
            // dependency-free JDK client.
            getByName("androidMain").dependencies {
                implementation(libs.okhttp)
            }
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit.jupiter)
            implementation(libs.testcontainers)
            implementation(libs.testcontainers.junit.jupiter)
            // The shared `SyncTargetContract` the driver must pass (ticket #26),
            // plus the in-memory fake and fixtures its cases build on.
            implementation(project(":testkit"))
        }
        if (androidEnabled) {
            // The driver contract on the runtime Android uses (issue #116): the
            // same `SyncTargetContract` against `testkit`'s in-process
            // `FakeWebDavServer`, which Robolectric runs on the host JVM, so the
            // OkHttp transport is proved without Docker and without a network.
            getByName("androidHostTest").dependencies {
                implementation(project(":testkit"))
                implementation(libs.junit4)
                implementation(libs.robolectric)
            }
        }
    }
}
