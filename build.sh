#!/usr/bin/env bash
# Builds Cosmic Plumber gently (niced), without writing to ~/.gradle or ~/.android. Dependencies come from the
# machine's shared Gradle cache; anything missing there is fetched into .gradle-home.
# Usage: ./build.sh [assembleRelease|assembleDebug|testDebugUnitTest|...]
set -euo pipefail
cd "$(dirname "$0")"
G=$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1 | head -1)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=${TMPDIR:-/tmp}"
export ANDROID_USER_HOME="$PWD/.android-home"
export GRADLE_USER_HOME="$PWD/.gradle-home"
export GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches"
args=("$@")
[ ${#args[@]} -eq 0 ] && args=(assembleRelease)
nice -n 19 "$G/bin/gradle" --no-daemon -Pkotlin.compiler.execution.strategy=in-process "${args[@]}"
apk=app/build/outputs/apk/release/app-release.apk
if [[ " ${args[*]} " == *" assembleRelease "* && -f $apk ]]; then
    "$(ls -d /opt/android-sdk/build-tools/* | sort -V | tail -1)/apksigner" verify "$apk"
    cp "$apk" CosmicPlumber.apk
    echo "CosmicPlumber.apk ($(du -h CosmicPlumber.apk | cut -f1)), signature verified"
fi
