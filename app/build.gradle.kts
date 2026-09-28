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
}

val androidEnabled: Boolean =
    gradle.extensions.extraProperties.get("lekto.androidEnabled") as Boolean

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
            }
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
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
            versionName = "0.1.0"
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
    }
}
