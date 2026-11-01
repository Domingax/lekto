@file:OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)

import org.gradle.api.tasks.PathSensitivity
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

// `versionCode` has to rise with every published APK, and the semantic version is
// the only version this project edits, so it is derived from it rather than kept
// as a second number (issue #262): major*10000 + minor*100 + patch, so 1.1.0 is
// 10100 and 1.1.1 is 10101. A preview is named by its git tag, not by
// `lekto.version` — jpackage rejects a version suffix — so a preview and its
// final share a versionCode, which Android still installs as an update because
// the release key is the same.
val appVersionCode: Int = run {
    val fields = appVersion.split('.')
    require(fields.size == 3 && fields.all { it.toIntOrNull() != null }) {
        "lekto.version=$appVersion is not a major.minor.patch version, so it cannot derive a versionCode."
    }
    val (major, minor, patch) = fields.map { it.toInt() }
    require(minor in 0..99 && patch in 0..99) {
        "lekto.version=$appVersion does not fit the versionCode scheme: minor and patch must be 0..99."
    }
    major * 10_000 + minor * 100 + patch
}

// Android release signing (issue #262). The release lane decodes the keystore
// into $RUNNER_TEMP and passes its path in `lekto.android.keystore`; the alias and
// the two passwords arrive through the environment, so no secret reaches a command
// line, a Gradle property or the repository. A build without the property stays
// unsigned, which is what a fork and the fast lane rely on, and a build with the
// property but without the rest fails rather than publishing an unsigned APK.
val androidKeystorePath: String? = providers.gradleProperty("lekto.android.keystore").orNull
val androidKeystorePassword: Provider<String> = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val androidKeyAlias: Provider<String> = providers.environmentVariable("ANDROID_KEY_ALIAS")
val androidKeyPassword: Provider<String> = providers.environmentVariable("ANDROID_KEY_PASSWORD")

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
            versionCode = appVersionCode
            versionName = appVersion
            // Drives the instrumented tests the nightly lane runs on an emulator
            // (issue #71).
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        // The release key is the project's, not a per-machine one: the same key
        // must sign every release or an installed copy cannot be updated. It is
        // only configured when the lane supplies it, so the fast lane and a fork
        // still build an unsigned release APK with no secret present.
        signingConfigs {
            if (androidKeystorePath != null) {
                val missing = listOf(
                    "ANDROID_KEYSTORE_PASSWORD" to androidKeystorePassword,
                    "ANDROID_KEY_ALIAS" to androidKeyAlias,
                    "ANDROID_KEY_PASSWORD" to androidKeyPassword,
                ).filterNot { (_, value) -> value.isPresent }.map { (name, _) -> name }
                require(missing.isEmpty()) {
                    "lekto.android.keystore is set but the signing environment is incomplete " +
                        "(missing ${missing.joinToString()}): refusing to publish an unsigned " +
                        "release APK (issue #262)."
                }
                val keystore = file(androidKeystorePath)
                require(keystore.isFile) {
                    "lekto.android.keystore=$androidKeystorePath is not a file: the release lane " +
                        "decodes it into \$RUNNER_TEMP before it builds (issue #262)."
                }
                create("release") {
                    storeFile = keystore
                    storePassword = androidKeystorePassword.get()
                    this.keyAlias = androidKeyAlias.get()
                    this.keyPassword = androidKeyPassword.get()
                }
            }
        }

        buildTypes {
            release {
                // R8 stays off in this issue: turning it on changes the shipped
                // APK and needs its own tests on a minified build (issue #262,
                // follow-up #273). The measured cost is the whole reason to
                // revisit it.
                isMinifyEnabled = false
                isShrinkResources = false
                signingConfigs.findByName("release")?.let { signingConfig = it }
            }
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
                iconFile.set(project.file("src/desktopMain/resources/app-icon/app-icon.ico"))
            }
            macOS {
                bundleID = "app.lekto"
                dockName = "Lekto"
                iconFile.set(project.file("src/desktopMain/resources/app-icon/app-icon.icns"))
            }
        }
    }
}

// The desktop icon rasters (issue #177). The mark's geometry lives in one place,
// the SVGs under `tools/icons/mark/`; `tools/icons/mark.sh` writes the two vector
// drawables this task reads, and the PNG, ICO and ICNS it produces are committed,
// the way the Lucide `ImageVector` set is. It has to live in `app` rather than in
// `tools/`: nothing may depend on `app`, and the renderer needs the desktop
// classpath, where Skia already is through Compose.
val renderAppIcon by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Renders the committed desktop icon rasters from the generated vector drawables."
    dependsOn("desktopMainClasses")
    workingDir = projectDir
    val desktopMain = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath(desktopMain.output.allOutputs, desktopMain.runtimeDependencyFiles)
    mainClass.set("app.lekto.tools.RenderAppIconKt")
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

// `AppIconTest` and `LauncherIconTest` read the Android source set at runtime, by
// path, which Gradle cannot see: without this input, changing the mark and running
// `check` reports the goldens UP-TO-DATE and verifies the drawing that was there
// before. Declaring the tree makes both tests rerun when it moves.
tasks.matching { it.name == "desktopTest" }.configureEach {
    inputs.dir(layout.projectDirectory.dir("src/androidMain"))
        .withPropertyName("androidSourceSet")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
