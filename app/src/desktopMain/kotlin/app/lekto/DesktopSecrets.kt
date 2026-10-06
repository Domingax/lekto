package app.lekto

import app.lekto.core.secret.JvmSecretStore
import app.lekto.core.secret.SecretStore
import java.io.File

/**
 * The desktop secret store (issue #24; ADR-0021): the OS keychain through KSafe,
 * rooted beside the vault in the app-data directory. It is deliberately **not**
 * the vault: a secret stored here is absent from every vault export, and the
 * store reports itself unavailable where the OS has no reachable keychain rather
 * than falling back to a key in the clear.
 *
 * [root] is the test seam, as [desktopVaultStore]'s is; production never passes
 * one.
 */
fun desktopSecretStore(root: File = File(appDataDirectory("lekto"), "secrets")): SecretStore = JvmSecretStore(root)
