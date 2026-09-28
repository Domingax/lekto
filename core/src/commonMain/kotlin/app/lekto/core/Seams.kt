package app.lekto.core

import kotlin.random.Random
import kotlin.time.Clock

/**
 * The ambient, non-deterministic inputs the domain is *given* rather than
 * reaches for: the clock, the identifier generator and randomness.
 *
 * ADR-0003 stamps every record with `updatedAt`, ADR-0004 breaks timestamp ties,
 * and ADR-0006's property tests generate text — none of which may read a wall
 * clock or a global RNG directly, or a failure stops reproducing
 * (docs/research/testing-harness.md §7). Thread [Seams] through whatever needs
 * them: production wires [system], tests wire the deterministic implementations
 * in `app.lekto.testkit`.
 */
data class Seams(
    val clock: Clock,
    val ids: IdGenerator,
    val random: Random,
) {
    companion object {
        /** The production wiring: the system clock, UUIDs and the default RNG. */
        fun system(): Seams = Seams(
            clock = Clock.System,
            ids = UuidIdGenerator,
            random = Random.Default,
        )
    }
}
