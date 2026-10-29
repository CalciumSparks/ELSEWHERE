# Frida Sensor Bridge

`他乡` 的 Frida 传感器桥是一套**外部注入**工作流，不是无 Root 的自动 Hook。

应用负责：

1. 根据当前运动模式、目标速度和传感器开关生成一份 JavaScript Hook 脚本。
2. 把脚本保存到应用外部文件目录。
3. 在 Shizuku 可用时，通过 shell 把脚本部署到 `/data/local/tmp/elsewhere-sensor.js`。
4. 给出在电脑 / Termux 上执行的 Frida 命令。

真正把脚本加载进目标进程的是 **Frida 客户端 + root frida-server**，或者一个已经把 **Frida Gadget** 打包进目标 APK 的构建。

> Shizuku 能让 shell 执行 AppOps 和文件操作，但它没有 ptrace 其他 UID 进程的权限。没有 Root / Gadget 时，必须在目标应用之外完成注入。

---

## 1. 环境

### A. Root + frida-server

适合测试设备 / 模拟器。

- 设备已 Root，并且 `su` 可执行。
- 设备上的 `frida-server` 与电脑/终端上的 Frida 客户端版本一致。
- 目标应用已安装；Hook 会在目标进程启动后生效。

常见启动方式：

```bash
# 把对应架构的 frida-server 放到设备
adb push frida-server /data/local/tmp/frida-server
adb shell su -c "chmod 755 /data/local/tmp/frida-server"

# Root shell 中启动
adb shell su -c "/data/local/tmp/frida-server -l 0.0.0.0:27042"
```

这条命令仅说明前提；`frida-server` 二进制不随他乡分发。

### B. Frida Gadget（重打包）

适合无法 Root 的测试设备，但必须重新签名目标 APK：

- 将 Frida Gadget 的 `.so` 加入目标 APK。
- 修改目标 APK 的加载入口或使用 Gadget 配置。
- 重新签名安装；这会改变目标应用签名，可能清除其数据。
- Gadget 启动后，使用 Frida 客户端 attach 并加载他乡生成的脚本。

这不是通用“普通用户开箱即用”的路径；请只在你有权修改的测试 App 上使用。

---

## 2. 在应用内生成脚本

打开：

**环境 → 04 Frida 动态 Hook**

1. 填写 `TARGET PACKAGE`，例如 `com.android.chrome`。
2. 选择运动模式和传感器开关（使用「运动」页的模式、速度与传感器开关）。
3. 点「检测环境」查看 Shizuku / `su` / `frida-server` 状态。
4. 点「生成 Hook 脚本」。

应用会生成：

```text
/storage/emulated/0/Android/data/com.elsewhere.location/files/frida/elsewhere-sensor.js
```

如果 Shizuku 已授权，还会尝试复制到：

```text
/data/local/tmp/elsewhere-sensor.js
```

面板会给出两条命令：

```bash
# 电脑端：先拉取脚本
adb pull '/storage/emulated/0/Android/data/com.elsewhere.location/files/frida/elsewhere-sensor.js' ./elsewhere-sensor.js

# 电脑端：spawn 目标并加载 Hook
frida -U -f com.android.chrome -l ./elsewhere-sensor.js --no-pause
```

如果 Frida 在设备上的 Termux 中运行，并且脚本已部署：

```bash
frida -U -f com.android.chrome -l /data/local/tmp/elsewhere-sensor.js --no-pause
```

> 使用 `-f` 时 Frida 会重新启动目标进程。目标应用的未保存状态会丢失。只想 attach 到已运行进程时，使用 `frida -U -n <进程名> -l ...`，但脚本必须在目标订阅传感器之前加载，映射才完整。

---

## 3. Hook 实现

脚本：`app/src/main/assets/frida/elsewhere_sensor_hook.js`

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

传感器类型通过 `SystemSensorManager` 内部的 handle → Sensor 映射解析；脚本同时拦截 `registerListenerImpl` 记录 handle。极少数厂商字段不同、导致 handle 无法映射时，3 轴事件会保持原值并输出一条 `unmapped-sensor` 日志，而不是错误改写。

脚本的配置由应用注入：

```js
const CONFIG = {
  targetPackage: "com.android.chrome",
  mode: "WALK",
  speedMps: 1.35,
  sensors: { accelerometer: true, /* ... */ },
  amplitude: 1.0
};
```

当前 Hook 是 **Java 层 dispatch 改写**：

- 它不会凭空创建设备没有的硬件传感器事件。目标必须先注册监听并收到事件。
- 它不会修改系统 sensor service 的全局状态；只影响被注入的目标进程。
- 若目标使用 native 传感器路径、独立进程或主动完整性检测，脚本可能无效。
- 不绕过反作弊 / Mock 检测。传感器 Hook 与定位 Mock 是两条独立链路。

---

## 4. 验证状态

本轮已验证：

- JavaScript 通过 `node --check` 语法检查。
- 应用可生成带目标包名、模式、速度、六个传感器开关的脚本。
- 生成文件可被 `adb pull` 读取；内容中的配置正确。
- 环境检测和错误路径不会崩溃。
- 在 Android 15 模拟器上**未执行真实 Frida 动态注入**：当前环境没有配置 host Frida 客户端/对应 frida-server。脚本的运行时 dispatch 行为需要在上述 Root 或 Gadget 环境中验证。

---

## 5. 排错

- **`Frida server not found`**  
  应用只检测进程名。确认 frida-server 以 root 运行，并与客户端版本匹配。
- **脚本加载了，但没有事件**  
  目标没有注册传感器，或尚未进入使用传感器的页面；也可能是 native 路径未被 hook。
- **只有部分轴/通道变化**  
  handle 映射失败时，脚本会保留原始值。查看 Frida 输出中的 `unmapped-sensor`。
- **`frida -f` 后目标闪退**  
  目标有签名/完整性校验或 Frida 检测；本工具不绕过。
- **脚本路径在电脑上打不开**  
  Android 11+ 的 `Android/data` 受限制；使用应用提供的 `adb pull` 命令或先部署到 `/data/local/tmp`。

---

## 6. 安全与合规

- 仅在你有权修改的测试设备、测试 App 和授权场景中使用。
- 不要用于绕过反作弊、欺诈或未经授权的监控。
- Frida 本身、目标应用、Shizuku 和高德 / 百度服务各有自己的条款。
