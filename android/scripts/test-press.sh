#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$project_dir/scripts/validate-guides.py"
mkdir -p "$project_dir/build/press-tests" "$project_dir/build/press-fixtures"
find "$project_dir/app/src/main/java/com/hoonjay/filmroom/core" -name '*.java' > "$project_dir/build/press-test-sources.txt"
printf '%s\n' "$project_dir/tests/RawCalibrationTests.java" "$project_dir/tests/PressImageTests.java" >> "$project_dir/build/press-test-sources.txt"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$project_dir/build/press-tests" @"$project_dir/build/press-test-sources.txt"
java -Xmx768m -cp "$project_dir/build/press-tests" PressImageTests "$project_dir/build/press-fixtures"
