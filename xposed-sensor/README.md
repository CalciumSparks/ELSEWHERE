# ELSEWHERE Xposed module

Classic Xposed API 82 module loaded by LSPatch or VirtualXposed.

- Entry: `com.elsewhere.location.xposed.SensorHook`
- Hook: `SystemSensorManager$SensorEventQueue.dispatchSensorEvent`
- Wireless hooks: `WifiInfo` / `WifiManager` scan, `BluetoothAdapter`, `TelephonyManager`,
  `CellIdentityLte`, `CellSignalStrengthLte`
- Config: `Settings.Global.elsewhere_sensor_config` (optional; WALK/1.35/all sensors,
  wireless groups disabled by default)
- Built APK (signed, version 1.1.0) is bundled in the app at
  `app/src/main/assets/xposed/elsewhere-sensor-xposed.apk`

Build:

```bash
./gradlew :xposed-sensor:assembleRelease
```

See [`../docs/LSPATCH_XPOSED.md`](../docs/LSPATCH_XPOSED.md) for usage and limitations.
