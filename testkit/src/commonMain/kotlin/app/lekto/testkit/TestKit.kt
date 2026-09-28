package app.lekto.testkit

/**
 * The shared test-support library.
 *
 * Every seam the domain injects — the clock, identifier generation, randomness,
 * and the storage and sync seams as they land — has a deterministic, in-memory
 * implementation here, so consumers exercise real behaviour rather than mock
 * call sequences. Contract suites that every implementation of a seam must pass
 * live here too, because a KMP `commonTest` source set cannot be published and
 * shared any other way.
 */
object TestKit {
    const val NAME: String = "lekto-testkit"
}
