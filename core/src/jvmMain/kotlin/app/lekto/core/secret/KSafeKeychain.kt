package app.lekto.core.secret

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeConfig
import eu.anifantakis.lib.ksafe.KSafeProtectionLevel
import java.io.File

/**
 * The desktop [SecretKeychain] backed by KSafe (ADR-0021): KSafe encrypts each
 * value with AES-256-GCM under a key it hands to the OS secret store — Windows
 * DPAPI, the macOS login Keychain, or the Linux Secret Service.
 *
 * [isOsBacked] is the fail-closed gate. KSafe reports `SOFTWARE` when no OS
 * store is reachable, and keeps working by writing the key in the clear beside
 * the ciphertext; that is weaker than the ADR allows, so [JvmSecretStore]
 * reports the store unavailable instead of accepting it (ADR-0021, "fails
 * closed ... rather than persisting a key in the clear"). An OS store that is
 * present but unreachable reports non-operational, which the gate also rejects.
 */
internal class KSafeKeychain(baseDir: File) : SecretKeychain {

    private val ksafe: KSafe = KSafe(
        fileName = STORE_NAME,
        baseDir = baseDir,
        config = KSafeConfig(appNamespace = APP_NAMESPACE),
    )

    override val isOsBacked: Boolean
        get() = ksafe.protectionInfo.let { info ->
            info.isEncryptionOperational && info.effectiveLevel != KSafeProtectionLevel.SOFTWARE
        }

    override fun get(key: String): String? = ksafe.getDirect<String?>(key, null)

    override fun put(key: String, value: String) {
        ksafe.putDirect(key, value)
    }

    override fun delete(key: String) {
        ksafe.deleteDirect(key)
    }

    private companion object {
        /** The store file under the secrets directory; KSafe requires a lowercase identifier. */
        const val STORE_NAME: String = "secrets"

        /** Namespaces the aliases KSafe files in the machine-wide OS stores (ADR-0021). */
        const val APP_NAMESPACE: String = "app.lekto"
    }
}
