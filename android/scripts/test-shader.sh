#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$project_dir/build/shader-tests" "$project_dir/build/shader-fixtures"
find "$project_dir/app/src/main/java/com/hoonjay/filmroom/core" -name '*.java' > "$project_dir/build/shader-sources.txt"
printf '%s\n' "$project_dir/tests/ShaderFixtures.java" >> "$project_dir/build/shader-sources.txt"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$project_dir/build/shader-tests" @"$project_dir/build/shader-sources.txt"
java -cp "$project_dir/build/shader-tests" ShaderFixtures "$project_dir/build/shader-fixtures"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -cp "$project_dir/build/shader-tests" -d "$project_dir/build/shader-tests" "$project_dir/tests/PreviewColorTests.java"
java -cp "$project_dir/build/shader-tests" PreviewColorTests "$project_dir/build/shader-fixtures"
python3 "$project_dir/tests/shader-parity.py" "$project_dir/build/shader-fixtures"
