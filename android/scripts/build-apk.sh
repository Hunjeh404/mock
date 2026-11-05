#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" ]]; then printf '%s\n' 'Set ANDROID_SDK_ROOT to your Android SDK folder.' >&2; exit 1; fi
build_tools="${PRESS_BUILD_TOOLS:-35.0.0}"
platform="${PRESS_PLATFORM:-35}"
tools_dir="$sdk_root/build-tools/$build_tools"
android_jar="$sdk_root/platforms/android-$platform/android.jar"
read -r app_package namespace app_code app_version < <(python3 - "$project_dir/app/build.gradle" <<'PY'
import re,sys
s=open(sys.argv[1]).read()
print(re.search(r"applicationId '([^']+)'",s)[1],re.search(r"namespace '([^']+)'",s)[1],re.search(r'versionCode (\d+)',s)[1],re.search(r"versionName '([^']+)'",s)[1])
PY
)
python3 "$project_dir/scripts/validate-guides.py"
out="$project_dir/build/direct"
mkdir -p "$out" "$project_dir/dist"
rm -rf "$out/classes" "$out/gen" "$out/dex"
mkdir -p "$out/classes" "$out/gen" "$out/dex"
"$tools_dir/aapt2" compile --dir "$project_dir/app/src/main/res" -o "$out/resources.zip"
python3 - "$project_dir/app/src/main/AndroidManifest.xml" "$out/AndroidManifest.xml" "$app_package" <<'PY'
import sys
s=open(sys.argv[1]).read().replace('<manifest ', '<manifest package="'+sys.argv[3]+'" ', 1)
open(sys.argv[2],'w').write(s)
PY
"$tools_dir/aapt2" link -o "$out/unsigned.apk" -I "$android_jar" --manifest "$out/AndroidManifest.xml" --min-sdk-version 30 --target-sdk-version 35 --version-code "$app_code" --version-name "$app_version" --custom-package "$namespace" --java "$out/gen" -A "$project_dir/app/src/main/assets" "$out/resources.zip"
find "$project_dir/app/src/main/java" "$out/gen" -name '*.java' > "$out/sources.txt"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -source 17 -target 17 -classpath "$android_jar" -d "$out/classes" @"$out/sources.txt"
java -m jdk.jartool/sun.tools.jar.Main --create --file "$out/classes.jar" -C "$out/classes" .
java -cp "$tools_dir/lib/d8.jar" com.android.tools.r8.D8 --min-api 30 --lib "$android_jar" --output "$out/dex" "$out/classes.jar"
python3 - "$out/unsigned.apk" "$out/dex" <<'PY'
import sys,pathlib,zipfile
with zipfile.ZipFile(sys.argv[1],'a',compression=zipfile.ZIP_DEFLATED) as z:
 for p in pathlib.Path(sys.argv[2]).glob('*.dex'):z.write(p,p.name)
PY
"$tools_dir/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
key_path="${PRESS_SIGNING_KEYSTORE:-$project_dir/signing/debug.keystore}"
mkdir -p "$(dirname "$key_path")"
if [[ ! -f "$key_path" ]]; then
 java -m java.base/sun.security.tools.keytool.Main -genkeypair -keystore "$key_path" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Press Camera Development, O=Hoon-jay, C=KR' >/dev/null 2>&1
fi
apk="$project_dir/dist/PressCamera-Android-$app_version.apk"
java -jar "$tools_dir/lib/apksigner.jar" sign --ks "$key_path" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$apk" "$out/aligned.apk"
java -jar "$tools_dir/lib/apksigner.jar" verify --verbose "$apk"
printf '\nAPK: %s\n' "$apk"
