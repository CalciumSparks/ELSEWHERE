# Frida Sensor Bridge

`他乡` 的 Frida 传感器桥是一套**外部注入**工作流。它现在支持三条路径，其中 **JDWP 是免 Root 路线，但只适用于 debuggable 目标**：

| 路径 | Root | 目标 APK 改写 | 适用范围 | 注入器 |
| --- | --- | --- | --- | --- |
| **JDWP** | 不需要 | 不需要 | 仅 `android:debuggable="true"` 的 App，或 `ro.debuggable=1` 的模拟器 | `frida-jdwp-loader` + ADB |
| **Root** | 需要 | 不需要 | 任意 App | `frida-server` |
| **Gadget** | 不需要 | 需要重打包并重新签名 | 可修改的 App | 内置 `frida-gadget` |
| **LSPatch / VirtualXposed** | 不需要 | 需要 patch 或虚拟空间 | 可修改/可虚拟化的 App | Xposed 模块（本项目未内置） |

应用负责生成脚本、展示目标是否可调试、给出对应命令；**它本身不 attach 其他 App**。

> 常见误解：Frida 的“免 Root”并不等于“任意 App 都能注入”。JDWP 需要目标进程开启调试；没有调试标志的生产包仍然不行。

---

## 1. JDWP：无需 Root、无需重打包

这是调试版 App、模拟器和应用逻辑测试的首选路径。

### 前提

- 目标 App 的 manifest 中 `android:debuggable="true"`；或设备/模拟器 `ro.debuggable=1`（例如 userdebug 模拟器）。
- 电脑安装了 Python 3、ADB，并能访问目标设备。
- 目标 App 已安装在设备上。
- 不要求 Root，不要求重打包，不要求目标声明 `INTERNET`。

应用内「检测环境」会通过 `PackageManager` 检查 `FLAG_DEBUGGABLE`；结果在状态行显示为 `TARGET DEBUGGABLE / NOT DEBUGGABLE / NOT INSTALLED`。

### 准备 loader

```bash
git clone https://github.com/frankheat/frida-jdwp-loader.git
cd frida-jdwp-loader
```

`frida-jdwp-loader` 是一个 MIT 许可的 Python 3 脚本，使用 JDWP 和 ADB 把 frida-gadget 注入可调试进程。

> **必须使用 Frida Gadget 16.x。** Frida 17 起，Gadget 不再内置 Java bridge；本脚本使用 `Java.perform`，在 17.x Gadget 上会直接失效。本项目固定 **16.7.19**，并已实测可注入、可改写传感器。

### 在应用内

1. 打开 **环境 → 04 Frida 动态 Hook**。
2. `INJECTION PATH` 选择 **JDWP**。
3. 填入目标包名，点「检测环境」。状态行会显示目标是否 installed / debuggable。
4. 点「生成 Hook 脚本」，从结果面板复制命令；或点 **「导出 / 分享一键注入包」**。

### 一键注入包

应用导出 `elsewhere-jdwp-kit.zip`，并打开 Android 分享面板。ZIP 内容：

```text
elsewhere-sensor.js          生成的 Hook 脚本（含当前模式、速度、传感器配置）
frida-jdwp-loader.py         vendored MIT loader，已修 Windows CRLF 包名校验
frida-jdwp-loader.LICENSE    MIT 许可
run-jdwp-windows.ps1         Windows 启动脚本
run-jdwp-unix.sh             macOS / Linux 启动脚本
target.txt                   目标包名
frida-version.txt            固定 16.7.19
README.txt                   使用说明
KIT-INFO.txt                 生成信息与目标 debuggable 状态
```

