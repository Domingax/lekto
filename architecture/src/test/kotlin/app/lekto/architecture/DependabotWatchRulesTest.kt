package app.lekto.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The watch list is paired with the manifests on disk: every manifest the
 * repository ships must have a Dependabot entry (issue #153). The rule is proven
 * against hand-built values for the parser and for a deliberate gap, and against
 * the real tree at the end, so neither a parser regression nor a missing entry
 * can pass silently — which is how the repository came to have 63 alerts and no
 * `dependabot.yml` at all.
 */
class DependabotWatchRulesTest :
    FunSpec({

        test("reads the ecosystem and the directory of every entry") {
            val entries = DependabotWatchRules.entries(
                """
                version: 2
                updates:
                  # A comment between entries disturbs nothing.
                  - package-ecosystem: "gradle"
                    directory: "/"
                    schedule:
                      interval: "weekly"
                  - package-ecosystem: npm
                    directory: /guide
                """.trimIndent(),
            )

            entries shouldBe listOf(
                DependabotEntry("gradle", "/"),
                DependabotEntry("npm", "/guide"),
            )
        }

        test("a manifest no entry names is a violation") {
            val violations = DependabotWatchRules.watch(
                entries = listOf(DependabotEntry("npm", "/guide")),
                manifests = listOf(
                    PackageManifest("npm", "/guide", "guide/package.json"),
                    PackageManifest("gradle", "/", "settings.gradle.kts"),
                ),
            )

            violations.single().rule shouldBe Rules.DEPENDABOT_WATCH
            violations.single().subject shouldBe "settings.gradle.kts"
        }

        test("a directory matches whatever its slashes") {
            DependabotWatchRules.watch(
                entries = listOf(DependabotEntry("npm", "guide")),
                manifests = listOf(PackageManifest("npm", "/guide/", "guide/package.json")),
            ) shouldBe emptyList()
        }

        test("an entry in a form the reader does not know leaves its manifest unwatched") {
            val entries = DependabotWatchRules.entries(
                """
                version: 2
                updates:
                  - package-ecosystem: "npm"
                    directories:
                      - "/guide"
                """.trimIndent(),
            )

            DependabotWatchRules.watch(
                entries = entries,
                manifests = listOf(PackageManifest("npm", "/guide", "guide/package.json")),
            ).single().subject shouldBe "guide/package.json"
        }

        test("every manifest the repository ships is watched by Dependabot") {
            val root = RepositoryScanner.repositoryRoot()
            val config = File(root, ".github/dependabot.yml")

            withClue("no .github/dependabot.yml watches the repository") {
                config.isFile shouldBe true
            }

            DependabotWatchRules.watch(
                entries = DependabotWatchRules.entries(config.readText()),
                manifests = RepositoryScanner.findManifests(root),
            ) shouldBe emptyList()
        }
    })
