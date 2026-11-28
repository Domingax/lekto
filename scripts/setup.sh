#!/usr/bin/env sh
# Install the repository's Git hooks. Run once per clone; linked worktrees share
# the setting because core.hooksPath lives in the clone's .git/config.
set -eu

repo_root=$(cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

git config --local core.hooksPath .githooks
chmod +x .githooks/commit-msg

echo "Git hooks installed: core.hooksPath=.githooks"
