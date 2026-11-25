#!/usr/bin/env bash
set -euo pipefail

# Compile committed source only. A disposable key is created outside the repository.
cd "$(dirname "$0")"
repository=$(git rev-parse --show-toplevel)
commit=$(git rev-parse HEAD)
output=${1:?Usage: build.sh ABSOLUTE_OUTPUT_DIRECTORY}
[[ "$output" = /* ]] || { echo 'Use an absolute output directory.' >&2; exit 1; }
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Set ANDROID_HOME to the Android SDK}}
platform="$sdk/platforms/android-34/android.jar"
tools="$sdk/build-tools/35.0.0"
java_bin=${JAVA_HOME:+$JAVA_HOME/bin/}
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
mkdir -p "$output" "$temporary/classes" "$temporary/dex" "$temporary/assets"
git -C "$repository" archive "$commit" LICENSE NOTICE tools/wsa-pointer-probe pointer-input/src/main/java | tar -x -C "$temporary"
source="$temporary/tools/wsa-pointer-probe"
probe=com/pokedaisy/mousesupremacy/probe
cat > "$temporary/BuildInfo.java" <<BUILD
package com.pokedaisy.mousesupremacy.probe;
final class BuildInfo {
    static final String COMMIT = "$commit";
}
BUILD
cp "$temporary"/{LICENSE,NOTICE} "$temporary/assets/"
printf '%s\n' "$commit" > "$temporary/assets/source-commit.txt"
"${java_bin}javac" --release 17 -classpath "$platform" -d "$temporary/classes" \
    "$temporary/pointer-input/src/main/java/com/pokedaisy/mousesupremacy/pointer/"*.java \
    "$source/src/$probe/"*.java "$temporary/BuildInfo.java"
"${java_bin}jar" cf "$temporary/classes.jar" -C "$temporary/classes" .
"$tools/d8" --min-api 26 --lib "$platform" --output "$temporary/dex" "$temporary/classes.jar"
"$tools/aapt2" link --manifest "$source/AndroidManifest.xml" -I "$platform" \
    -A "$temporary/assets" -o "$temporary/resources.apk"
cp "$temporary/resources.apk" "$temporary/unsigned.apk"
(cd "$temporary/dex" && zip -q "$temporary/unsigned.apk" classes.dex)
"$tools/zipalign" -f 4 "$temporary/unsigned.apk" "$temporary/aligned.apk"
"${java_bin}keytool" -genkeypair -storetype PKCS12 -keystore "$temporary/debug.p12" \
    -storepass android -keypass android -alias probe -keyalg RSA -keysize 2048 -validity 3650 \
    -dname 'CN=MouseSupremacy Pointer Probe Debug' > /dev/null 2>&1
apk="$temporary/mousesupremacy-pointer-probe.apk"
"$tools/apksigner" sign --ks "$temporary/debug.p12" --ks-pass pass:android --ks-key-alias probe \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled false \
    --out "$apk" "$temporary/aligned.apk"
"$tools/apksigner" verify --verbose --print-certs "$apk" > "$temporary/apk-signature.txt"
"$tools/zipalign" -c 4 "$apk"
"$tools/aapt" dump badging "$apk" > "$temporary/apk-badging.txt"
"$tools/aapt" dump xmltree "$apk" AndroidManifest.xml > "$temporary/apk-manifest.txt"
python3 "$source/verify-apk.py" "$temporary" "$commit"
cp "$apk" "$temporary"/apk-{badging,manifest,signature}.txt "$temporary/probe-build.json" \
    "$source/Test-WsaPointerProbe.ps1" "$source/README.md" "$temporary"/{LICENSE,NOTICE} "$output/"
