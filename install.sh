#!/usr/bin/env bash
# Usage: ./install.sh   (phone connected via USB with USB debugging enabled)
# Pulls the ELEMNT app from the phone, patches it, and reinstalls the patched version.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd); pkg=com.wahoofitness.boltcompanion
J=${JAVA_HOME:+$JAVA_HOME/bin/}java
command -v adb >/dev/null || { echo "missing: adb (Android platform-tools)" >&2; exit 1; }
[ "$(adb get-state 2>/dev/null)" = device ] || { echo "no phone found - connect via USB, enable USB debugging, accept the prompt" >&2; exit 1; }
paths=$(adb shell pm path "$pkg" | tr -d '\r' | sed 's/^package://')
[ -n "$paths" ] || { echo "$pkg is not installed on the phone - install ELEMNT from the Play Store first" >&2; exit 1; }
w=$(mktemp -d); trap 'rm -rf "$w"' EXIT
mkdir "$w/pull" "$w/out"
for p in $paths; do echo "pulling $p"; adb pull "$p" "$w/pull/" >/dev/null; done
"$here/patch.sh" "$w/pull/base.apk"
mv "$w/pull/base-anyapp.apk" "$w/out/"; rm "$w/pull/base.apk"
# split APKs must carry the same signature as the patched base
if ls "$w"/pull/*.apk >/dev/null 2>&1; then
  "$J" -jar "$here/tools/signer.jar" --allowResign -a "$w/pull" -o "$w/out" >/dev/null
fi
cp "$w/out/base-anyapp.apk" "$here/"
echo
echo "The original app must be uninstalled first (different signature)."
echo "This deletes the app's data - you will need to pair your ELEMNT again."
read -rp "Uninstall original and install patched version? [y/N] " a
[ "$a" = y ] || [ "$a" = Y ] || { echo "aborted; patched APK saved as $here/base-anyapp.apk"; exit 0; }
adb uninstall "$pkg"
adb install-multiple "$w"/out/*.apk
echo "done - open ELEMNT on the phone and grant notification access again"
