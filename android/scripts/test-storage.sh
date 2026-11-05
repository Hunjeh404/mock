#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$project_dir/build/storage-tests"
find "$project_dir/app/src/main/java/com/hoonjay/filmroom/core" "$project_dir/tests/storage-stubs" -name '*.java' > "$project_dir/build/storage-sources.txt"
for name in Stores RecipeJson EditJson; do printf '%s\n' "$project_dir/app/src/main/java/com/hoonjay/filmroom/storage/$name.java" >> "$project_dir/build/storage-sources.txt"; done
printf '%s\n' "$project_dir/tests/StorageReconcileTests.java" >> "$project_dir/build/storage-sources.txt"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -cp "${PRESS_JSON_TEST_JAR:-$project_dir/build/test-dependencies/json.jar}" -d "$project_dir/build/storage-tests" @"$project_dir/build/storage-sources.txt"
java -cp "$project_dir/build/storage-tests:${PRESS_JSON_TEST_JAR:-$project_dir/build/test-dependencies/json.jar}" StorageReconcileTests
