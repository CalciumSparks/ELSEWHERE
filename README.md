# 他乡 / ELSEWHERE

> Android 上的无 Root 位置仪。地图、准星、悬浮摇杆、路线，以及一条可选的 Frida 传感器桥。

[中文](#中文) · [English](#english) · [Releases](https://github.com/CalciumSparks/ELSEWHERE/releases) · [Interactive architecture](docs/architecture/elsewhere-architecture.html)

[![他乡系统结构](docs/architecture/elsewhere-architecture.png)](docs/architecture/elsewhere-architecture.html)

> **分支状态 / Branch status**  
> `main` 在 `v1.0.0` 之后新增了 **路线录制** 与 **Frida Sensor Bridge**；这两项尚未进入新的 GitHub Release。当前公开 APK 仍是 `v1.0.0`，如需使用新功能请从源码构建。  
> `main` adds **route recording** and the **Frida Sensor Bridge** after `v1.0.0`. The latest public APK is still `v1.0.0`; build from source to try the new work.

---

## 中文

### 它是什么

他乡是一台**不需要 Root 的位置仪**：Shizuku 提供 shell 级通道，应用向 Android 的 GPS / Network / Fused 测试定位提供者写入坐标，地图负责选点和路线，前台服务维持输出，悬浮摇杆负责跨应用移动。

核心定位链路不需要 Root。**Frida 传感器桥是可选的高级外挂**，它需要 Root + frida-server，或把 Frida Gadget 打进目标 APK；没有外部注入环境时，应用只生成和部署脚本，不会假装已经 Hook 了其他 App。

### 核心能力

- 全屏地图、固定准星、拖图选点、地址搜索、经纬度输入。
- 高德栅格底图（GCJ-02 显示）与 OpenStreetMap；内部与注入始终 WGS-84。
- 一键开始 / 停止模拟；停止后移除测试提供者，系统定位恢复。
- 真实 GPS 两阶段确认：先显示坐标、地址、精度与缓存年龄，再手动应用。
- 走路 / 跑步 / 骑行，速度包络、步态节奏、步数、路线环线与漂移。
- 系统悬浮摇杆；路线巡航时自动切换为环形进度。
- 应用内摇杆、路线规划 / 保存 / 重播。
- **路线录制**：在模拟移动时采样实时轨迹，保存为可巡航 RoutePlan。
- Wi-Fi / 蓝牙 / 基站 extras、场景预设、应用内传感器推演。
- 应用扫描、缓存占用、手动系统清理入口、外部缓存快照。
- **Frida Sensor Bridge**：生成真实 JavaScript Hook，通过目标进程的 `SystemSensorManager` 改写六类传感器事件。
- Shizuku / Root / Hook 诊断，GitHub Release 更新检查。

### 真实 / 模拟边界

| 能力 | 实际效果 | 说明 |
| --- | --- | --- |
| 系统定位 | **真实注入** | GPS / Network / Fused 测试提供者；Android 会标记为 mock。 |
| 坐标 / 运动 / 路线 / 漂移 | **真实注入** | 前台服务每 200 ms 推进并在测试提供者中更新。 |
| 悬浮摇杆 | **真实系统悬浮窗** | `TYPE_APPLICATION_OVERLAY`。 |
| 路线录制 | **应用内采样** | 采样当前被注入的坐标，保存为普通 RoutePlan。 |
| 应用内传感器 | **仅应用内推演** | 无 Root 无法把传感器流交给其他 App。 |
| Frida 传感器 Hook | **真实 Hook 脚本（外部注入）** | 需要 Root frida-server 或 Frida Gadget；只影响目标进程。 |
| Wi-Fi / 蓝牙 / 基站 | **Location extras** | 不改变系统无线电扫描。 |
| 清理其他 App 缓存 | **系统页面手动完成** | Shizuku shell 不具备 `INTERNAL_DELETE_CACHE_FILES`。 |

### 安装

稳定发行版是 [`v1.0.0`](https://github.com/CalciumSparks/ELSEWHERE/releases/tag/v1.0.0)。从 Release 下载 APK：

```bash
adb install elsewhere-1.0.0.apk
```

要试用本分支新增的路线录制 / Frida 功能，请从 `main` 构建：

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

首次打开会进入三步校准。

### 准备 Shizuku（推荐）

1. 安装并启动 [Shizuku](https://github.com/RikkaApps/Shizuku/releases)。
2. 开启无线调试，按 Shizuku 指引启动服务。
3. 在「他乡 → 首次校准 / 系统与诊断」点击「打开 Shizuku 启动」。
4. 授权「他乡」，回到应用自动刷新。
5. 点击「授权 / 配置」，应用会通过 shell 完成模拟位置 AppOp、悬浮窗与通道检查。

没有 Shizuku 时也能使用核心模拟定位：在开发者选项 →「选择模拟位置信息应用」中选择「他乡」，授予精确位置、通知与悬浮窗权限即可。

### 使用

**选点 / 启动**：拖动地图把目标点放到准星下，点「开始模拟」；再点停止。

**真实 GPS**：静止时点右侧「真实」，确认卡片内容后点「应用此点」。如果只有系统缓存，会显示「缓存定位 · N 分钟前」，不会伪装成实时 GPS。

**悬浮摇杆**：启动后自动出现。按住拖动方向，松手滑行减速；可切换走路 / 跑步 / 骑行、微调速度；`×` 收起，红色方块停止。

**路线录制**：

1. 启动模拟，让坐标开始移动。
2. 运动页 →「模拟路线」→「开始录制」。
3. 用摇杆、路线巡航或其他方式持续移动；卡片实时显示时长、距离、点数。
4. 点「结束并保存」生成「录制 MM-dd HH:mm」，出现在路线列表，可直接巡航或编辑。
5. 录制采样间隔约 1 秒，忽略距离小于 1.5 m 的点；少于 5 m 不保存。模拟停止时会尝试自动保存足够长的录制。

**Frida Sensor Bridge**：

1. 环境页 →「04 Frida 动态 Hook」，填写目标包名。
2. 点「生成 Hook 脚本」；应用把脚本写入 `Android/data/com.elsewhere.location/files/frida/`，Shizuku 可用时复制到 `/data/local/tmp`。
3. 按面板给出的命令，在电脑或 Termux 上运行 Frida：

```bash
adb pull '<脚本路径>' ./elsewhere-sensor.js
frida -U -f <目标包名> -l ./elsewhere-sensor.js --no-pause
```

4. 目标应用必须运行并注册对应传感器；脚本会改写 `SystemSensorManager$SensorEventQueue.dispatchSensorEvent` 收到的 values。

详细环境要求和限制：[`docs/FRIDA_SENSOR_BRIDGE.md`](docs/FRIDA_SENSOR_BRIDGE.md)。

> **Frida 不是无 Root 自动注入。** Shizuku 没有跨进程 ptrace 权限；没有 Root / Gadget 时，应用只能生成和部署脚本。Hook 也无法凭空创建设备没有的传感器事件。

### 缓存与限制

- 缓存清理由系统应用信息页手动完成；Shizuku 无法静默清理其他应用缓存。
- 应用可保存**可访问的外部缓存**快照；写回的所有者仍是 `shell`，目标应用不保证能读取。内部私有缓存无法备份。
- 无 Root 不能向第三方注入传感器流，也不能改变系统 Wi-Fi / 蓝牙 / 基站扫描结果。
- 注入位置会被系统标记为 mock；不绕过反作弊或 Mock 检测。
- 路线录制采样的是**当前模拟输出**，不是独立的真实 GPS 轨迹记录器。
- 更新检查只读取公开 GitHub Release，不自动下载安装；本分支尚未发布新 Release。

### 构建与验证

环境：JDK 17/21、Android SDK 35、Gradle Wrapper 8.9。

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

- 19 个 JVM 单元测试通过：Geo、ChineseGeo、MotionEngine、RouteRecorder、更新版本比较、设置序列化。
- `lintDebug` 通过。
- 已在 Android 15 模拟器（1080×2400）验证三路 mock 注入、悬浮摇杆、路线巡航、路线录制保存，以及 Frida 脚本生成和 `node --check` 语法检查。
- Frida 运行时注入尚未在真实 Root / Gadget 环境联调。

完整验证记录：[`docs/TESTING.md`](docs/TESTING.md)。

### 架构

[![Archify architecture](docs/architecture/elsewhere-architecture.png)](docs/architecture/elsewhere-architecture.html)

Archify 架构图覆盖主定位链路、路线录制、Shizuku 通道和外部 Frida 注入边界。可打开交互式 HTML，或查看 [`docs/DESIGN.md`](docs/DESIGN.md) 的设计记录。

主要模块：

```text
Compose UI
  └─ ElsewhereViewModel
       ├─ SettingsRepository (DataStore)
       ├─ RouteRecorder
       ├─ ShizukuManager ── Shizuku service (shell UID 2000)
       ├─ Frida Sensor Bridge ── [external Frida client/server] ── Target app
       └─ MockLocationService
            └─ MotionEngine
                 └─ MockLocationProvider ── Android LocationManager
```

### 许可

应用代码 MIT；字体 OFL；osmdroid / AndroidX / OkHttp / Shizuku 等遵循各自许可；地图、地理编码和 Frida 服务有独立条款。详见 [`LICENSE`](LICENSE) 与 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

---

## English

### What it is

ELSEWHERE is a **root-free Android location instrument**. Shizuku provides a shell-level channel; the app injects coordinates through Android's GPS / Network / Fused test providers. A full-screen map handles point selection and routes; a foreground service keeps the output alive; a system overlay joystick moves the position across apps.

The core location path does not require root. The **Frida Sensor Bridge is an optional external workflow** needing either a rooted `frida-server` or a Frida Gadget build of the target. Without an external injection environment the app generates and deploys the script; it never claims to have hooked another app.

### Features

- Full-screen map, fixed crosshair, drag-to-select, address/coordinate search.
- AMap raster tiles in GCJ-02 for display; OSM fallback; WGS-84 internally and for injection.
- One-tap start/stop; test providers are removed on stop.
- Two-step real GPS confirmation, including explicit cached-fix age.
- Walk / run / cycle modes, smooth speed envelopes, gait rhythm, steps, route loops, drift.
- System overlay joystick; automatic ring progress during route cruise.
- In-app joystick, route planning, save, replay.
- **Route recording** from the live simulated path into a replayable `RoutePlan`.
- Wi-Fi / Bluetooth / cell Location extras, presets, in-app sensor telemetry.
- App scan, cache size, manual system cache cleanup, external cache snapshots.
- **Frida Sensor Bridge** generating a real JavaScript hook for six Android sensor types.
- Shizuku / root / hook diagnostics and GitHub Release checks.

### What is real?

| Capability | Effect | Notes |
| --- | --- | --- |
| System location | **Real injection** | GPS / Network / Fused test providers; Android flags values as mock. |
| Position / motion / route / drift | **Real injection** | 5 Hz foreground service updates. |
| Floating joystick | **Real system overlay** | `TYPE_APPLICATION_OVERLAY`. |
| Route recording | **App-side sampling** | Samples the currently injected point. |
| In-app sensors | **App-only telemetry** | No root cannot expose sensor streams to other apps. |
| Frida sensor hook | **Real hook script, external injection** | Root frida-server or Frida Gadget; affects the target process only. |
| Wi-Fi / Bluetooth / cell | **Location extras** | Does not change radio scans. |
| Cache clearing | **Manual system page** | Shizuku shell lacks `INTERNAL_DELETE_CACHE_FILES`. |

### Install

The stable binary is [`v1.0.0`](https://github.com/CalciumSparks/ELSEWHERE/releases/tag/v1.0.0). For the new route recorder / Frida work, build `main`:

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Shizuku setup

Install and start [Shizuku](https://github.com/RikkaApps/Shizuku/releases), authorize ELSEWHERE, then tap **Authorize / Configure**. Without Shizuku, choose **ELSEWHERE** under Developer options → Select mock location app, and grant location, notification and overlay permissions.

### Frida Sensor Bridge

The app generates a configuration-specific JavaScript file. With Shizuku it can copy it to `/data/local/tmp`; the actual injection must be performed by an external Frida client:

```bash
adb pull '<script path>' ./elsewhere-sensor.js
frida -U -f <target.package> -l ./elsewhere-sensor.js --no-pause
```

The hook replaces values in `SystemSensorManager$SensorEventQueue.dispatchSensorEvent` for accelerometer, gyroscope, gravity, linear acceleration, step counter and step detector. The target must run and register the sensors; this cannot create hardware events that do not exist.

Requirements and troubleshooting: [`docs/FRIDA_SENSOR_BRIDGE.md`](docs/FRIDA_SENSOR_BRIDGE.md).

### Route recording

Start a mock session, open **Motion → Route plans → Start recording**, move via the joystick or a saved route, then **Stop and save**. Points closer than 1.5 m are ignored; less than 5 m is not saved. The result is a normal route plan and can be replayed, edited or deleted.

### Limits

- No anti-cheat or mock-detection bypass. Injected locations are marked mock.
- No system-wide sensor or radio injection without root/system privileges.
- Other apps' private caches cannot be cleared or backed up silently; external snapshots may not be readable by the target after write-back.
- Frida injection is external. Shizuku cannot ptrace another UID and cannot automatically attach Frida.
- The route recorder captures the simulated output, not an independent real-GPS track.
- The public v1.0.0 APK predates the two additions on `main`.

### Build and verification

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

19 JVM tests pass, Android Lint passes, and the Android 15 emulator QA covers mock injection, overlay joystick, route cruise, route recording, Frida script generation and JavaScript syntax. Actual Frida runtime injection has not yet been exercised against a rooted target.

See [`docs/TESTING.md`](docs/TESTING.md) and the interactive architecture at [`docs/architecture/elsewhere-architecture.html`](docs/architecture/elsewhere-architecture.html).

### License

MIT for the app code; fonts under OFL; third-party libraries and map/geocoding/Frida services retain their own terms. See [`LICENSE`](LICENSE) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
