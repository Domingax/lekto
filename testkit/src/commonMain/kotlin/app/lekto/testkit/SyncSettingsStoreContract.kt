package app.lekto.testkit

import app.lekto.core.sync.SyncSettings
import app.lekto.core.sync.SyncSettingsStore

/**
 * The specification of the [SyncSettingsStore] seam, as executable cases (issue
 * #28). Framework-free like [LlmSettingsStoreContract], so the in-memory fake
 * runs it in `core/commonTest` and the JSON document store runs the same cases
 * over an in-memory filesystem there and over a real directory in `core/jvmTest`
 * — the two implementations cannot silently differ (docs/testing.md, "Test
 * levels"). Each case builds a fresh store from [newStore], so cases cannot leak
 * state.
 */
class SyncSettingsStoreContract(private val newStore: () -> SyncSettingsStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unconfigured store loads the defaults") { loadsDefault() },
        ContractCase("a saved configuration round-trips") { roundTrips() },
        ContractCase("a later save replaces the previous configuration") { replaces() },
        ContractCase("disabling sync round-trips") { roundTripsDisabled() },
    )

    private fun loadsDefault() {
        expectEquals(SyncSettings.DEFAULT, newStore().load(), "an unconfigured store")
    }

    private fun roundTrips() {
        val store = newStore()
        val settings = SyncSettings(serverUrl = "https://cloud.example.test/remote.php/dav", username = "reader")
        store.save(settings)
        expectEquals(settings, store.load(), "a saved configuration")
    }

    private fun replaces() {
        val store = newStore()
        store.save(SyncSettings(serverUrl = "https://first.test/dav", username = "first"))
        val second = SyncSettings(serverUrl = "https://second.test/dav", username = "second", enabled = true)
        store.save(second)
        expectEquals(second, store.load(), "a replaced configuration")
    }

    private fun roundTripsDisabled() {
        val store = newStore()
        store.save(SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = true))
        store.save(SyncSettings.DEFAULT)
        expectEquals(SyncSettings.DEFAULT, store.load(), "a disconnected store")
    }
}
