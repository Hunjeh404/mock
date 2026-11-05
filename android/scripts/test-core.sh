#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$project_dir/build/core-tests" "$project_dir/build/test-output"
find "$project_dir/app/src/main/java/com/hoonjay/filmroom/core" -name '*.java' > "$project_dir/build/core-test-sources.txt"
printf '%s\n' "$project_dir/tests/CoreTests.java" >> "$project_dir/build/core-test-sources.txt"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$project_dir/build/core-tests" @"$project_dir/build/core-test-sources.txt"
java -Xmx768m -Djava.awt.headless=true -cp "$project_dir/build/core-tests" CoreTests "$project_dir/build/test-output"
