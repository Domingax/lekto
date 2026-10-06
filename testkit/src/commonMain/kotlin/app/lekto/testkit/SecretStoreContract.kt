package app.lekto.testkit

import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore

/**
 * The specification of the [SecretStore] seam, as executable cases (issue #24;
 * ADR-0021).
 *
 * The contract is framework-free on purpose, like [VaultStoreContract]: each
 * consumer registers [cases] with whatever runner its module uses. The in-memory
 * fake runs it in `core/commonTest`, the desktop store in `core/jvmTest`, and the
 * Android store in `core/androidHostTest`, so the three cannot silently differ
 * (docs/testing.md, "Test levels").
 *
 * Each case builds a fresh store from [newStore], so cases cannot leak state.
 */
class SecretStoreContract(private val newStore: () -> SecretStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unknown key reads as absent") { readsUnknownAsAbsent() },
        ContractCase("a stored secret reads back unchanged") { readsStoredSecret() },
        ContractCase("storing a secret replaces the previous value") { replacesSecret() },
        ContractCase("a deleted secret reads as absent") { deletesSecret() },
        ContractCase("deleting an unknown key is not an error") { deletesUnknown() },
        ContractCase("a secret with awkward characters round-trips") { roundTripsAwkwardSecret() },
    )

    private fun readsUnknownAsAbsent() {
        expectEquals(SecretResult.Absent, newStore().get("absent"), "an unknown key")
    }

    private fun readsStoredSecret() {
        val store = newStore()
        expectEquals(SecretResult.Stored, store.put("api-key", A_SECRET), "the write outcome")
        expectEquals(SecretResult.Found(A_SECRET), store.get("api-key"), "the secret read back")
    }

    private fun replacesSecret() {
        val store = newStore()
        store.put("api-key", "first")
        store.put("api-key", "second")
        expectEquals(SecretResult.Found("second"), store.get("api-key"), "the replaced secret")
    }

    private fun deletesSecret() {
        val store = newStore()
        store.put("api-key", A_SECRET)
        expectEquals(SecretResult.Deleted, store.delete("api-key"), "the delete outcome")
        expectEquals(SecretResult.Absent, store.get("api-key"), "a deleted secret")
    }

    private fun deletesUnknown() {
        expectEquals(SecretResult.Deleted, newStore().delete("never-stored"), "deleting an unknown key")
    }

    private fun roundTripsAwkwardSecret() {
        val store = newStore()
        store.put("api-key", AWKWARD_SECRET)
        expectEquals(SecretResult.Found(AWKWARD_SECRET), store.get("api-key"), "an awkward secret")
    }
}

/** A representative secret: a realistic key shape nothing else would collide with. */
private const val A_SECRET: String = "sk-live-0123456789abcdef"

/** Leading and trailing spaces, a tab, a newline, a bullet, CJK and an emoji, so encoding is exercised. */
private const val AWKWARD_SECRET: String = "  sk-\u2022\na b\tc \u4f60\u597d \ud83c\udf89 "
