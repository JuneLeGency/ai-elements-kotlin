#!/usr/bin/env bash
# Publish to an isolated file repository, then compile apps with Maven coordinates only.
set -euo pipefail
cd "$(dirname "$0")/.."
version="${1:-$(sed -n 's/^VERSION_NAME=//p' gradle.properties)}"
repository="$PWD/build/consumer-repository"
./gradlew publishToMavenLocal "-PVERSION_NAME=$version" "-Dmaven.repo.local=$repository"
python3 tools/build-component-docs.py --check --export-samples samples/published-consumer/build/component-samples
./gradlew -p samples/published-consumer --max-workers=2 assembleDebug assembleRelease lintDebug \
  "-PlibraryVersion=$version" "-PartifactRepository=$repository"
