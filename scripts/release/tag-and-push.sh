#!/usr/bin/env bash
#
# Commit the release version bump, tag it as vX.Y.Z, and push both the commit
# and the tag. Run by the Release workflow after the artifacts are deployed.
#
# Usage: tag-and-push.sh <version>
#
set -euo pipefail

VERSION="${1:?release version required (e.g. 0.0.2)}"

git config user.name "github-actions[bot]"
git config user.email "github-actions[bot]@users.noreply.github.com"
git add -A
git commit -m "Release ${VERSION}"
git tag "v${VERSION}"
git push
git push --tags
