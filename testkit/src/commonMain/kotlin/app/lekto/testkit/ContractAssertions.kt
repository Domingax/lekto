package app.lekto.testkit

/** One named behaviour a seam must exhibit, and how to run it. */
data class ContractCase(val name: String, val body: () -> Unit)

/** A contract case whose body may suspend, e.g. to collect a flow or await a target. */
data class SuspendContractCase(val name: String, val body: suspend () -> Unit)

/**
 * The assertion helpers every framework-free contract suite in `testkit` shares,
 * so `VaultStoreContract` and `DictionaryPackContract` state a violation the same
 * way and the wording lives once.
 */
internal const val CONTRACT_MISMATCH = "contract violation"

internal fun expectEquals(expected: Any?, actual: Any?, what: String) {
    if (expected != actual) {
        throw AssertionError("$CONTRACT_MISMATCH: $what: expected <$expected> but was <$actual>")
    }
}

internal fun expectTrue(condition: Boolean, what: String) {
    if (!condition) throw AssertionError("$CONTRACT_MISMATCH: $what")
}
