# Lekto for the desktop

Installers for the desktop client, whose reader, library and vocabulary are the
same as Android and whose **Vault** is the same format: a Vault moves between
them by export and import.

- **Windows** — `Lekto-{{VERSION}}-windows-x64.msi` or
  `Lekto-{{VERSION}}-windows-x64.exe`: the MSI is the managed Windows Installer
  package, the EXE is the double-click installer.
- **macOS, Apple Silicon** — `Lekto-{{VERSION}}-macos-arm64.dmg`
- **macOS, Intel** — `Lekto-{{VERSION}}-macos-x64.dmg`
- **Linux** — `lekto-{{VERSION}}-linux-x64.AppImage`: one portable file, no
  installation, any x86-64 distribution.

## Accessibility on the desktop

The desktop client's screen-reader support is limited by the toolkit it is built
with. This is stated here because it is a reason to choose a client, not a
footnote:

- **macOS** — a screen reader such as VoiceOver works with Lekto.
- **Windows** — works through the Java Access Bridge, which is off by default. A
  screen-reader user enables it once with `jabswitch /enable` (from a JDK's `bin`
  directory), or by writing
  `assistive_technologies=com.sun.java.accessibility.AccessBridge` into
  `%USERPROFILE%\.accessibility.properties`. Lekto ships the module the bridge
  needs.
- **Linux** — there is **no accessibility support**. A screen reader cannot read
  Lekto's desktop interface on Linux; use the Android client if you rely on one.

The full statement, and the installation steps, are in
[the desktop page of the guide](https://github.com/{{REPOSITORY}}/blob/main/guide/desktop.md).
