package app.lekto.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The Android release contract (issue #262): the release lane signs and verifies
 * the APK it publishes, the release job waits for it, the fast lane compiles the
 * unsigned variant first, and the version it ships is derived from the one
 * project version rather than kept as a second number. The reader is proved
 * against hand-built workflows, then against the real tree, so neither a parser
 * regression nor a dropped step can pass silently until a release.
 */
class AndroidPackagingTest :
    FunSpec({

        test("reads the body of a job and says nothing about its neighbour") {
            val workflow = """
                on:
                  push:
                jobs:
                  package:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo package
                  # A comment is not a job.
                  android:
                    runs-on: ubuntu-latest
                    steps:
                      - run: ./gradlew :app:assembleRelease
                  release:
                    needs: [package, android]
            """.trimIndent()

            AndroidPackaging.job(workflow, "android")?.trimEnd() shouldBe
                """
                    |    runs-on: ubuntu-latest
                    |    steps:
                    |      - run: ./gradlew :app:assembleRelease
                """.trimMargin()
        }

        test("a comment that names a tool is not a step") {
            val workflow = """
                jobs:
                  android:
                    steps:
                      # apksigner verify runs in the build step.
                      - run: ./gradlew :app:assembleRelease
            """.trimIndent()

            withClue("prose must not satisfy a rule that asks for a command") {
                requireNotNull(AndroidPackaging.job(workflow, "android")).contains("apksigner") shouldBe false
            }
        }

        test("reads the dependencies a job waits for") {
            val workflow = """
                jobs:
                  release:
                    if: startsWith(github.ref, 'refs/tags/v')
                    needs: [package, android]
                    runs-on: ubuntu-latest
            """.trimIndent()

            AndroidPackaging.needs(workflow, "release") shouldBe setOf("package", "android")
        }

        test("a job that is not declared has no body and no dependencies") {
            withClue("an unknown job must not read as an empty job") {
                AndroidPackaging.job("jobs:\n  package:\n    runs-on: ubuntu-latest", "android").shouldBeNull()
            }
            AndroidPackaging.needs("jobs:\n  package:\n    runs-on: ubuntu-latest", "android") shouldBe emptySet()
        }

        test("the app derives the versionCode from the one shared version") {
            val root = RepositoryScanner.repositoryRoot()
            val buildScript = File(root, "app/build.gradle.kts").readText()

            withClue("a hardcoded versionCode cannot rise with the version") {
                Regex("""versionCode\s*=\s*\d+""").containsMatchIn(buildScript) shouldBe false
            }
            withClue("Android's versionCode no longer reads the derived value") {
                buildScript.contains("versionCode = appVersionCode") shouldBe true
            }
            withClue("1.0.0 must derive 10000, 1.1.0 10100 and 1.1.1 10101 (issue #262)") {
                Regex("""major\s*\*\s*10_000\s*\+\s*minor\s*\*\s*100\s*\+\s*patch""")
                    .containsMatchIn(buildScript) shouldBe true
            }
        }

        test("the signing configuration is conditional on the release lane's secrets") {
            val root = RepositoryScanner.repositoryRoot()
            val buildScript = File(root, "app/build.gradle.kts").readText()

            withClue("a build without the keystore must still produce an unsigned APK") {
                buildScript.contains("""providers.gradleProperty("lekto.android.keystore")""") shouldBe true
            }
            withClue("the signing secrets must not be Gradle properties, which a command line exposes") {
                buildScript.contains("ANDROID_KEYSTORE_PASSWORD") shouldBe true
            }
        }

        test("the release lane signs, verifies and names the APK it publishes") {
            val root = RepositoryScanner.repositoryRoot()
            val workflow = File(root, ".github/workflows/release.yml")

            withClue("no release lane publishes an APK") { workflow.isFile shouldBe true }
            val lane = requireNotNull(AndroidPackaging.job(workflow.readText(), AndroidPackaging.ARTIFACT)) {
                "release.yml has no '${AndroidPackaging.ARTIFACT}' job, so a tag publishes no APK"
            }

            withClue("the Android job must build the release variant, not the debug one") {
                lane.contains(AndroidPackaging.RELEASE_TASK) shouldBe true
            }
            withClue("the Android job needs the SDK, so it cannot turn the Android target off") {
                lane.contains("-Plekto.android=true") shouldBe true
                lane.contains("-Plekto.android=false") shouldBe false
            }
            withClue("the APK is signed from the keystore the lane decodes") {
                lane.contains("-Plekto.android.keystore=") shouldBe true
                lane.contains("RUNNER_TEMP") shouldBe true
            }
            withClue("the build signing the file is not the promise; the shipped artefact is") {
                lane.contains("apksigner") shouldBe true
            }
            withClue("the asset name is what the guide and the release body point at") {
                lane.contains(AndroidPackaging.APK_ASSET) shouldBe true
            }
            withClue("the release job collects the Android artifact by name") {
                lane.contains("name: ${AndroidPackaging.ARTIFACT}") shouldBe true
            }
        }

        test("the release job waits for the Android APK") {
            val root = RepositoryScanner.repositoryRoot()
            val workflow = File(root, ".github/workflows/release.yml").readText()

            withClue("a release without the APK is not the release the guide promises") {
                AndroidPackaging.needs(workflow, "release") shouldBe setOf("package", AndroidPackaging.ARTIFACT)
            }
        }

        test("the fast lane compiles the release variant without a secret") {
            val root = RepositoryScanner.repositoryRoot()
            val workflow = File(root, ".github/workflows/ci.yml").readText()

            val lane = requireNotNull(AndroidPackaging.job(workflow, "fast")) {
                "ci.yml has no 'fast' job, so a broken release build only fails on a tag"
            }
            withClue("a resource or packaging failure must fail a pull request, not a tag") {
                lane.contains(AndroidPackaging.RELEASE_TASK) shouldBe true
            }
            withClue("a fork carries no signing secret, so the fast lane must not need one") {
                lane.contains("ANDROID_KEYSTORE_BASE64") shouldBe false
            }
        }
    })
