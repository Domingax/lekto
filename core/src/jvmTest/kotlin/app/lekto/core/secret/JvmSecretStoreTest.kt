package app.lekto.core.secret

import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.SecretStoreContract
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/**
 * The desktop [SecretStore] (issue #24; ADR-0021): it maps the OS keychain
 * behind [SecretKeychain] to honest [SecretResult]s. The same
 * [SecretStoreContract] the in-memory fake passes runs here against a fake
 * keychain, so the desktop store cannot differ from the seam's specification;
 * the extra cases prove the fail-closed gate, the no-secret-in-an-error
 * guarantee, and that a secret never reaches a vault export.
 *
 * The real keychain is never touched: [SecretKeychain] is injected, as
 * `SpeechHost` is for the desktop pronouncer.
 */
class JvmSecretStoreTest :
    FunSpec({

        SecretStoreContract { JvmSecretStore(FakeKeychain()) }.cases().forEach { case ->
            test(case.name) { case.body() }
        }

        test("a store with no OS keychain fails closed instead of writing a key in the clear") {
            val keychain = FakeKeychain(isOsBacked = false)
            val store = JvmSecretStore(keychain)

            store.put(API_KEY, A_SECRET) shouldBe SecretResult.Unavailable(SecretStore.UNAVAILABLE)
            store.get(API_KEY) shouldBe SecretResult.Unavailable(SecretStore.UNAVAILABLE)
            store.delete(API_KEY) shouldBe SecretResult.Unavailable(SecretStore.UNAVAILABLE)
            keychain.writes shouldBe emptyList()
        }

        test("a keychain that throws is an honest message, not a crash") {
            val keychain = FakeKeychain(failure = IllegalStateException("keychain is locked"))

            JvmSecretStore(keychain).put(API_KEY, A_SECRET) shouldBe
                SecretResult.Unavailable(SecretStore.UNAVAILABLE)
        }

        test("an error message never carries the secret that failed to store") {
            val keychain = FakeKeychain(failure = IllegalStateException("could not store $A_SECRET"))

            val result = JvmSecretStore(keychain).put(API_KEY, A_SECRET)

            result.toString().contains(A_SECRET) shouldBe false
        }

        test("a stored secret is absent from a vault export") {
            val root = Files.createTempDirectory("lekto-secret-isolation").toFile()
            try {
                val vault = JsonVaultStore(JvmVaultFileSystem(root))
                val store = JvmSecretStore(FakeKeychain())
                val marker = "sk-live-desktop-isolation-0123456789"

                store.put(API_KEY, marker)
                vault.put(testVaultRecord("vocabulary"))

                vault.exportBundle().decodeToString().contains(marker) shouldBe false
            } finally {
                root.deleteRecursively()
            }
        }
    })

/** A scripted [SecretKeychain]: an in-memory keychain that can be turned off or made to throw. */
private class FakeKeychain(override val isOsBacked: Boolean = true, private val failure: Exception? = null) :
    SecretKeychain {

    private val values: MutableMap<String, String> = mutableMapOf()

    /** Every `(key, value)` written, so a fail-closed test can prove nothing was stored. */
    val writes: MutableList<Pair<String, String>> = mutableListOf()

    override fun get(key: String): String? {
        failure?.let { throw it }
        return values[key]
    }

    override fun put(key: String, value: String) {
        failure?.let { throw it }
        values[key] = value
        writes += key to value
    }

    override fun delete(key: String) {
        failure?.let { throw it }
        values.remove(key)
    }
}

private const val API_KEY: String = "llm.api-key"
private const val A_SECRET: String = "sk-live-0123456789abcdef"
