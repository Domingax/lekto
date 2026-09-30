package app.lekto.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

/**
 * The architecture suite proper: it scans this repository and asserts the module
 * boundaries and naming conventions hold (ticket #9; AGENTS.md, "Module
 * boundaries you must not cross"). A boundary the build would otherwise let a
 * human review miss — the domain reaching for the application, an integration
 * leaking into the domain, a package drifting from its directory — fails
 * `./gradlew check` here.
 *
 * The rules themselves are proven to catch violations by
 * [ArchitectureViolationTest]; this test proves the real tree passes them.
 */
class LektoArchitectureTest :
    FunSpec({

        val root = RepositoryScanner.repositoryRoot()
        val repository = RepositoryScanner.scan(root)

        test("the scanner discovers the modules and their sources") {
            repository.modules.map { module -> module.path }.sorted() shouldContainExactly
                LektoArchitecture.policies.map { policy -> policy.path }.sorted()
            repository.sources.shouldNotBeEmpty()
        }

        test("the repository conforms to the module boundaries and naming conventions") {
            LektoArchitecture.check(repository) shouldBe emptyList()
        }
    })
