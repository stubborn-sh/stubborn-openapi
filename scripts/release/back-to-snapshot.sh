#!/usr/bin/env bash
#
# Restore the next -SNAPSHOT version after a release and push it. Relies on the
# git identity configured by tag-and-push.sh earlier in the same job.
#
# Usage: back-to-snapshot.sh <next-snapshot-version>
#
set -euo pipefail

NEXT="${1:?next snapshot version required (e.g. 0.0.3-SNAPSHOT)}"

./mvnw versions:set -DnewVersion="${NEXT}" -DgenerateBackupPoms=false -q
git add -A
git commit -m "Back to ${NEXT}"
git push
