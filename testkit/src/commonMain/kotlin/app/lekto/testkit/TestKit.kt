package app.lekto.testkit

/**
 * Entry point for the shared test-support library.
 *
 * Contract suites (`SyncTarget`, `VaultStore`) and an in-memory fake for every
 * seam live here so that consumers exercise real behaviour rather than mock
 * call sequences. The seams themselves arrive with the harness (ticket #5).
 */
object TestKit {
    const val NAME: String = "lekto-testkit"
}
