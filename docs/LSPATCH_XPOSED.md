# LSPatch / VirtualXposed sensor module

`他乡` 现在带一个独立的 **classic Xposed API 82** 模块，供 LSPatch / VirtualXposed 在无 Root 环境下加载。

模块包名：`com.elsewhere.location.xposed`
模块入口：`com.elsewhere.location.xposed.SensorHook`
Hook 目标：`android.hardware.SystemSensorManager$SensorEventQueue.dispatchSensorEvent`

模块 APK 已打进 `他乡` 的 assets，环境页 →「05 LSPatch / Xposed」可以直接导出/分享，也可以从源码构建。

---

## 1. 它与 Frida/JDWP 的区别

| | JDWP + Frida | LSPatch / VirtualXposed 模块 |
| --- | --- | --- |
| Root | 不需要 | 不需要 |
| 目标限制 | 必须 debuggable | 必须被 LSPatch patch，或运行在 VirtualXposed 中 |
| 目标 APK 是否改写 | 不改 | LSPatch 改写 APK 签名；VirtualXposed 使用虚拟空间 |
| 注入时机 | 外部 ADB/JDWP | 目标进程启动时由 LSPatch 加载模块 |
| 配置 | 生成到脚本 | `Settings.Global.elsewhere_sensor_config` |

两者都不能 hook 一个“完全未修改、未虚拟化”的非 debuggable 生产 App。LSPatch 的“免 Root”是 patch 单 App；不是系统级注入。

---

## 2. LSPatch 使用步骤

1. 在 `他乡 → 环境 → 05 LSPatch / Xposed` 点「导出模块 APK」，安装导出的 APK。
   - 也可以安装 LSPatch manager 后，从已安装模块列表选择 `他乡 · 传感器 Xposed 模块`。
2. 安装 [LSPatch](https://github.com/JingMatrix/LSPatch/releases)（选 manager release APK）。
3. 在 LSPatch 中选择目标 APK，勾选本模块，执行 patch，然后安装 patched APK。
   - Manager mode：模块变化可随时生效，但 patched App 与 manager 绑定。
   - Integrated mode：模块固化进 patched APK，更独立。
4. 启动目标 App。模块默认使用 WALK / 1.35 m/s / 全部传感器通道。
5. 如需同步 `他乡` 当前模式：
   - 点「写入当前配置」（需要 Shizuku）；或复制面板里的 ADB 命令：
     ```bash
     adb shell settings put global elsewhere_sensor_config '{"enabled":true,"mode":"WALK","speedMps":1.35,...}'
     ```
   - 模块每 2 秒重读一次全局配置；目标下次 dispatch 即生效。

---

## 3. VirtualXposed 使用步骤

1. 安装 VirtualXposed。
2. 将 **目标 App** 和 **Xposed 模块 APK** 都安装到 VirtualXposed 的虚拟空间。
3. 在 VirtualXposed 的模块管理中启用 `他乡 · 传感器 Xposed 模块`，作用域选择目标 App。
4. 启动目标 App。
5. 配置方式与 LSPatch 相同：Shizuku/ADB 写入 `Settings.Global.elsewhere_sensor_config`，或使用默认配置。

VirtualXposed 里目标运行在虚拟容器中，权限、数据目录和进程行为与原始安装不同；这是测试环境的限制，不是“绕过”。

---

## 4. 配置格式

```json
{
  "enabled": true,
  "mode": "WALK",
  "speedMps": 1.35,
  "amplitude": 1.0,
  "sensors": {
    "accelerometer": true,
    "gyroscope": true,
    "gravity": true,
    "linearAcceleration": true,
    "stepCounter": true,
    "stepDetector": true
  }
}
```

- `mode`：`WALK` / `RUN` / `CYCLE`
- `speedMps`：目标速度
- `amplitude`：幅度缩放
- 传感器开关与 `他乡` 环境页一致

模块读取 `Settings.Global.getString(contentResolver, "elsewhere_sensor_config")`。没有配置时使用 WALK + 全部通道。

---

## 5. 构建模块

```bash
./gradlew :xposed-sensor:assembleRelease
```

产物：

```text
xposed-sensor/build/outputs/apk/release/xposed-sensor-release-unsigned.apk
```

需要签名后才能安装；`他乡` 的 assets 中已放入使用项目 release key 签名的版本。重新构建后可用 `apksigner` 签名并替换：

```bash
apksigner sign --ks release/elsewhere-release.jks \
  --ks-key-alias elsewhere \
  --out app/src/main/assets/xposed/elsewhere-sensor-xposed.apk \
  xposed-sensor/build/outputs/apk/release/xposed-sensor-release-unsigned.apk
rm -f app/src/main/assets/xposed/elsewhere-sensor-xposed.apk.idsig
```

Xposed API 82 只用于编译（`compileOnly`），不会打进模块 APK；运行时由 LSPatch/Vector 提供。

---

## 6. 验证状态

已完成：

- `:xposed-sensor:assembleRelease` 成功，模块 APK 1.0.0 构建并签名。
- `他乡` 能导出该 APK，系统分享面板打开，模块安装成功（`pm install`）。
- 模块 APK 的 manifest 包含 `xposedmodule`、`xposedminversion=82`，以及 `assets/xposed_init`。
- LSPatch v1.2 CLI 实际执行：

  ```bash
  java -jar lspatch.jar -m elsewhere-sensor-xposed.apk \
    -o out sensor-target.apk
  ```

  输出 `Embedding modules: com.elsewhere.location.xposed`、`Done`。patched APK 安装成功。
- 启动 target 后 logcat 出现：

  ```text
  ELSEWHERE-XPOSED: installed hooks for com.example.sensortarget
  ```

- target 的加速度/重力/线性加速度被真实改写：重力变为 `0,0,9.81`，线性加速度从原始噪声变为时变值。
- 通过 `settings put global elsewhere_sensor_config '{"enabled":false,...}'` 关闭后，target 收到的值恢复为原始值；重新启用后再次变为脚本曲线。模块每 2 秒重读配置，链路已验证。
- 同一套公式也在 Frida 16.7.19 + JDWP 的真实传感器目标上验证。

> LSPatch manager 的 UI 在当前模拟器上 patch 到 “Writing & signing” 卡住；改用 **官方 lspatch CLI jar** 后完成 patch 和安装，因此 CLI 路径是已验证路径。管理员 UI 的卡住属于当前环境问题，不是模块本身。

---

## 7. 限制与安全

- LSPatch/VirtualXposed 只影响被 patch/虚拟化的 App；不能注入任意未修改 App。
- 目标 APK 被重新签名，可能被完整性校验、SafetyNet/Play Integrity 或应用自身的 Frida/Xposed 检测发现。
- 模块只改 Java 层 `SensorEvent`；native 传感器路径不受影响。
- 不绕过反作弊、Mock 检测或授权限制。
- 模块与 `他乡` 定位 Mock 是两条独立链路；定位注入依旧需要 Shizuku/AppOps。
- 仅在你有权修改和测试的设备/App 上使用。