电脑端：

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File .\run-jdwp-windows.ps1
```

```bash
# macOS / Linux
chmod +x run-jdwp-unix.sh
./run-jdwp-unix.sh
```

启动脚本会检查 adb/Python 3，读取 target.txt；如果目录中存在 `frida-gadget-*.so` 就使用本地文件，否则读取 `frida-version.txt` 并传 `-g 16.7.19`，由 loader 下载对应 ABI 的 Gadget。

> Android 应用无法充当 ADB host，也不能替另一个 UID 创建 `adb forward`。因此“完整集成”的边界是：他乡负责生成、打包、目标检查和分享；实际 JDWP 连接必须由电脑或 Termux 上的 ADB 完成。这个边界不是 UI 偷懒，而是 JDWP/adb 传输层的平台约束。

### 实测：应用导出 → 电脑启动 → 真实传感器改写

在 Android 15 x86_64 模拟器、无 Root、无 APK 重打包：

1. 应用生成脚本并导出 ZIP（24,839 B）；ZIP 内是脚本、vendored loader、启动脚本、target.txt、frida-version.txt 和许可证。
2. 解压 ZIP，放入本机 x86_64 **Frida 16.7.19** Gadget，运行 `run-jdwp-windows.ps1`。
3. 启动脚本完成 adb/Python 检查、目标重启、JDWP handshake、Gadget 上传和 `Runtime.load`；loader 输出 `Library injection successful!`，退出码 0。
4. 另建一个最小 debuggable 传感器目标 App，注册加速度计、陀螺仪、重力与线性加速度。未注入前，系统给的 values 是设备的固定/噪声值；注入后：
   - logcat 的 `ELSEWHERE-SENSOR` 出现 `ready` 事件；
   - 重力读数从约 `0.003, 9.773, 0.808` 变为 `0.0, 0.0, 9.81`；
   - 加速度读数变为随时间变化的 `x/y/z`（脚本的步态曲线）。
5. 这验证了 Hook 的真实效果，而不仅是脚本生成/注入成功。

> 最初 QA 使用 Frida 17.24.3 Gadget 时，JDWP 注入成功但脚本完全不动；排查后确认是 Frida 17 移除了 Java bridge。换用 16.7.19 后 `Java.perform` 正常，传感器值被改写。这也是本项目固定 16.7.19 的原因。

### 电脑端命令

```bash
adb pull '/storage/emulated/0/Android/data/com.elsewhere.location/files/frida/elsewhere-sensor.js' ./elsewhere-sensor.js

python frida-jdwp-loader.py frida \
  -n com.example.target \
  -i script \
  -l ./elsewhere-sensor.js \
  -g 16.7.19
