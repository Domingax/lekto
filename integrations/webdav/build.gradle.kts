import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The WebDAV sync driver, isolated so drivers can be added without touching the
// domain's test compile. The `SyncTarget` seam it implements arrives in the
// domain; ticket #27 is the driver itself.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
    }
}
