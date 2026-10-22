#!/usr/bin/env bash
#
# Regenerate the desktop application's installer icons (issue #29).
#
# The brand mark has one source: the guide's `favicon.svg`. jpackage asks each
# operating system for its own container — a PNG for Linux, an ICO for Windows,
# an ICNS for macOS — so this script renders that single SVG into the three and
# commits them beside the app. Re-run it when the mark changes; never edit a
# generated icon by hand.
#
# Requirements: bash, rsvg-convert (librsvg), ImageMagick (`convert`) and
# python3 (to assemble the ICNS container, which has no packaged tool here).
#
# Usage: tools/icons/app-icons.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SOURCE="$ROOT/guide/public/favicon.svg"
OUT="$ROOT/app/icons"

for tool in rsvg-convert convert python3; do
    command -v "$tool" >/dev/null || {
        echo "missing $tool: see the requirements above" >&2
        exit 1
    }
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

render() {
    rsvg-convert --width "$1" --height "$1" "$SOURCE" --output "$2"
}

# Linux: one 512px PNG. jpackage derives the freedesktop icon sizes from it.
render 512 "$OUT/lekto.png"

# Windows: a multi-resolution ICO, so Explorer, the taskbar and the installer
# each pick a crisp size.
for size in 16 24 32 48 64 128 256; do
    render "$size" "$WORK/lekto-$size.png"
done
convert "$WORK/lekto-16.png" "$WORK/lekto-24.png" "$WORK/lekto-32.png" \
    "$WORK/lekto-48.png" "$WORK/lekto-64.png" "$WORK/lekto-128.png" \
    "$WORK/lekto-256.png" "$OUT/lekto.ico"

# macOS: an ICNS is a flat list of length-prefixed chunks, each a PNG for one
# size. The modern (`icp*`/`ic0*`) chunk types are PNG, so no conversion is
# needed beyond the render.
for size in 16 32 64 128 256 512 1024; do
    render "$size" "$WORK/lekto-$size.png"
done
python3 - "$WORK" "$OUT/lekto.icns" <<'PYTHON'
import struct
import sys
from pathlib import Path

work, destination = Path(sys.argv[1]), Path(sys.argv[2])
chunks = [(16, b"icp4"), (32, b"icp5"), (64, b"icp6"), (128, b"ic07"),
          (256, b"ic08"), (512, b"ic09"), (1024, b"ic10")]

body = b"".join(
    kind + struct.pack(">I", len(png) + 8) + png
    for size, kind in chunks
    for png in [(work / f"lekto-{size}.png").read_bytes()]
)
# The header: the magic, then the total length including the header itself.
destination.write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)
PYTHON

echo "wrote $OUT/lekto.png, $OUT/lekto.ico and $OUT/lekto.icns"
