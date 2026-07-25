#!/usr/bin/env bash
set -euo pipefail

TAG="${1:-}"
if [ -z "$TAG" ]; then
  echo "Usage: $0 <tag>"
  echo "  e.g. $0 v1.7.2-experimental"
  exit 1
fi

if ! echo "$TAG" | grep -qE '^v[0-9]+\.[0-9]+\.[0-9]+(-experimental|-SNAPSHOT)?$'; then
  echo "Error: tag must match v<num>.<num>.<num>[-experimental|-SNAPSHOT]"
  echo "  e.g. v1.7.2, v1.7.2-experimental, v1.7.2-SNAPSHOT"
  exit 1
fi

VERSION="${TAG#v}"

echo "Setting pom version to $VERSION"
mvn versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false

echo "Committing and tagging"
git add -A
git commit -m "Release $TAG"
git tag "$TAG"

echo "Done. Push with: git push origin main --tags"
