package app.lekto.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The scanner is the only part that touches the working tree, so it is the only
 * part that needs its own tests: the Gradle and Kotlin parsing are exercised
 * against small, hand-written inputs instead of the repository, which is
 * covered by [LektoArchitectureTest].
 */
class RepositoryScannerTest :
    FunSpec({

        test("reads the included modules from settings") {
            val settings = """
                include(":core")
                include(":integrations:webdav")
            """.trimIndent()

            GradleBuildFile.includedModules(settings) shouldContainExactly listOf(":core", ":integrations:webdav")
        }

        test("maps a module path to its directory") {
            GradleBuildFile.moduleDirectory(":integrations:webdav") shouldBe "integrations/webdav"
        }

        test("maps a file path to its source set") {
            RepositoryScanner.sourceSetOf("core/src/commonTest/kotlin/app/lekto/core/LektoTest.kt") shouldBe
                "commonTest"
            RepositoryScanner.sourceSetOf("tools/dictionaries/src/main/kotlin/Main.kt") shouldBe "main"
        }

        test("maps a file path to the package its directory implies") {
            PlacementRules.packageFromPath("core/src/commonMain/kotlin/app/lekto/core/Lekto.kt") shouldBe
                "app.lekto.core"
        }

        test("tags a project dependency with the source set that declares it") {
            val build = """
                kotlin {
                    sourceSets {
                        commonMain.dependencies {
                            implementation(project(":core"))
                        }
                        commonTest.dependencies {
                            implementation(project(":testkit"))
                        }
                    }
                }
            """.trimIndent()

            GradleBuildFile.declaredDependencies(build) shouldContainExactly listOf(
                DeclaredDependency(":core", testScoped = false),
                DeclaredDependency(":testkit", testScoped = true),
            )
        }

        test("ignores project references in comments and strings") {
            val build = """
                dependencies {
                    // implementation(project(":ignored"))
                    val text = "project(\":also-ignored\")"
                    kover(project(":core"))
                }
            """.trimIndent()

            GradleBuildFile.declaredDependencies(build) shouldContainExactly listOf(
                DeclaredDependency(":core", testScoped = false),
            )
        }
    })
