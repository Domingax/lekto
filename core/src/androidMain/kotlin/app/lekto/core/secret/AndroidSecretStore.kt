package app.lekto.core.secret

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * The Android [SecretStore] (issue #24; ADR-0021): an AES-256-GCM key held by the
 * Android Keystore, with the ciphertext in app-private storage. The store is
 * first-party — `androidx.security:security-crypto` (`EncryptedSharedPreferences`)
 * was deprecated with no successor and is not used (ADR-0021).
 *
 * The platform cannot run in the fast JVM lane, so [cipher] is the seam a
 * Robolectric host test injects; production uses [KeystoreCipher]. Every
 * operation reports an honest [SecretResult] rather than throwing, and the
 * message is always the constant [SecretStore.UNAVAILABLE], so a secret cannot
 * appear in an error (issue #24, acceptance criterion 3).
 */
class AndroidSecretStore internal constructor(private val directory: File, private val cipher: SecretCipher) :
    SecretStore {

    /** The production store: the Keystore cipher, under `filesDir/secrets`. */
    constructor(context: Context) : this(File(context.filesDir, SECRETS_DIRECTORY), KeystoreCipher())

    override fun get(key: String): SecretResult = secretResultOf {
        val file = fileFor(key)
        when {
            !file.isFile -> SecretResult.Absent
            else -> cipher.decrypt(file.readBytes())?.decodeToString()?.let(SecretResult::Found) ?: SecretResult.Absent
        }
    }

    override fun put(key: String, value: String): SecretResult = secretResultOf {
        directory.mkdirs()
        val file = fileFor(key)
        // Write a sibling then rename, so a crash cannot leave a half-written secret.
        val temporary = File(directory, file.name + TEMP_SUFFIX)
        temporary.writeBytes(cipher.encrypt(value.encodeToByteArray()))
        if (!temporary.renameTo(file)) {
            file.writeBytes(temporary.readBytes())
            temporary.delete()
        }
        SecretResult.Stored
    }

    override fun delete(key: String): SecretResult = secretResultOf {
        fileFor(key).delete()
        SecretResult.Deleted
    }

    /** One file per key, named by a hash so an arbitrary key cannot escape [directory]. */
    private fun fileFor(key: String): File = File(directory, keyFileName(key))

    private companion object {
        /** App-private subdirectory of `filesDir`; never the vault, never exported (ADR-0005). */
        const val SECRETS_DIRECTORY: String = "secrets"

        const val TEMP_SUFFIX: String = ".tmp"
    }
}

/**
 * The cryptographic half of [AndroidSecretStore]: it encrypts and decrypts the
 * stored bytes. The Android Keystore cannot run on the host JVM, so it is a seam
 * a Robolectric test replaces with a reversible fake to prove the store's file
 * layout and outcomes.
 */
internal interface SecretCipher {

    /** Encrypts [plaintext]; the returned blob carries whatever [decrypt] needs (IV and tag). */
    fun encrypt(plaintext: ByteArray): ByteArray

    /** Decrypts [blob], or null when it was not written by this cipher. */
    fun decrypt(blob: ByteArray): ByteArray?
}

/** The app-private file name for [key]: a SHA-256 hex digest, so no key text reaches a path. */
internal fun keyFileName(key: String): String = MessageDigest.getInstance("SHA-256")
    .digest(key.encodeToByteArray())
    .joinToString(separator = "") { byte ->
        (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(PAIR_WIDTH, '0')
    } + ".bin"

/** A byte as an unsigned 0–255 value before it is rendered as two hex digits. */
private const val BYTE_MASK: Int = 0xFF

private const val HEX_RADIX: Int = 16

/** Two hex digits per byte. */
private const val PAIR_WIDTH: Int = 2