```

如果 loader 位于其他目录，`-l` 使用脚本的绝对路径，或在 `frida-jdwp-loader` 目录里执行。

`-i script` 表示脚本在目标进程内自主运行。输出通过 Android logcat 查看：

```bash
adb logcat -s ELSEWHERE-SENSOR:*
```

脚本在 JDWP/Gadget 自主模式下没有 Frida CLI 消息通道，因此同时使用 `android.util.Log` 和 `send()`；前者用于 JDWP，后者用于 CLI attach。

### Windows 注意事项

QA 在 Windows 主机上观察到 `frida-jdwp-loader` 的包名校验可能因 adb 输出的 CRLF 失败，报：

```text
Unexpected error: Invalid package name: com.example.target
```

这是第三方 loader 的换行处理问题，不是目标 App 的问题。可在 WSL 下运行，或把本地 loader 中的包名比较改为：

```python
if line.removeprefix("package:").strip() == self.package_name:
```

一键包中的 `frida-jdwp-loader.py` 已经包含该修正；只有手工使用外部 loader 时才需要自行处理。

---

## 2. Root frida-server

适用于任意目标（包括不可调试的生产包），但设备必须已 Root。

```bash
adb push frida-server /data/local/tmp/frida-server
adb shell su -c "chmod 755 /data/local/tmp/frida-server"
adb shell su -c "/data/local/tmp/frida-server -l 0.0.0.0:27042"
```

电脑端：

```bash
adb pull '<脚本路径>' ./elsewhere-sensor.js
frida -U -f <目标包名> -l ./elsewhere-sensor.js --no-pause
```

`frida-server` 版本应与 host Frida 客户端一致；二进制不随他乡分发。

---

## 3. Frida Gadget

适用于非 debuggable、但可以修改和重新签名的目标 APK：

1. 把对应 ABI 的 `frida-gadget.so` 加入目标 APK。
2. 修改加载入口或 Gadget 配置，使其在进程启动时加载。
3. 重新签名并安装；这可能清除目标数据，也容易被完整性校验发现。
4. 用 Frida 客户端 attach 到 Gadget，并加载他乡生成的脚本。

Gadget 路径不是“普通用户开箱即用”的免 Root 方案；它属于重打包测试流程。

---

## 4. LSPatch / VirtualXposed

这两条也是常见的免 Root 思路，但和 JDWP 不同：

- **LSPatch**：把目标 APK patch 成带 Xposed loader 的版本，再安装。需要提供一个 Xposed 模块；APK 签名改变，部分应用会拒绝或崩溃。
- **VirtualXposed**：把目标 App 安装进虚拟空间，在虚拟容器内加载 Xposed 模块。目标不在原 UID/原进程里运行，权限、数据和系统行为都可能不同。

它们需要**专门的 Xposed 传感器 Hook 模块**；本项目当前没有内置该模块，也没有把 LSPatch / VirtualXposed 集成到运行时。JDWP 是当前“不重打包、不 Root”的首选实现。

如果后续增加 Xposed 模块，它可以复用本项目的传感器曲线和模式配置，但需要独立的模块 APK 与 patch/virtual 安装步骤。

---

## 5. PriviSense（相关研究，不是免 Root 方案）

[PriviSense: A Frida-Based Framework for Multi-Sensor Spoofing on Android](https://arxiv.org/abs/2601.22414)（ICSE 2026 Demonstrations）提出了面向 Android 的多传感器欺骗框架，支持随时间变化的传感器流，并可在未修改的 App 中注入。

需要明确：论文摘要明确其运行环境是 **rooted Android devices**；代码按研究申请提供。它证明了 Frida 传感器欺骗的可行性，但不是“无 Root 任意 App”的方案，也不是本项目 JDWP 路径的替代。本项目的实现是独立的 Java 层 Hook，**没有复制或集成 PriviSense 代码**。

---

## 6. Hook 实现

脚本：[`app/src/main/assets/frida/elsewhere_sensor_hook.js`](../app/src/main/assets/frida/elsewhere_sensor_hook.js)

它 hook：

```text
android.hardware.SystemSensorManager$SensorEventQueue.dispatchSensorEvent(...)
```

在目标应用收到 `SensorEvent` 之前修改 `float[] values`：

| Android 类型 | 生成值 |
| --- | --- |
| `TYPE_ACCELEROMETER` (1) | 重力 + 步态正弦 + 噪声 |
| `TYPE_GYROSCOPE` (4) | 小幅转向角速度 |
| `TYPE_GRAVITY` (9) | 0, 0, 9.81 |
| `TYPE_LINEAR_ACCELERATION` (10) | 步态线性加速度 |
| `TYPE_STEP_COUNTER` (19) | 按步频累计的整数步数 |
| `TYPE_STEP_DETECTOR` (18) | 每次事件置 1 |

传感器类型通过 `SystemSensorManager` 内部的 handle → Sensor 映射解析；脚本同时拦截 `registerListenerImpl` 记录 handle。极少数厂商字段不同、导致 handle 无法映射时，3 轴事件会保持原值并通过 logcat/`send` 输出一条 `unmapped-sensor`，不会错误改写。

配置由应用注入：

```js
const CONFIG = {
  targetPackage: "com.example.target",
  mode: "WALK",
  speedMps: 1.35,
  sensors: { accelerometer: true, /* ... */ },
  amplitude: 1.0
};
```

### 限制

- 不会凭空创建设备没有的硬件传感器事件；目标必须先注册并收到事件。
- 不会修改系统 sensor service 的全局状态；只影响被注入的目标进程。
- 若目标走 native 传感器路径、独立进程或完整性检测，脚本可能无效。
- 不绕过反作弊或 Mock 检测；定位 Mock 与传感器 Hook 是两条独立链路。
- JDWP 需要目标可调试；LSPatch / VirtualXposed 需要 patch/虚拟空间和 Xposed 模块。

---

## 7. 排错

- **`Invalid package name`（Windows）**  
  先看上面的 CRLF 说明；在 WSL 中运行，或给 loader 的包名校验加 `.strip()`。
- **JDWP 注入成功，但 `ELSEWHERE-SENSOR` 没有 ready、传感器值也不变**  
  先确认使用的是 **Frida Gadget 16.x**。17.x 移除了 Java bridge，本脚本会静默失效；一键包固定 16.7.19。若版本正确，再检查目标是否注册传感器、是否收到事件，或是否走 native 路径。用 `adb logcat -s ELSEWHERE-SENSOR:*` 查看 `ready` / `unmapped-sensor`。
- **`Unable to find debugger` / JDWP 连接失败**  
  确认目标 debuggable；某些 ROM 会禁止 `am set-debug-app`，或需要在开发者选项里允许调试。
- **脚本路径在电脑上打不开**  
  Android 11+ 的 `Android/data` 受限制；使用应用给出的 `adb pull` 命令，或先部署到 `/data/local/tmp`。
- **`frida -f` 后目标闪退**  
  目标有签名/完整性校验或 Frida 检测；本工具不绕过。

---

## 8. 安全与合规

- 只在有权修改的测试设备、测试 App 和授权场景中使用。
- 不要用于绕过反作弊、欺诈或未经授权的监控。
- Frida、frida-jdwp-loader、目标应用、Shizuku 和高德/百度服务各有自己的条款。
- `frida-jdwp-loader` 是第三方 MIT 工具；本项目只生成自己的 JavaScript Hook 脚本。


