#!/usr/bin/env bash
# Builds the documentation site into site/: the pages (Zensical, see zensical.toml) and the Dokka
# API reference under site/api/. Preview the pages alone with `uvx zensical serve`.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/build-component-docs.py --check
./gradlew -q :demo:compileDebugKotlin :dokkaGenerate
uvx zensical build --clean
uvx zensical build --config-file zensical.zh.toml --clean
# Zensical does not copy the shared asset-directory symlink into the Chinese output.
cp -R docs/assets/. site/zh/assets/
rm -rf site/api
cp -R build/dokka/html site/api
python3 tools/check-docs.py
echo "Site: $(pwd)/site/index.html"
