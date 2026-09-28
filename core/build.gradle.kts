import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The domain: vault, records, merge, tokenisation, word identity, sync engine,
// parsers and provider adapters. Pure Kotlin in commonMain, so it runs on the
// JVM in seconds with no emulator and no Docker (see docs/build.md).
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
