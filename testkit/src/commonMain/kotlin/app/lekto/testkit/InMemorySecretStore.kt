package app.lekto.testkit

import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore

/**
 * An in-memory [SecretStore] for tests (issue #24; ADR-0021): a map behind the
 * seam, so a consumer's logic runs without a Keystore, a keychain or an Android
 * `Context`. [available] can be turned off to prove a consumer handles an
 * unavailable store without a device.
 */
class InMemorySecretStore(
    /** Whether the store behaves as if the platform's secure storage is reachable. */
    var available: Boolean = true,
) : SecretStore {

    private val values: MutableMap<String, String> = mutableMapOf()

    override fun get(key: String): SecretResult {
        if (!available) return SecretResult.Unavailable(SecretStore.UNAVAILABLE)
        return values[key]?.let(SecretResult::Found) ?: SecretResult.Absent
    }

    override fun put(key: String, value: String): SecretResult {
        if (!available) return SecretResult.Unavailable(SecretStore.UNAVAILABLE)
        values[key] = value
        return SecretResult.Stored
    }

    override fun delete(key: String): SecretResult {
        if (!available) return SecretResult.Unavailable(SecretStore.UNAVAILABLE)
        values.remove(key)
        return SecretResult.Deleted
    }
}
