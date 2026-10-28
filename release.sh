#!/usr/bin/env bash
# Releases Cosmic Plumber: bumps versionCode and the patch version, builds and signs the APK on this PC, commits
# and pushes, then publishes it as a GitHub release with a latest.json beside it, which Settings › Updates picks up.
# Usage: ./release.sh "What changed"      (./release.sh --current "Notes" publishes the current version unbumped)
set -euo pipefail
cd "$(dirname "$0")"
bump=1
if [ "${1:-}" = "--current" ]; then bump=0; shift; fi
notes=${1:?Usage: ./release.sh \"What changed\"}
if [ $bump = 1 ]; then
    code=$(sed -n 's/^versionCode=//p' version.properties)
    name=$(sed -n 's/^versionName=//p' version.properties)
    newname="${name%.*}.$(( ${name##*.} + 1 ))"
    sed -i "s/^versionCode=.*/versionCode=$((code + 1))/; s/^versionName=.*/versionName=$newname/" version.properties
fi
code=$(sed -n 's/^versionCode=//p' version.properties)
name=$(sed -n 's/^versionName=//p' version.properties)
./build.sh testDebugUnitTest assembleRelease
git add -A
git diff --cached --quiet || git commit -m "$notes" -m "Cosmic Plumber $name"
git push

python3 - "$code" "$name" "$notes" <<'PY'
import hashlib, json, sys
apk = open("CosmicPlumber.apk", "rb").read()
json.dump({"versionCode": int(sys.argv[1]), "versionName": sys.argv[2], "sha256": hashlib.sha256(apk).hexdigest(),
           "size": len(apk), "notes": sys.argv[3]}, open("latest.json", "w"), indent=2)
PY
gh release create "v$name" CosmicPlumber.apk latest.json --title "Cosmic Plumber $name" --notes "$notes" --latest
rm latest.json
echo "Released Cosmic Plumber $name."
