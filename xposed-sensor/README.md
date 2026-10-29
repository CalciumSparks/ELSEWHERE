# ELSEWHERE Xposed module

Classic Xposed API 82 module loaded by LSPatch or VirtualXposed.

- Entry: `com.elsewhere.location.xposed.SensorHook`
- Hook: `SystemSensorManager$SensorEventQueue.dispatchSensorEvent`
- Config: `Settings.Global.elsewhere_sensor_config` (optional; defaults to WALK/1.35/all channels)
- Built APK is bundled in the app at `app/src/main/assets/xposed/elsewhere-sensor-xposed.apk`

Build:

```bash
./gradlew :xposed-sensor:assembleRelease
```

See [`../docs/LSPATCH_XPOSED.md`](../docs/LSPATCH_XPOSED.md) for usage and limitations.
