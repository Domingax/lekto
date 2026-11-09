package app.lekto.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * The dictionary-data contract (issue #306, ADR-0046, ADR-0047): the four layers
 * are one reusable workflow the data repository calls, the application publishes
 * no data release, the manifest is built from what the run produced and published
 * after the bytes it names, and the app follows the movable manifest in the data
 * repository. Each rule is proved against a hand-built workflow first, then
 * against the real tree.
 */
class DictionaryDataPipelineTest :
    FunSpec({

        test("publishes the manifest after the assets it names") {
            val ordered = """
                jobs:
                  assemble:
                    steps:
                      - run: gh release create dictionary-20261101 --draft assets/*
                      - run: gh release upload dictionary-20261101 manifest.json
                      - run: gh release edit dictionary-20261101 --draft=false --latest
            """.trimIndent()
            val reversed = """
                jobs:
                  assemble:
                    steps:
                      - run: gh release upload dictionary-20261101 manifest.json
                      - run: gh release create dictionary-20261101 --draft assets/*
                      - run: gh release edit dictionary-20261101 --draft=false --latest
            """.trimIndent()
            val publishedEarly = """
                jobs:
                  assemble:
                    steps:
                      - run: gh release create dictionary-20261101 assets/*
                      - run: gh release upload dictionary-20261101 manifest.json
                      - run: gh release edit dictionary-20261101 --draft=false --latest
            """.trimIndent()

            DictionaryDataPipeline.publishesManifestLast(ordered) shouldBe true
            withClue("a manifest uploaded first can name bytes that are not there yet") {
                DictionaryDataPipeline.publishesManifestLast(reversed) shouldBe false
            }
            withClue("a non-draft release becomes latest before its manifest exists") {
                DictionaryDataPipeline.publishesManifestLast(publishedEarly) shouldBe false
            }
        }

        test("builds the manifest from the produced files, not the configured roster") {
            val produced = """
                jobs:
                  assemble:
                    steps:
                      - run: dictionaries --assemble-release assets
            """.trimIndent()
            val configured = """
                jobs:
                  assemble:
                    steps:
                      - run: dictionaries --languages-config languages.json
            """.trimIndent()

            DictionaryDataPipeline.buildsFromProducedFiles(produced) shouldBe true
            withClue("a manifest from languages.json advertises a pair that may have failed") {
                DictionaryDataPipeline.buildsFromProducedFiles(configured) shouldBe false
            }
        }

        test("the reusable workflow cannot run on a push, a pull request or a schedule") {
            val reusable = """
                name: dictionary-data
                on:
                  workflow_call:
                jobs:
                  assemble:
                    runs-on: ubuntu-latest
            """.trimIndent()
            val triggered = """
                name: dictionary-data
                on:
                  schedule:
                    - cron: "17 3 1 * *"
                jobs:
                  assemble:
                    runs-on: ubuntu-latest
            """.trimIndent()

            DictionaryDataPipeline.isReusableOnly(reusable) shouldBe true
            withClue("the application must not run the data lane alongside its own CI") {
                DictionaryDataPipeline.isReusableOnly(triggered) shouldBe false
            }
        }

        test("the application ships the four layers as one reusable workflow") {
            val root = RepositoryScanner.repositoryRoot()
            val reusable = File(root, DictionaryDataPipeline.REUSABLE)

            withClue("no workflow builds the dictionary data") { reusable.isFile shouldBe true }
            val text = reusable.readText()

            DictionaryDataPipeline.isReusableOnly(text) shouldBe true
            withClue("the data repository's own lane is the only trigger") {
                text shouldContain "workflow_call:"
            }
            withClue("the assembly is not built from the configured roster") {
                DictionaryDataPipeline.buildsFromProducedFiles(text) shouldBe true
            }
            withClue("a snapshot must not replace a public one's bytes") {
                DictionaryDataPipeline.publishesManifestLast(text) shouldBe true
            }
        }

        test("the four layers and their preparation jobs are separate jobs") {
            val root = RepositoryScanner.repositoryRoot()
            val workflow = File(root, DictionaryDataPipeline.REUSABLE).readText()

            (DictionaryDataPipeline.LAYERS + DictionaryDataPipeline.PREPARATION).forEach { layer ->
                withClue("the '$layer' job is missing") {
                    (WorkflowJobs.job(workflow, layer) != null) shouldBe true
                }
            }
            withClue("a pair needs its language's shared intermediate") {
                WorkflowJobs.needs(workflow, "pair") shouldBe setOf("prepare", "extract")
            }
            withClue("a table needs the shared index and one frequency list") {
                WorkflowJobs.needs(workflow, "difficulty") shouldBe setOf("prepare", "extract", "frequencies")
            }
            withClue("the assembly waits for every pair and table") {
                WorkflowJobs.needs(workflow, "assemble") shouldBe setOf("prepare", "pair", "difficulty")
            }
        }

        test("each layer runs the CLI stage it owns") {
            val root = RepositoryScanner.repositoryRoot()
            val workflow = File(root, DictionaryDataPipeline.REUSABLE).readText()

            val extract = requireNotNull(WorkflowJobs.job(workflow, "extract"))
            withClue("one edition is streamed into a shared index and per-pair content") {
                extract shouldContain "--reading-language"
                extract shouldContain "--extract-target"
                extract shouldContain "--output-dir"
            }
            val pair = requireNotNull(WorkflowJobs.job(workflow, "pair"))
            withClue("one pair writes one pack from its content file and the shared index") {
                pair shouldContain "--content"
                pair shouldContain "--form-index"
            }
            withClue("the drift gate compares the pair with its own previous count") {
                pair shouldContain "--previous-lemma-count"
            }
            val difficulty = requireNotNull(WorkflowJobs.job(workflow, "difficulty"))
            withClue("one language's table is keyed by its shared index") {
                difficulty shouldContain "--form-index"
                difficulty shouldContain "--frequency-input"
            }
            val assemble = requireNotNull(WorkflowJobs.job(workflow, "assemble"))
            withClue("the assembly applies the published lemma floor") {
                assemble shouldContain "--assemble-release"
            }
            withClue("old snapshots are pruned so the repository stays small") {
                assemble shouldContain "gh release delete"
            }
        }

        test("the data repository holds only the triggers") {
            val root = RepositoryScanner.repositoryRoot()
            val caller = File(root, DictionaryDataPipeline.CALLER)

            withClue("the caller is versioned so a change is reviewed with the CLI") { caller.isFile shouldBe true }
            val text = caller.readText()
            text shouldContain "workflow_dispatch:"
            text shouldContain "schedule:"
            withClue("the four layers live in the application, not in a second copy") {
                text shouldContain "uses: Domingax/lekto/.github/workflows/dictionary-data.yml@main"
            }
        }

        test("the application publishes no dictionary release") {
            val root = RepositoryScanner.repositoryRoot()

            withClue("the retired lane still publishes data into the application's releases") {
                File(root, DictionaryDataPipeline.RETIRED).isFile shouldBe false
            }
        }

        test("the app follows the movable manifest in the data repository") {
            val root = RepositoryScanner.repositoryRoot()
            val release = File(root, "app/src/commonMain/kotlin/app/lekto/language/LanguageRelease.kt").readText()

            release shouldContain "https://github.com/Domingax/lekto-data/releases/latest/download/manifest.json"
            withClue("the application's latest must keep meaning its latest version (ADR-0047)") {
                release shouldNotContain "github.com/Domingax/lekto/releases/latest/download/language-manifest.json"
            }
        }
    })
