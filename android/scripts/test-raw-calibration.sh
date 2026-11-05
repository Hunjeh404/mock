#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$project_dir/build/raw-calibration-tests" "$project_dir/build/raw-calibration-output"
find "$project_dir/app/src/main/java/com/hoonjay/filmroom/core" -name '*.java' > "$project_dir/build/raw-calibration-sources.txt"
printf '%s\n' "$project_dir/tests/RawCalibrationTests.java" >> "$project_dir/build/raw-calibration-sources.txt"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$project_dir/build/raw-calibration-tests" @"$project_dir/build/raw-calibration-sources.txt"
java -Xmx1024m -cp "$project_dir/build/raw-calibration-tests" RawCalibrationTests "$project_dir/build/raw-calibration-output" "$@"
