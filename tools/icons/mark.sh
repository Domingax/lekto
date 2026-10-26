#!/usr/bin/env bash
#
# Regenerate the launcher mark (issue #177).
#
# The mark has one source of truth: the SVGs in `tools/icons/mark/`. This script
# turns them into the Android vector drawables, the guide's favicon and, through
# `:app:renderAppIcon`, the desktop rasters (PNG, ICO, ICNS). Every output is
# committed, so a change to the mark is a reviewable diff and no build step
# depends on this script running. Edit the SVGs, never the generated files.
#
# The geometry, the palette and the rule that decides which sizes keep the
# colours are `docs/design.md` §5; the font licence is
# `THIRD-PARTY-NOTICES.md`.
#
# Requirements: bash, java (JDK 21 — `Convert.java` is a single-file program).
# The rasters additionally need the Gradle wrapper, which the repository ships.
#
# Usage: tools/icons/mark.sh [--vectors-only]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MARK_DIR="$REPO_ROOT/tools/icons/mark"
RES_DIR="$REPO_ROOT/app/src/androidMain/res"
GUIDE_PUBLIC="$REPO_ROOT/guide/public"

# The mark's ground, docs/design.md §2.1: an app icon cannot follow the App
# theme, so the favicon always sits on the Light paper.
PAPER="#FAF7F2"

command -v java >/dev/null 2>&1 || {
    echo "mark.sh: java is required but not on PATH" >&2
    exit 1
}

convert() {
    java "$MARK_DIR/Convert.java" "$@"
}

echo "Mark: writing the Android vector drawables"
# The coloured mark is the launcher's foreground; the letter alone is the
# monochrome layer Android 13 tints. Both are the 108 dp adaptive container.
convert vector "$MARK_DIR/mark.svg" \
    "$RES_DIR/drawable/ic_launcher_foreground.xml" 108dp 108dp
convert vector "$MARK_DIR/letter.svg" \
    "$RES_DIR/drawable/ic_launcher_monochrome.xml" 108dp 108dp

echo "Mark: writing the guide's favicon"
convert favicon "$MARK_DIR/mark.svg" "$GUIDE_PUBLIC/favicon.svg" "$PAPER"

if [[ "${1:-}" == "--vectors-only" ]]; then
    echo "Mark: vectors only; run ./gradlew :app:renderAppIcon for the rasters."
    exit 0
fi

echo "Mark: rendering the desktop rasters"
"$REPO_ROOT/gradlew" -q :app:renderAppIcon

echo "Mark: done. Run ./gradlew check to prove the tree still builds."
