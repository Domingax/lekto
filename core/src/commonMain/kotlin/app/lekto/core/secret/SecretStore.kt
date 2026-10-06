package app.lekto.core.secret

/**
 * The honest outcome of a [SecretStore] operation (issue #24; ADR-0021).
 *
 * A read either finds a value or finds none; a write or a delete either
 * succeeds or the platform's secure storage cannot be reached. The last case is
 * a first-class outcome, not an exception into the settings screen — and it
 * carries no secret, so it is always safe to show.
 */
sealed interface SecretResult {

    /** A read found the [value] stored under its key. */
    data class Found(val value: String) : SecretResult

    /** A read found nothing stored under its key. */
    data object Absent : SecretResult

    /** A write stored the value. */
    data object Stored : SecretResult

    /** A delete removed the value, or it was already gone. */
    data object Deleted : SecretResult

    /**
     * The platform's secure storage could not be reached, so nothing was read,
     * stored or removed. [message] is safe to show and never contains a secret.
     */
    data class Unavailable(val message: String) : SecretResult
}

/**
 * The secret-storage seam (issue #24; ADR-0021): a small get/put/delete surface
 * for values that must never enter the **Vault** (ADR-0005), an export, or a log
 * — an API key, say.
 *
 * Android backs it with the Android Keystore and the ciphertext in app-private
 * storage; desktop with the OS keychain (macOS Keychain, Windows DPAPI, Linux
 * Secret Service) through KSafe. An in-memory fake lives in `testkit`, and the
 * application wires the platform store through `AppEnvironment`.
 *
 * Every operation reports an honest [SecretResult] rather than throwing. A
 * device with no usable secure storage — a desktop with no reachable keyring,
 * say — is [SecretResult.Unavailable], never a fallback that writes a key in the
 * clear. The seam is synchronous for now; a store that must prompt for user
 * authentication adds a method, not a module.
 */
interface SecretStore {

    /** Reads the secret stored under [key]. */
    fun get(key: String): SecretResult

    /** Stores [value] under [key], replacing any previous value. */
    fun put(key: String, value: String): SecretResult

    /** Removes the secret stored under [key]; deleting an absent key is not an error. */
    fun delete(key: String): SecretResult

    companion object {
        /**
         * Shown when no usable secure storage is available on this device. It is
         * a constant: an unavailable outcome never interpolates the value, so a
         * secret cannot appear in a message.
         */
        const val UNAVAILABLE: String = "Secure storage isn't available on this device."
    }
}

/**
 * Runs [operation], reporting [SecretStore.UNAVAILABLE] on any failure of the
 * platform's secure storage. The Android and desktop stores share it so they map
 * a platform failure the same way, and a thrown exception — whose message might
 * carry a secret — is never echoed (issue #24, acceptance criterion 3).
 */
internal fun secretResultOf(operation: () -> SecretResult): SecretResult = try {
    operation()
} catch (_: Exception) {
    SecretResult.Unavailable(SecretStore.UNAVAILABLE)
}
