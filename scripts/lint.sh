#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
project_dir="$repo_root/resource-tracker"
lint_tool_dir="${TMPDIR:-/tmp}/resource-tracker-lint-tools"
ktlint_version="1.8.0"
detekt_version="2.0.0-alpha.6"

mkdir -p "$lint_tool_dir"

ktlint_jar="$lint_tool_dir/ktlint-cli-$ktlint_version-all.jar"
if [[ ! -f "$ktlint_jar" ]]; then
  curl --fail --silent --show-error --location \
    "https://repo.maven.apache.org/maven2/com/pinterest/ktlint/ktlint-cli/$ktlint_version/ktlint-cli-$ktlint_version-all.jar" \
    --output "$ktlint_jar"
fi

detekt_jar="$lint_tool_dir/detekt-cli-$detekt_version-all.jar"
if [[ ! -f "$detekt_jar" ]]; then
  curl --fail --silent --show-error --location \
    "https://repo.maven.apache.org/maven2/dev/detekt/detekt-cli/$detekt_version/detekt-cli-$detekt_version-all.jar" \
    --output "$detekt_jar"
fi

cd "$project_dir"
if [[ "${1:-}" == "--update-baseline" ]]; then
  rm -f ktlint-baseline.xml detekt-baseline.xml
  java -jar "$ktlint_jar" --baseline=ktlint-baseline.xml "src/main/kotlin/**/*.kt" "src/test/kotlin/**/*.kt" || true
  java -jar "$detekt_jar" \
    --input src/main/kotlin:src/test/kotlin \
    --baseline detekt-baseline.xml \
    --create-baseline \
    --build-upon-default-config \
    --fail-on-severity Warning
else
  java -jar "$ktlint_jar" --baseline=ktlint-baseline.xml "src/main/kotlin/**/*.kt" "src/test/kotlin/**/*.kt"
  java -jar "$detekt_jar" \
    --input src/main/kotlin:src/test/kotlin \
    --baseline detekt-baseline.xml \
    --build-upon-default-config \
    --fail-on-severity Warning
fi
