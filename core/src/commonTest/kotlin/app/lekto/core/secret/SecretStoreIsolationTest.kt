package app.lekto.core.secret

import app.lekto.testkit.InMemorySecretStore
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * The secret store keeps secrets out of the **Vault** (issue #24, acceptance
 * criteria 2 and 3; ADR-0005, ADR-0021): a value written through the secret seam
 * appears nowhere in a vault record, a vault listing, or a vault export, and an
 * unavailable outcome never echoes the value it could not store.
 */
class SecretStoreIsolationTest :
    FunSpec({

        test("a stored secret is absent from the vault and from its export") {
            val vault = InMemoryVaultStore()
            val secrets = InMemorySecretStore()
            val marker = "sk-live-isolation-marker-0123456789"

            secrets.put(API_KEY, marker) shouldBe SecretResult.Stored
            vault.put(testVaultRecord("vocabulary"))

            val export = vault.exportBundle().decodeToString()

            export shouldNotContain marker
            vault.all().toString() shouldNotContain marker
        }

        test("an unavailable store reports a constant message carrying no secret") {
            val store = InMemorySecretStore(available = false)
            val marker = "sk-live-never-in-a-message"

            store.put(API_KEY, marker) shouldBe SecretResult.Unavailable(SecretStore.UNAVAILABLE)
            store.get(API_KEY).toString() shouldNotContain marker
            store.delete(API_KEY).toString() shouldNotContain marker
        }
    })

private const val API_KEY: String = "llm.api-key"
