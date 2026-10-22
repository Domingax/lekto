package app.lekto.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

/**
 * The desktop packaging contract (issue #29): every platform gets an installer,
 * the release lane builds each on the operating system that owns jpackage, the
 * Windows accessibility module ships, and one version names both the APK and the
 * installers. The reader is proven against hand-built values and deliberately
 * broken snippets, then against the real tree, so neither a parser regression nor
 * a dropped platform can pass silently until a release.
 */
class DesktopPackagingTest :
    FunSpec({

        test("reads the formats a build script declares") {
            DesktopPackaging.formats(
                """
                nativeDistributions {
                    targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe)
                }
                """.trimIndent(),
            ) shouldBe setOf("dmg", "msi", "exe")
        }

        test("a commented-out format is not a declaration") {
            DesktopPackaging.formats(
                """
                nativeDistributions {
                    // targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe)
                    targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
                }
                """.trimIndent(),
            ) shouldBe setOf("dmg", "msi")
        }

        test("a dropped format leaves the promise incomplete") {
            val declared = DesktopPackaging.formats(
                "nativeDistributions { targetFormats(TargetFormat.Dmg, TargetFormat.Msi) }",
            )

            (DesktopPackaging.REQUIRED_FORMATS - declared) shouldBe setOf("exe")
        }

        test("reads the runner and installers of every packaging job") {
            DesktopPackaging.packages(
                """
                strategy:
                  matrix:
                    include:
                      - label: macos-arm64
                        runner: macos-15
                        formats: Dmg
                      - label: windows-x64
                        runner: windows-latest
                        formats: Msi Exe
                """.trimIndent(),
            ) shouldBe setOf(
                DesktopPackaging.PackageJob(runner = "macos-15", formats = setOf("dmg")),
                DesktopPackaging.PackageJob(runner = "windows-latest", formats = setOf("msi", "exe")),
            )
        }

        test("an installer built on the wrong platform is a violation") {
            val built = DesktopPackaging.packages(
                """
                - label: linux-x64
                  runner: ubuntu-latest
                  formats: Dmg
                """.trimIndent(),
            )

            withClue("a dmg built on Linux cannot satisfy a platform's requirement") {
                (DesktopPackaging.REQUIRED_PACKAGES - built) shouldBe DesktopPackaging.REQUIRED_PACKAGES
            }
        }

        test("an accessibility module outside a modules call does not count") {
            withClue("a comment that names the module is not a declaration") {
                DesktopPackaging.shipsAccessibility(
                    "// modules(\"jdk.accessibility\") is deliberately commented out here",
                ) shouldBe false
            }
            DesktopPackaging.shipsAccessibility(
                """
                modules(
                    "jdk.accessibility",
                    "java.sql",
                )
                """.trimIndent(),
            ) shouldBe true
        }

        test("the application declares an installer for every platform") {
            val root = RepositoryScanner.repositoryRoot()
            val buildScript = File(root, "app/build.gradle.kts").readText()

            withClue("app/build.gradle.kts no longer promises every installer format") {
                DesktopPackaging.formats(buildScript) shouldContainAll DesktopPackaging.REQUIRED_FORMATS
            }
            withClue("the Windows accessibility module is not shipped (ADR-0007)") {
                DesktopPackaging.shipsAccessibility(buildScript) shouldBe true
            }
        }

        test("the release lane builds each installer on the platform that owns it") {
            val root = RepositoryScanner.repositoryRoot()
            val workflow = File(root, ".github/workflows/release.yml")

            withClue("no release lane builds the desktop installers") {
                workflow.isFile shouldBe true
            }
            DesktopPackaging.packages(workflow.readText()) shouldContainAll DesktopPackaging.REQUIRED_PACKAGES
        }

        test("the Linux job wraps the application image in the AppImage") {
            val root = RepositoryScanner.repositoryRoot()
            val workflow = File(root, ".github/workflows/release.yml").readText()
            val script = File(root, "tools/packaging/appimage.sh")

            withClue("no AppImage script turns the application image into the portable file") {
                script.isFile shouldBe true
                script.canExecute() shouldBe true
            }
            withClue("the Linux packaging job no longer calls the AppImage script") {
                workflow.contains("tools/packaging/appimage.sh") shouldBe true
            }
        }

        test("the APK and the installers read one version") {
            val root = RepositoryScanner.repositoryRoot()
            val properties = File(root, "gradle.properties").readText()
            val buildScript = File(root, "app/build.gradle.kts").readText()
            val sharedVersion = DesktopPackaging.version(properties)

            withClue("gradle.properties has no well-formed lekto.version") {
                sharedVersion shouldNotBe null
            }
            withClue("jpackage's macOS bundler rejects a version whose first number is zero") {
                checkNotNull(sharedVersion).substringBefore('.').toInt() shouldBeGreaterThanOrEqual 1
            }
            withClue("the app no longer reads the shared version") {
                buildScript.contains("""providers.gradleProperty("lekto.version")""") shouldBe true
            }
            withClue("Android's versionName no longer reads the shared version") {
                buildScript.contains("versionName = appVersion") shouldBe true
            }
            withClue("the installers' packageVersion no longer reads the shared version") {
                buildScript.contains("packageVersion = appVersion") shouldBe true
            }
        }
    })
