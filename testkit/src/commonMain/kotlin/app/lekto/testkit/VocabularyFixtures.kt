package app.lekto.testkit

import app.lekto.core.vault.DeviceId
import app.lekto.core.vocabulary.VaultVocabulary
import app.lekto.core.vocabulary.Vocabulary

/**
 * A [Vocabulary] over an in-memory **Vault**, so a test can inspect it and start a
 * fresh controller over the same records — the vocabulary counterpart of
 * [InMemoryVaultStore] itself.
 *
 * Pass the same [vault] to a second call to simulate a restart. [deviceId] names
 * the writer, so a test can tell two devices' records apart.
 */
fun inMemoryVocabulary(
    vault: InMemoryVaultStore = InMemoryVaultStore(),
    deviceId: DeviceId = DeviceId("device-a"),
): Vocabulary = VaultVocabulary(vault, InMemoryTombstoneStore(), deterministicSeams(), deviceId)
