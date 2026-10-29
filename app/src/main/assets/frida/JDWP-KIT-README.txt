# ELSEWHERE · JDWP no-root injection kit

Target package: read from `target.txt` in this directory.

This kit contains a pre-generated Frida sensor script and a vendored copy of
frida-jdwp-loader (MIT). It injects over Android's JDWP debug channel; it does
NOT require root and does NOT repackage the target APK.

## Requirements

- Target app must be debuggable (`android:debuggable="true"`), or the device /
  emulator must have `ro.debuggable=1`.
- `adb` in PATH and USB/wireless debugging enabled.
- Python 3 in PATH (the loader uses only the standard library).
- Network access on first run unless you drop a matching `frida-gadget-*.so`
  into this directory: the launchers detect that file and pass `-g` to the
  loader; otherwise the loader downloads the matching ABI automatically.

## Windows

```powershell
powershell -ExecutionPolicy Bypass -File .\run-jdwp-windows.ps1
```

## macOS / Linux

```bash
chmod +x run-jdwp-unix.sh
./run-jdwp-unix.sh
```

## Output

The loader runs the script autonomously. Read the hook status from Android logcat:

```bash
adb logcat -s ELSEWHERE-SENSOR:*
```

The target must run and register the relevant sensors. This kit cannot create
hardware sensor events that do not exist, and it does not bypass Frida / mock
detection.

## Limits

- Debuggable targets only. Non-debuggable production apps need a rooted
  frida-server, a Frida Gadget rebuild, or a patch/virtual-space route such as
  LSPatch / VirtualXposed with an Xposed module (not included here).
- The loader's Windows package check is already patched in this vendored copy.
- No anti-cheat or integrity-check bypass.
