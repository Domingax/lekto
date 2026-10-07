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
        // The WebDAV integration lane (ticket #7) drives a real server through
        // Testcontainers' JUnit 5 support, so the JVM test run uses the JUnit
        // Platform. See docs/testing.md.
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit.jupiter)
            implementation(libs.testcontainers)
            implementation(libs.testcontainers.junit.jupiter)
            // The shared `SyncTargetContract` the driver must pass (ticket #26),
            // plus the in-memory fake and fixtures its cases build on.
            implementation(project(":testkit"))
        }
    }
}
