# Secret storage is a platform seam; an API key never enters the vault

Bringing your own key (issues #24, #25) means an API key lives on the device, and
ADR-0002 forbids a server to hold it. It must never enter the **vault** (ADR-0005), an
export, or a log, and an input must be masked by default. Android and desktop back
this differently — the Android Keystore versus the operating system's keychain — so,
as with pronunciation (ADR-0019), the capability is a **platform seam**.

We decided on a `SecretStore` in `core`'s shared sources: a small get/put/delete
surface that reports an honest unavailable outcome rather than throwing into the
settings screen. Android backs it first-party in `core`'s `androidMain` with an
AES-256-GCM key held by the Android Keystore and the ciphertext in app-private
storage; desktop backs it in `core`'s `jvmMain` through **KSafe**, which drives the
macOS Keychain, Windows DPAPI and the Linux Secret Service with a fail-closed
software fallback. An in-memory fake lives in `testkit`, and the application wires the
platform store through `AppEnvironment`, as it does the other seams.

We rejected `androidx.security:security-crypto` (`EncryptedSharedPreferences`), the
obvious choice until 2025: its APIs were deprecated in 1.1.0 with no successor, and
the documented path is now the Keystore directly. We rejected storing keys in the
vault (ADR-0005 forbids it, and they would travel in every export), an unencrypted
fallback (it would make "secure storage" a lie), and hand-written JNA bindings for the
three desktop keychains (three native surfaces to own for a second-priority platform);
KSafe is permissively licensed and actively maintained, and the seam keeps it
replaceable. We also rejected the alternative libraries: KVault has no desktop target,
`multiplatform-settings` has no encryption, and the Microsoft credential library is
archived.

**Consequences**: the Android Keystore cannot run in the fast JVM lane, so the
platform store is proved through Robolectric (`core`'s Android host tests) and, for
what only a device proves, the nightly lane. On a desktop with no running Secret
Service, KSafe fails closed and `SecretStore` reports unavailable rather than
persisting a key in the clear. Because the web target is closed (ADR-0008), the
browser's no-keychain problem does not arise. KSafe's transitive dependencies must
clear the licence gate (ADR-0011) before the dependency lands. The seam is synchronous
for now; a store that must prompt for user authentication adds a method, not a module.
