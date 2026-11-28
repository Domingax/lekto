# Platforms

How the Android and desktop clients are built and packaged. The JVM/desktop
build needs no Android SDK; see [`development.md#prerequisites`](development.md#prerequisites).

## Android

The app's `minSdk` is **27** (Android 8.1), read from `android-minSdk` in
`gradle/libs.versions.toml`. It is the first level where
`SQLiteDatabase.openDatabase(File, OpenParams)` exists, so the Android
dictionary-pack reader needs no deprecation workaround.

```sh
./gradlew :app:assembleDebug             # build the debug APK
./gradlew :app:installDebug              # install on a running device/emulator
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### The release APK

```sh
./gradlew :app:assembleRelease -Plekto.android=true   # the APK a release ships
```

The release lane publishes it as `Lekto-<version>-android.apk`. Its `versionCode`
is derived from `lekto.version` rather than kept as a second number:
`major * 10000 + minor * 100 + patch`, so `1.0.0` is `10000`, `1.1.0` `10100`
and `1.1.1` `10101`. It rises with every published APK, because that is what lets
an installed copy be updated. A preview is named by its git tag, not by
`lekto.version` — jpackage rejects a version suffix — so a preview and its final
share a `versionCode`, which Android still installs as an update because the same
key signs both. `minor` and `patch` stay below 100; the build fails rather than
emit a colliding code.

The APK is signed with the project's release key, never a per-machine one: the
key must sign every release or an installed copy can never be updated again. The
release lane decodes the key from `ANDROID_KEYSTORE_BASE64` into `$RUNNER_TEMP`,
so it never enters the workspace, and passes its path in
`lekto.android.keystore`; the alias and the two passwords arrive as
`ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_PASSWORD`, so no
secret reaches a command line or a Gradle property. `apksigner verify
--print-certs` checks the file that is actually published, and `aapt dump
badging` checks it carries the version it claims.

A build without those inputs stays unsigned, which is what a fork and the `fast`
lane rely on; a build given the keystore but not the rest fails rather than
publishing an unsigned APK. To sign locally:

```sh
ANDROID_KEYSTORE_PASSWORD=… ANDROID_KEY_ALIAS=lekto ANDROID_KEY_PASSWORD=… \
  ./gradlew :app:assembleRelease -Plekto.android=true \
  -Plekto.android.keystore=/path/to/lekto-release.jks
```

A signed build writes `app/build/outputs/apk/release/app-release.apk`; an unsigned
one writes `app-release-unsigned.apk` beside it. R8 is off in this build: the
release APK is not minified or shrunk. A minified build needs its own tests before
it can ship.

## Desktop

```sh
./gradlew :app:run                        # run the desktop app
```

The desktop client is packaged for the three desktop systems:

- **Windows** — a `.msi` and a `.exe`, both built by jpackage. The MSI is the
  Windows Installer package: Add/Remove Programs, silent install and managed
  deployment, with `upgradeUuid` replacing the previous version. The EXE is the
  double-click installer.
- **macOS** — a `.dmg`, built by jpackage, for Apple Silicon and for Intel.
- **Linux** — a portable `.AppImage`. jpackage has no single-file Linux format
  that runs on every distribution, so the lane builds the jpackage application
  image and `tools/packaging/appimage.sh` wraps it: an AppDir with the
  application, icon, desktop entry and `AppRun`, assembled with the pinned
  `appimagetool`.

A distribution can only be built on its own operating system — jpackage does not
cross-compile — so each runner builds its own artefacts. On Windows and macOS:

```sh
./gradlew :app:packageDistributionForCurrentOS -Plekto.android=false
```

jpackage writes the MSI and EXE under
`app/build/compose/binaries/main/{msi,exe}` and the DMG under `dmg/`. On Linux:

```sh
./gradlew :app:createDistributable -Plekto.android=false
tools/packaging/appimage.sh app/build/compose/binaries/main/app/Lekto \
  app/src/desktopMain/resources/app-icon/app-icon.png dist/lekto-<version>-linux-x64.AppImage
```

The Android variant is switched off so the lane needs no Android SDK and is the
same on every runner. The release lane (`.github/workflows/release.yml`) runs
these on Linux, Windows, Apple Silicon macOS and Intel macOS, uploads five desktop
files plus the signed Android APK, and attaches them to the release when the
commit carries a `v*` tag. The release body is the changelog link: a GitHub
compare between the previous `v*` tag and this one. A tag with a pre-release
suffix (`v1.0.0-preview`) publishes a pre-release that never becomes the
`/releases/latest` download; a manual run builds the installers without
publishing. The Windows installers need WiX, which the runner carries; the Linux
lane downloads `appimagetool` at the pinned version and checks its digest.

The application version lives in `gradle.properties` (`lekto.version`), read by
Android's `versionName` and the installers' `packageVersion`, so one release's
APK and installers cannot claim different versions. Its first number is at least
1: jpackage's macOS bundler rejects an `app-version` that starts at zero. The
packaging names its identity explicitly: bundle id `app.lekto` and dock name
`Lekto` on macOS, and a stable `upgradeUuid` on Windows so an upgrade replaces the
previous install instead of sitting beside it. The runtime image carries the
modules `:app:suggestRuntimeModules` reports — `java.sql` for sqlite-jdbc,
`java.net.http` for the desktop WebDAV transport, `jdk.unsupported` for JNA — plus
`jdk.accessibility`, which makes the Java Access Bridge reachable on Windows.

The desktop's accessibility is limited by Compose Desktop, not by a Lekto
setting: macOS is fully supported, Windows works through the Java Access Bridge
(off by default; the user enables it with `jabswitch /enable`), and Linux is
unsupported. ADR-0007 records the limitation; the end-user guide states it where
a downloader meets it ([`guide/desktop.md`](../guide/desktop.md) and the download
section of the home page).

## Icon pipeline

`tools/icons/fetch.sh` regenerates the icon set committed under
`app/src/commonMain/kotlin/app/lekto/ui/icons/`. It fetches the fourteen SVGs at
the pinned Lucide tag, runs the pinned Valkyrie CLI with its zip digest checked,
and writes the `ImageVector` Kotlin. Nothing is generated at build time and the
app declares no icon dependency: the output is committed, and `LektoIconsTest`
pins its geometry in `./gradlew check`. The licences are in
`THIRD-PARTY-NOTICES.md`; the design rule is [`design.md`](design.md) §5, and the
reasoning is [`research/icones-compose.md`](research/icones-compose.md).

`tools/icons/mark.sh` regenerates the launcher mark from the SVGs in
`tools/icons/mark/`, which are its only source of truth: `mark.svg` is the
letter, with its paper rim and the four Mastery level colours; `letter.svg` is
the same letter in one ink for Android 13's monochrome layer. It converts them
into the adaptive icon's two layers under `app/src/androidMain/res/` — the launch
screen reuses the foreground, because the rim carries the mark onto the Dark
paper and the system masks the same third of both — writes
`guide/public/favicon.svg`, and renders the desktop rasters into
`app/src/desktopMain/resources/app-icon/`: a PNG for the Linux package and the
window icon, a multi-resolution ICO for Windows and an ICNS for macOS. The
conversion is `tools/icons/mark/Convert.java`, a single-file JDK program; the
renderer is `app.lekto.tools.RenderAppIcon`, run by
`./gradlew :app:renderAppIcon`, which draws with the Skia already on the desktop
classpath.

The pipeline adds no dependency, and every output is committed:
`LauncherIconTest` pins the manifest-and-resource wiring, `AppIconTest`'s golden
pins the geometry, and a raster is regenerated by hand rather than compared in CI.
The desktop distribution's `nativeDistributions` consumes the three rasters: the
Linux AppImage and window icon use the PNG, Windows the ICO and macOS the ICNS.
