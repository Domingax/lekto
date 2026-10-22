# Lekto on the desktop

The desktop client is a first-class way to read, not a preview. It runs the same
reader, library and vocabulary as Android and keeps the same **Vault** format, so
a book and its words can move between the two by export and import.

## Get and install

Download the file for your computer from the
[latest release](https://github.com/Domingax/lekto/releases/latest):

- **Windows** — the `.msi` installer, or the `.exe` if you prefer a simple
  double-click. Both install the same application; the MSI is the one to use if
  you manage machines or want an upgrade to replace the previous version
  cleanly.
- **macOS** — the `.dmg` disk image. Open it, drag Lekto to **Applications**, and
  start it from there. Releases are provided for Apple Silicon (`macos-arm64`) and
  for Intel Macs (`macos-x64`); take the one your Mac needs.
- **Linux** — the `.AppImage`. It is one file, needs no installation and runs on
  any x86-64 distribution, Arch included:

  ```sh
  chmod +x lekto-<version>-linux-x64.AppImage
  ./lekto-<version>-linux-x64.AppImage
  ```

  If your system has no FUSE 2, run it with
  `./lekto-<version>-linux-x64.AppImage --appimage-extract-and-run`.

The desktop build is not signed with a paid operating-system certificate. Windows
SmartScreen or macOS Gatekeeper may therefore ask you to confirm the first
launch; the source is public and the build is reproducible from the release tag.

## Your data is the same Vault

The desktop client reads and writes exactly the Vault format Android uses. To move
your reading between devices deliberately, use **Settings → Vault export and
import**: export on one device, copy the bundle across, import it on the other.
Syncing through your own WebDAV server works on both as well; see
[Vault and sync](/vault-and-sync).

Secrets — your **API key** and your **Application password** — are kept in the
operating system's **Secret store** (the macOS Keychain, Windows DPAPI or the Linux
Secret Service), never in the Vault and never in an export. See
[LLM provider and privacy](/llm-provider).

## Pronunciation uses your system voices

There is no speech engine inside Lekto. On the desktop, **Listen** asks the
operating system's synthesizer to speak — `say` on macOS, `spd-say` or `espeak-ng`
on Linux, and the Windows speech API — using a voice in the book's language. If
that language has no installed voice, the panel says so instead of failing. Install
the voice in your system settings and try again. See
[Pronunciation](/pronunciation).

## Accessibility on the desktop

This limitation is real and is stated here rather than hidden. It comes from the
toolkit the desktop client is built with, not from a Lekto setting.

- **macOS** — a screen reader such as VoiceOver works with Lekto.
- **Windows** — support goes through the Java Access Bridge, which is **off by
  default**. A screen-reader user must enable it once on the machine: run
  `jabswitch /enable` from a JDK's `bin` directory, or put
  `assistive_technologies=com.sun.java.accessibility.AccessBridge` in
  `%USERPROFILE%\.accessibility.properties`. Only then is Lekto's interface
  exposed to Narrator, NVDA, JAWS or another screen reader. Lekto ships the module
  the bridge needs, so enabling it is all that is missing.
- **Linux** — there is **no accessibility support**. A screen reader cannot read
  Lekto's desktop interface on Linux. Use the Android client if you rely on one.

The Android client has full screen-reader support, so it is the recommended
client for anyone who needs one.
