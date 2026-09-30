rootProject.name = "lekto"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    // Lets Gradle provision the pinned JDK itself, so a clean checkout needs
    // nothing installed beyond a JVM able to run the wrapper.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// --- Android target detection -------------------------------------------------
// The fast JVM loop must build with nothing installed beyond the pinned JDK.
// The Android target only activates when an Android SDK is discoverable, or when
// it is forced explicitly with -Plekto.android=true|false. See docs/build.md.
val androidOverride: Boolean? =
    providers.gradleProperty("lekto.android").orNull?.toBooleanStrictOrNull()

fun detectedAndroidSdk(): String? {
    val localProperties = rootDir.resolve("local.properties")
    val fromLocalProperties = if (localProperties.isFile) {
        java.util.Properties()
            .apply { localProperties.inputStream().use { load(it) } }
            .getProperty("sdk.dir")
    } else {
        null
    }
    return listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        fromLocalProperties,
        System.getenv("HOME")?.let { java.io.File(it, "Android/Sdk").takeIf { sdk -> sdk.isDirectory }?.path },
    ).firstOrNull { it.isNotBlank() }
}

val androidSdk: String? = detectedAndroidSdk()
gradle.extensions.extraProperties.set("lekto.androidEnabled", androidOverride ?: (androidSdk != null))
gradle.extensions.extraProperties.set("lekto.androidSdk", androidSdk)

include(":core")
include(":testkit")
include(":integrations:webdav")
include(":app")
include(":tools:dictionaries")
// The architecture suite (ticket #9): the module boundaries and naming
// conventions expressed as tests. Test-scoped and never shipped, so it sits
// beside `:testkit` on the verification side of the graph.
include(":architecture")
