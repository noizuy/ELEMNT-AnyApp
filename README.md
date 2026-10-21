# ELEMNT AnyApp

Patches the Wahoo ELEMNT companion app (`com.wahoofitness.boltcompanion`) so it
forwards notifications from **any** app to your ELEMNT, not only the ones Wahoo supports.
Notifications from apps Wahoo doesn't know about appear on the ELEMNT as messages.

> Not affiliated with or endorsed by Wahoo Fitness. "Wahoo" and "ELEMNT" are trademarks of
> Wahoo Fitness. This project contains no Wahoo code; it patches a copy of the app you
> already have installed. Use at your own risk.

## Requirements

- Linux or macOS (on Windows, use WSL)
- JDK 11 or newer (`java` and `javac` on PATH, or set `JAVA_HOME`, e.g.
  `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk` if your default java is older)
- `python3`, `zip`, `unzip`, `curl`
- `adb` (Android platform-tools)
- An Android phone with **USB debugging** enabled and ELEMNT installed from the Play Store

## Install

On first run the scripts download their build tools (apktool, uber-apk-signer, and d8 and
android.jar from the Android SDK, about 60 MB) into `tools/` and verify their checksums.

1. Connect the phone via USB and accept the "Allow USB debugging" prompt.
2. Run:
   ```bash
   ./install.sh
   ```
   This pulls ELEMNT from your phone, patches it, uninstalls the original, and installs
   the patched version. It asks before uninstalling.
3. Open ELEMNT, sign in, pair your ELEMNT again, and grant notification access.

**Warning:** uninstalling deletes the app's data, so you will need to pair again. After
installing, don't update ELEMNT from the Play Store, because that would replace the patch.
To update, install the new version from the Play Store and run `./install.sh` again.

### Patch only (manual)

```bash
./patch.sh base.apk        # produces base-anyapp.apk
```

## Filtering apps (optional)

Create `Android/data/com.wahoofitness.boltcompanion/files/notif_filter.txt` on the phone:

```
# never forward Spotify
-com.spotify.music
# if any "+" line exists, ONLY these apps (plus Wahoo's built-in ones) are forwarded
+org.telegram.messenger
```

The easiest way to push it: `adb push notif_filter.txt /sdcard/Android/data/com.wahoofitness.boltcompanion/files/`

## Troubleshooting

- **`hook anchor not found`**: your ELEMNT version is different from the one this patch
  was written for (1.77.5.2) and the patch needs updating.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: the original app is still installed. Uninstall it first.

## License

MIT, see [LICENSE](LICENSE). This covers only this project's code, not the ELEMNT app
or the downloaded tools.
