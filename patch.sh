#!/usr/bin/env bash
# Usage: ./patch.sh <ELEMNT.apk>   -> <ELEMNT>-anyapp.apk
# Pull the stock APK from your phone with:
#   adb shell pm path com.wahoofitness.boltcompanion   (then adb pull <path>)
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd); in=$(realpath "$1"); out="${in%.apk}-anyapp.apk"
J=${JAVA_HOME:+$JAVA_HOME/bin/}java; JC=${JAVA_HOME:+$JAVA_HOME/bin/}javac
for c in "$J" "$JC" python3 zip unzip curl; do command -v "$c" >/dev/null || { echo "missing: $c (need a JDK 11+, python3, zip, unzip, curl)" >&2; exit 1; }; done
"$here/fetch-tools.sh"
w=$(mktemp -d); trap 'rm -rf "$w"' EXIT
"$JC" --release 8 -nowarn -cp "$here/tools/sdk/android.jar" -d "$w/cls" "$here"/src/com/wahoofitness/mod/AnyAppNotif.java
"$J" -cp "$here/tools/sdk/d8.jar" com.android.tools.r8.D8 --min-api 29 --lib "$here/tools/sdk/android.jar" --output "$w" "$w"/cls/com/wahoofitness/mod/*.class
"$J" -jar "$here/tools/apktool.jar" d -f -r -o "$w/dec" "$in" >/dev/null
f=$(grep -rl '^.class.* Lcom/wahoofitness/boltcompanion/service/notifications/BCNotificationListener;' "$w"/dec/smali*/)
python3 - "$f" <<'PY'
import sys,re
p=sys.argv[1]; s=open(p).read()
pat=re.compile(r'(invoke-static \{v0\}, Lcom/wahoofitness/support/notif/e;->a\(Landroid/service/notification/StatusBarNotification;\)I\s+move-result v4\s+move-object/from16 v5, p0\n)')
hook='''
    invoke-static {v5, v0, v3, v4}, Lcom/wahoofitness/mod/AnyAppNotif;->remap(Landroid/content/Context;Landroid/service/notification/StatusBarNotification;II)I

    move-result v4

    shr-int/lit8 v3, v4, 0x8

    and-int/lit16 v4, v4, 0xff
'''
s,n=pat.subn(lambda m:m.group(1)+hook,s)
if n!=1: sys.exit("hook anchor not found (app version changed) - needs manual update")
open(p,'w').write(s)
PY
"$J" -jar "$here/tools/apktool.jar" b -o "$w/unsigned.apk" "$w/dec" >/dev/null
n=$(unzip -l "$w/unsigned.apk" | grep -c 'classes[0-9]*\.dex$'); mv "$w/classes.dex" "$w/classes$((n+1)).dex"
(cd "$w" && zip -q unsigned.apk "classes$((n+1)).dex")
"$J" -jar "$here/tools/signer.jar" -a "$w/unsigned.apk" -o "$w/signed" >/dev/null
mv "$w"/signed/*.apk "$out"; echo "built $out"
