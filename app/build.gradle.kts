@file:OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Compose Multiplatform application. The same `App()` composable runs on
// Android and on desktop; only the entry points differ.
//
// The Android Gradle plugin is only applied when an Android SDK is available
// (see settings.gradle.kts and docs/build.md). Without one the module still
// builds and runs as a desktop application, which keeps the fast loop
// JDK-only.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.roborazzi)
}

val androidEnabled: Boolean =
    gradle.extensions.extraProperties.get("lekto.androidEnabled") as Boolean

// The version both artefacts of a release carry (issue #29): Android's
// `versionName` and the desktop installers' `packageVersion` read the same
// property, so they cannot drift. See gradle.properties.
val appVersion: String = providers.gradleProperty("lekto.version").get()

if (androidEnabled) {
    apply(plugin = libs.plugins.androidApplication.get().pluginId)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    if (androidEnabled) {
        androidTarget {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        if (androidEnabled) {
            getByName("androidMain").dependencies {
                implementation(libs.androidx.activity.compose)
                // The WebDAV sync driver (issue #116; ADR-0026): the Android
                // client now owns a real sync target, wired under `Context.filesDir`
                // in `androidSync`, so Settings → Sync is usable on the
                // first-class client. The domain never names it; only this entry
                // point does.
                implementation(project(":integrations:webdav"))
            }
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            // The WebDAV sync driver (issue #28; ADR-0025): the desktop client is
            // the one composition root that owns a real sync target for now, so
            // the driver is wired here and contributes the settings screen's sync
            // section. The domain never names it; only this entry point does.
            implementation(project(":integrations:webdav"))
        }
        // The UI-semantics suite runs on the JVM (desktop) in seconds, with no
        // emulator. It lives in desktopTest rather than commonTest because the
        // Compose Multiplatform common test API cannot run under Android's local
        // (host) test configuration; the Android-specific `androidUnitTest` lane
        // below runs the same screens under Robolectric, and instrumented coverage
        // is a separate, slower nightly lane. See docs/testing.md and
        // docs/research/testing-harness.md §6.
        getByName("desktopTest").dependencies {
            implementation(project(":testkit"))
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            // kotlinx-coroutines-test supplies runTest and virtual time, so the
            // library controller's asynchronous import is driven deterministically
            // (docs/testing.md, "Deterministic seams").
            implementation(libs.kotlinx.coroutines.test)
            // UI screenshot goldens (ticket #7); recorded and verified by the
            // Roborazzi desktop tasks. See docs/testing.md.
            implementation(libs.roborazzi.core)
            implementation(libs.roborazzi.compose.desktop)
        }
        if (androidEnabled) {
            // Host tests (Robolectric, issue #71): the Compose UI runs on a
            // simulated Android runtime in the fast lane, so a constraint only
            // Android enforces — a `LazyColumn` key the platform cannot save, say
            // — fails here rather than on a device, which is where the vocabulary
            // list crashed (issue #69). JUnit 4 drives Robolectric.
            getByName("androidUnitTest").dependencies {
                implementation(project(":testkit"))
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.robolectric)
                implementation(libs.junit4)
            }
            // Instrumented smoke tests on an emulator in the nightly lane (issue
            // #71): the paths the host lane cannot reach, and a launched Activity.
            getByName("androidInstrumentedTest").dependencies {
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}

if (androidEnabled) {
    configure<com.android.build.api.dsl.ApplicationExtension> {
        namespace = "app.lekto"
        compileSdk = libs.versions.android.compileSdk.get().toInt()

        defaultConfig {
            applicationId = "app.lekto"
            minSdk = libs.versions.android.minSdk.get().toInt()
            targetSdk = libs.versions.android.targetSdk.get().toInt()
            versionCode = 1
            versionName = appVersion
            // Drives the instrumented tests the nightly lane runs on an emulator
            // (issue #71).
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        testOptions {
            // Robolectric host tests read the app's resources and merged manifest
            // (issue #71), so the Compose rule can start its host Activity.
            unitTests {
                isIncludeAndroidResources = true
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.lekto.MainKt"
        // The desktop client as a shippable artefact (issue #29): jpackage turns
        // the application into a native installer. A distribution can only be built
        // on its own operating system, so the release lane runs this on Windows and
        // macOS (`.github/workflows/release.yml`) and
        // `packageDistributionForCurrentOS` picks the format that applies here. On
        // Linux the lane builds the jpackage application image and wraps it in an
        // AppImage (`tools/packaging/appimage.sh`), because jpackage has no
        // single-file Linux format and a distribution package would not run on
        // every distribution.
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Lekto"
            packageVersion = appVersion
            description = "An open-source, local-first immersive reading app for language learning."
            copyright = "© 2026 Lekto contributors"
            vendor = "Lekto"
            // Java Access Bridge is how Compose Desktop reaches a screen reader on
            // Windows (ADR-0007, "Desktop accessibility is limited and accepted").
            // The module is part of every JDK, and shipping it is what makes that
            // conditional support reachable at all. The rest is what
            // `:app:suggestRuntimeModules` reports for this application: the
            // desktop WebDAV transport (`java.net.http`), the desktop dictionary
            // reader and JDBC (`java.sql`), and JNA's `sun.misc.Unsafe` use
            // (`jdk.unsupported`) among the module graph's other references.
            modules(
                "jdk.accessibility",
                "java.instrument",
                "java.management",
                "java.net.http",
                "java.sql",
                "jdk.javadoc",
                "jdk.unsupported",
            )
            windows {
                menuGroup = "Lekto"
                // A stable identity, so an upgrade replaces the previous install
                // instead of sitting beside it. Generated once and pinned.
                upgradeUuid = "6f1f3f9e-6d7a-4c1a-9c2e-8f0b5f3a7d21"
                iconFile.set(project.file("icons/lekto.ico"))
            }
            macOS {
                bundleID = "app.lekto"
                dockName = "Lekto"
                iconFile.set(project.file("icons/lekto.icns"))
            }
        }
    }
}

if (androidEnabled) {
    // The Compose test rule starts a host Activity; `ui-test-manifest` contributes
    // it to the debug app the Robolectric host tests run against (issue #71). The
    // AAR must be on the app, not the test classpath, for its manifest to merge —
    // `debugImplementation`, as the Android docs prescribe.
    dependencies {
        add("debugImplementation", libs.compose.ui.test.manifest)
    }

    // `check` does not reach the Android unit tests in this KMP setup (only the
    // desktop suite), so the fast lane would miss the host lane: wire it in
    // explicitly, as the acceptance criterion for issue #71 requires.
    tasks.named("check") { dependsOn("testDebugUnitTest") }

    // The host Activity the Compose rule starts is debug-only (`ui-test-manifest`
    // is a debug dependency), and the app has no release-specific behaviour to
    // prove, so the release unit-test variant is turned off. Without this, the
    // aggregate `check` runs it too and fails for want of that Activity.
    extensions.configure<com.android.build.api.variant.ApplicationAndroidComponentsExtension> {
        beforeVariants(selector().withBuildType("release")) { variant ->
            variant.enableUnitTest = false
        }
    }
}

// UI screenshot goldens (ticket #7). `separateOutputDirs` gives each KMP target
// its own goldens directory so the record/compare/verify tasks cannot race; the
// committed goldens live beside the tests rather than under the ignored build
// directory.
roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/desktopTest/goldens"))
    separateOutputDirs.set(true)
}
