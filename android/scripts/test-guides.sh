#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
json_jar="${PRESS_JSON_TEST_JAR:-$project_dir/build/test-dependencies/json.jar}"
if [[ ! -f "$json_jar" ]]; then
  mkdir -p "$(dirname "$json_jar")"
  curl -L --fail --silent --show-error https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o "$json_jar"
fi
mkdir -p "$project_dir/build/guide-tests"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -classpath "$json_jar" -d "$project_dir/build/guide-tests" "$project_dir/tests/guide-stubs/android/content/Context.java" "$project_dir/tests/guide-stubs/android/content/res/AssetManager.java" "$project_dir/app/src/main/java/com/hoonjay/filmroom/core/GuideAppearance.java" "$project_dir/app/src/main/java/com/hoonjay/filmroom/guides/GuideCatalog.java" "$project_dir/tests/GuideCatalogTests.java"
java -classpath "$project_dir/build/guide-tests:$json_jar" GuideCatalogTests "$project_dir/app/src/main/assets"
