package app.lekto.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The **Sample** is a **Derived asset**, not a **Vault** record (issue #297;
 * ADR-0005): its catalogue ships with the app as a build output, the domain model
 * names no vault store, and the export never carries one. A sample that leaked
 * into the vault would travel to a sync target as if the reader had authored it.
 *
 * The catalogue's own contents are decoded by `CommittedSampleCatalogTest` in
 * `core`, through the reader the app uses; this suite keeps the structural rule
 * and the producer/consumer handshake.
 */
class SampleAssetsAreDerivedTest :
    FunSpec({

        val root = RepositoryScanner.repositoryRoot()

        test("the sample model names no vault store") {
            val sources = RepositoryScanner.scan(root).sources
                .filter { source -> source.path.contains("core/src/commonMain/kotlin/app/lekto/core/sample/") }

            sources.shouldNotBeEmpty()
            sources.flatMap { source -> source.imports }
                .filter { import -> import.startsWith("app.lekto.core.vault") }
                .shouldBeEmpty()
        }

        test("the vault's export never names a sample") {
            VAULT_EXPORT.forEach { path ->
                val text = File(root, path).readText().lowercase()
                check("sample" !in text) { "$path must never carry a sample into an export" }
            }
        }

        test("the sample catalogue lives under resources, never under a vault path") {
            File(root, CATALOGUE).isFile shouldBe true
            check("vault" !in CATALOGUE) { "the sample catalogue must not live under a vault path" }
        }

        test("the producer and the consumer agree on the sample format version") {
            val producer = File(root, PRODUCER_FORMAT).readText()
            val produced = requireNotNull(Regex("""const val VERSION: Int = (\d+)""").find(producer)) {
                "no 'const val VERSION: Int = …' in $PRODUCER_FORMAT"
            }.groupValues[1].toInt()

            produced shouldBe consumedFormatVersion(root)
        }
    })

private fun consumedFormatVersion(root: File): Int {
    val consumer = File(root, CONSUMER_FORMAT).readText()
    return requireNotNull(Regex("""const val FORMAT_VERSION: Int = (\d+)""").find(consumer)) {
        "no 'const val FORMAT_VERSION: Int = …' in $CONSUMER_FORMAT"
    }.groupValues[1].toInt()
}

private const val CATALOGUE: String = "core/src/commonMain/resources/samples/catalogue.json"

private const val PRODUCER_FORMAT: String =
    "tools/dictionaries/src/main/kotlin/app/lekto/tools/dictionaries/SampleContent.kt"

private const val CONSUMER_FORMAT: String =
    "core/src/commonMain/kotlin/app/lekto/core/sample/SampleCatalog.kt"

private val VAULT_EXPORT = listOf(
    "core/src/commonMain/kotlin/app/lekto/core/vault/VaultBundle.kt",
    "core/src/commonMain/kotlin/app/lekto/core/vault/VaultManifest.kt",
)
