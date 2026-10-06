package app.lekto.core.secret

import java.io.File

/**
 * The desktop [SecretStore] (issue #24; ADR-0021): the operating system's
 * keychain through KSafe — Windows DPAPI, the macOS login Keychain, or the Linux
 * Secret Service. Android backs the seam with its own Keystore code, so this
 * implementation is desktop-only (`jvmMain`, not `jvmSharedMain`).
 *
 * A store the OS does not back — KSafe's software fallback, which writes the key
 * in the clear beside the ciphertext — is reported [SecretResult.Unavailable]
 * rather than accepted, so "secure storage" is never a lie. [keychain] is the
 * seam a test injects; production uses the [KSafeKeychain] behind
 * [JvmSecretStore]'s `baseDir` constructor.
 */
class JvmSecretStore internal constructor(private val keychain: SecretKeychain) : SecretStore {

    /** The production store: the OS keychain through KSafe, rooted under [baseDir]. */
    constructor(baseDir: File) : this(KSafeKeychain(baseDir))

    override fun get(key: String): SecretResult = guarded {
        keychain.get(key)?.let(SecretResult::Found) ?: SecretResult.Absent
    }

    override fun put(key: String, value: String): SecretResult = guarded {
        keychain.put(key, value)
        SecretResult.Stored
    }

    override fun delete(key: String): SecretResult = guarded {
        keychain.delete(key)
        SecretResult.Deleted
    }

    /**
     * Runs [operation] against the OS keychain, or reports the store unavailable
     * when no OS keychain backs it. [secretResultOf] maps a thrown failure the
     * same way and never echoes its message, so a secret cannot appear in an
     * error (issue #24, acceptance criterion 3).
     */
    private fun guarded(operation: () -> SecretResult): SecretResult = secretResultOf {
        if (keychain.isOsBacked) operation() else SecretResult.Unavailable(SecretStore.UNAVAILABLE)
    }
}

/**
 * The OS keychain behind [JvmSecretStore]: a small string key/value surface that
 * reports whether the OS's own secret store is holding the key.
 *
 * It is `internal` because it is the desktop engine's implementation detail —
 * the application sees only [SecretStore] — and because `jvmTest` injects a fake
 * to prove the outcome mapping without touching a developer's keychain, as it
 * does with `SpeechHost`.
 */
internal interface SecretKeychain {

    /** Whether the OS keychain — not a software fallback — is holding the key. */
    val isOsBacked: Boolean

    /** The value stored under [key], or null when none is. */
    fun get(key: String): String?

    /** Stores [value] under [key]. */
    fun put(key: String, value: String)

    /** Removes the value stored under [key]; removing an absent key is not an error. */
    fun delete(key: String)
}
