#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$project_dir/build/capture-focus-tests"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$project_dir/build/capture-focus-tests" "$project_dir/app/src/main/java/com/hoonjay/filmroom/core/CaptureFocusGate.java" "$project_dir/tests/CaptureFocusTests.java"
java -cp "$project_dir/build/capture-focus-tests" CaptureFocusTests
