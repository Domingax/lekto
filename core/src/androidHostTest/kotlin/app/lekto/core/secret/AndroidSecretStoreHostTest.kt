package app.lekto.core.secret

import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.SecretStoreContract
import app.lekto.testkit.testVaultRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Android [SecretStore] on a **simulated Android runtime** (issue #24;
 * ADR-0021): the same [SecretStoreContract] the in-memory fake and the desktop
 * store pass, run here against the app-private file layout. The Android Keystore
 * cannot run on the host JVM, so the cryptographic half is a reversible fake —
 * the seam [SecretCipher] exists for exactly this — and the real [KeystoreCipher]
 * is proved to fail closed rather than crash where no Keystore is reachable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AndroidSecretStoreHostTest {

    @Test
    fun `the Android store satisfies the secret store contract`() {
        var index = 0
        SecretStoreContract { store(index++) }.cases().forEach { case -> case.body() }
    }

    @Test
    fun `a Keystore that cannot be reached is an honest message, not a crash`() {
        val store = AndroidSecretStore(File(filesDir(), "unreachable"), FailingCipher())

        assertEquals(
            SecretResult.Unavailable(SecretStore.UNAVAILABLE),
            store.put(API_KEY, "sk-live-0123456789"),
        )
    }

    @Test
    fun `a stored secret sits outside the vault and is absent from its export`() {
        val marker = "sk-live-android-isolation-0123456789"
        val vaultRoot = File(filesDir(), "vault")
        val vault = JsonVaultStore(JvmVaultFileSystem(vaultRoot))
        vault.put(testVaultRecord("vocabulary"))

        val secretsDirectory = File(filesDir(), "secrets")
        AndroidSecretStore(secretsDirectory, ReversibleCipher()).put(API_KEY, marker)

        val export = vault.exportBundle().decodeToString()

        assertFalse(export.contains(marker))
        assertFalse(secretsDirectory.canonicalPath.startsWith(vaultRoot.canonicalPath + File.separator))
    }

    /** A store over a fresh directory, so contract cases cannot leak state into one another. */
    private fun store(index: Int): AndroidSecretStore =
        AndroidSecretStore(File(filesDir(), "secrets-$index"), ReversibleCipher())

    private fun filesDir(): File = RuntimeEnvironment.getApplication().filesDir
}

/** A reversible stand-in for the Keystore cipher, so the file behaviour is provable on the host. */
private class ReversibleCipher : SecretCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext.reversedArray()

    override fun decrypt(blob: ByteArray): ByteArray = blob.reversedArray()
}

/** A cipher that fails the way the Keystore does where it is unreachable. */
private class FailingCipher : SecretCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray = error("AndroidKeyStore is not available")

    override fun decrypt(blob: ByteArray): ByteArray? = error("AndroidKeyStore is not available")
}

private const val API_KEY: String = "llm.api-key"
