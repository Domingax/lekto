package app.lekto.core

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The seam that mints identifiers for records and other domain objects.
 *
 * Every record carries an `id` (ADR-0003), so identifier generation is a source
 * of non-determinism. It is injected rather than reached for, so a test can hand
 * in a generator whose output it controls and a failure always reproduces
 * (docs/research/testing-harness.md §7).
 *
 * Production uses [UuidIdGenerator]; tests use `SequentialIdGenerator` in the
 * shared testkit.
 */
fun interface IdGenerator {
    fun newId(): String
}

/** The production [IdGenerator]: a random UUID, as ADR-0003's records expect. */
@OptIn(ExperimentalUuidApi::class)
object UuidIdGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}
