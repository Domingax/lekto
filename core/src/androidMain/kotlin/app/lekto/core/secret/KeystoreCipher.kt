package app.lekto.core.secret

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Android Keystore-backed [SecretCipher] (issue #24; ADR-0021): an
 * AES-256-GCM key the Keystore never releases — only the ciphertext leaves — so
 * the key is unrecoverable from the app's storage. The first use mints the key
 * under [KEY_ALIAS]; every later use reads it back.
 *
 * The GCM IV is random per write and prefixed to the ciphertext, so the stored
 * blob is `iv || ciphertext-and-tag`. A blob this cipher cannot authenticate —
 * another device's key, a truncated file — decrypts to null, which the store
 * reports as absent rather than as an error.
 */
internal class KeystoreCipher : SecretCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(blob: ByteArray): ByteArray? {
        if (blob.size <= IV_LENGTH) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, blob.copyOf(IV_LENGTH)))
        return try {
            cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH)
        } catch (_: AEADBadTagException) {
            null
        }
    }

    /** The Keystore's AES key, minting it on first use. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
        const val KEY_ALIAS: String = "lekto.secrets"
        const val TRANSFORMATION: String = "AES/GCM/NoPadding"

        /** The GCM IV the Keystore uses; GCM's standard 96-bit length. */
        const val IV_LENGTH: Int = 12

        /** The GCM authentication tag length, in bits. */
        const val TAG_BITS: Int = 128

        const val KEY_SIZE_BITS: Int = 256
    }
}
