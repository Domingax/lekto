#!/usr/bin/env sh
# Re-record the visual identity planches (issue #168).
#
# The prototype writes its images through Roborazzi, which targets the committed
# goldens directory; this script moves them where the issue points at them, so a
# re-record never leaves prototype images in the verified-goldens lane.
#
# Run from anywhere: the script walks back to the repository root.
set -eu

here=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root=$(CDPATH= cd -- "$here/../../.." && pwd)
cd "$root"

./gradlew :app:recordRoborazziDesktop --tests "app.lekto.prototype.VisualIdentityPrototype"
mv app/src/desktopTest/goldens/desktop/prototype/*.png "$here/"
rmdir app/src/desktopTest/goldens/desktop/prototype

echo "Planches regénérées dans $here"
