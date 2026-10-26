#!/usr/bin/env bash
#
# Build a portable AppImage from the jpackage application image (issue #29).
#
# jpackage produces a directory tree, not an AppImage, so this script turns that
# tree into the single-file format: an AppDir with the application, its icon, a
# desktop entry and an `AppRun` that starts the bundled launcher. The result runs
# on any x86-64 Linux distribution without installation, which is why the
# release lane ships it instead of a distribution package.
#
# Requirements: bash, `appimagetool` on PATH or in APPIMAGETOOL (version and
# digest pinned by the release workflow), and the JDK that built the image.
#
# Usage: tools/packaging/appimage.sh <app-image-dir> <icon.png> <output.AppImage>

set -euo pipefail

if [ "$#" -ne 3 ]; then
    echo "usage: $0 <app-image-dir> <icon.png> <output.AppImage>" >&2
    exit 2
fi

application="$1"
icon="$2"
output="$3"
appimagetool="${APPIMAGETOOL:-appimagetool}"

for input in "$application" "$icon"; do
    if [ ! -e "$input" ]; then
        echo "missing input: $input" >&2
        exit 1
    fi
done

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
appdir="$work/Lekto.AppDir"

mkdir -p "$appdir/usr"
# The launcher resolves `lib/` beside its own `bin/`, so the whole image keeps
# its shape under `usr/`.
cp -a "$application" "$appdir/usr/lekto"
cp "$icon" "$appdir/lekto.png"

cat > "$appdir/lekto.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=Lekto
Comment=Read your own books. Learn the words.
Exec=lekto
Icon=lekto
Categories=Education;
Terminal=false
DESKTOP

# `readlink -f` resolves the AppImage's mount point, so the launcher runs from
# wherever the reader saved the file.
cat > "$appdir/AppRun" <<'APPRUN'
#!/bin/sh
set -eu
here="$(dirname "$(readlink -f "$0")")"
exec "$here/usr/lekto/bin/Lekto" "$@"
APPRUN
chmod +x "$appdir/AppRun"

# The application is x86-64 for now; the registry is a fixed address, not a
# network call made at run time.
ARCH=x86_64 "$appimagetool" --no-appstream "$appdir" "$output"
echo "wrote $output"
