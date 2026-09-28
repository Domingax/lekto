import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Contract suites and in-memory fakes, published as a library because a KMP
// commonTest source set cannot be shared through a test-only dependency.
// Ticket #5 fills this out; here it exists, compiles and depends on the domain.
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
