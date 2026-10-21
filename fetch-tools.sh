#!/usr/bin/env bash
# Downloads the build tools into tools/ and verifies their checksums. patch.sh runs this automatically.
set -euo pipefail
t="$(cd "$(dirname "$0")" && pwd)/tools"; mkdir -p "$t/sdk"
sha() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }
# fetch <dest> <sha256> <url> [path-in-zip]
fetch() {
  local dest="$t/$1" want=$2 url=$3 member=${4:-} tmp
  [ -f "$dest" ] && [ "$(sha "$dest")" = "$want" ] && return
  echo "downloading $1"; tmp=$(mktemp)
  curl -fsSL -o "$tmp" "$url"
  if [ -n "$member" ]; then unzip -p "$tmp" "$member" > "$tmp.x"; mv "$tmp.x" "$tmp"; fi
  [ "$(sha "$tmp")" = "$want" ] || { rm -f "$tmp"; echo "checksum mismatch for $1" >&2; exit 1; }
  mv "$tmp" "$dest"
}
fetch apktool.jar dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423 \
  https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar
fetch signer.jar e1299fd6fcf4da527dd53735b56127e8ea922a321128123b9c32d619bba1d835 \
  https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar
fetch sdk/d8.jar d43c8a94c9b1f1da1a7cc49c32b81e8cee1708b37ee8b530a81a0688222b42c0 \
  https://dl.google.com/android/repository/build-tools_r34-linux.zip 'android-14/lib/d8.jar'
fetch sdk/android.jar 6cea1df3efb77103ac3e2beb9bf4718964b0e0869ab16d39d29d5cbae1c147ad \
  https://dl.google.com/android/repository/platform-34-ext7_r03.zip '*/android.jar'
