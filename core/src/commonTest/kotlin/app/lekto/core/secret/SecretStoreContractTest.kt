package app.lekto.core.secret

import app.lekto.testkit.InMemorySecretStore
import app.lekto.testkit.SecretStoreContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [SecretStoreContract] run against the in-memory fake: the seam's
 * specification, proved without a Keystore or a keychain (issue #24; ADR-0021;
 * docs/testing.md, "Test levels"). The desktop store runs the same cases in
 * `jvmTest` and the Android store in `androidHostTest`.
 */
class SecretStoreContractTest :
    FunSpec({

        SecretStoreContract { InMemorySecretStore() }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
