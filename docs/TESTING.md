# 验证记录 · 他乡 / ELSEWHERE 1.0.0

验证时间：2026-10-28  
验证对象：`com.elsewhere.location` 1.0.0-debug / 1.0.0-release  
验证环境：Windows 11 · JDK 21 · Gradle 8.9 · Android SDK 35  
模拟器：`elsewhere_api35` · Android 15 · 1080×2400 · 420dpi · headless  
Shizuku：13.6.0，服务端以 shell（UID 2000）运行

---

## 1. 自动化结果

### JVM 单元测试

命令：

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:ANDROID_HOME='D:\Android\sdk'
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

结果：**16 / 16 通过**。

覆盖：

- `GeoTest`：haversine、方位角、最短角度收敛、距离格式化、WGS-84 → GCJ-02 往返、境外坐标不变。
- `MotionEngineTest`：手动摇杆加速、松手滑行停止、路线到点完成、环线回程、快速骑行到点、漂移偏移边界、模式切换。
- `UpdateRepositoryTest`：不同段数、`v` 前缀、`-dev` 后缀的版本比较。
- `SettingsSerializationTest`：路线 / 坐标 / 模式往返、缺省字段默认值。

### Android Lint

```powershell
.\gradlew.bat :app:lintDebug --no-daemon
```

结果：**通过**。保留的警告主要是 Compose `Modifier` 参数顺序、`String.format` 显式 Locale 建议、未使用资源、SDK 级别冗余判断和 Shizuku provider 的 exported 提示；没有阻塞级错误。

### Release 构建与签名

```powershell
.\gradlew.bat :app:assembleRelease --no-daemon
# zipalign + apksigner，详见 README
apksigner verify --print-certs dist\elsewhere-1.0.0.apk
```

结果：

- APK 安装成功，`versionName=1.0.0`，`versionCode=1`。
- 签名证书 SHA-256：`27780366d4d1a041c0eb99b8b99bfafb9374de4d35af4ed840e422f2598349d0`。
- `dist/SHA256SUMS.txt` 记录发行包与调试包的 SHA-256。

---

## 2. 手工验收

### 2.1 首次引导

- 3 页一次通过，可前进/后退/跳过。
- 页 1 的能力条完整显示；页 2 在通道已就绪时隐藏「稍后配置」入口；页 3 权限状态与系统一致。
- 页 2 的 Shizuku 状态显示 `Binder 正常 · API v13`、授权、Mock AppOp 三个 READY。
- 页 3 显示精确位置、通知、悬浮窗、模拟位置通道全部已就绪。
- 证据：[`00-onboarding.png`](screenshots/00-onboarding.png)、[`01-onboarding-shizuku.png`](screenshots/01-onboarding-shizuku.png)、[`02-onboarding-permissions.png`](screenshots/02-onboarding-permissions.png)。

### 2.2 地图与搜索

- 高德瓦片正常加载，GCJ-02 显示与 WGS-84 注入一致；缩放到人民广场附近，准星落点与地图要素对应。
- OpenStreetMap 图层可切换；远端网络不佳时只显示网格，不崩溃。
- 搜索 `Shanghai` 返回 Photon 结果；选择「上海市」后地图移动到对应中心点。
- 搜索结果面板、输入框、软键盘、结果行、关闭按钮均可用。
- 证据：[`03-map.png`](screenshots/03-map.png)、[`16-search.png`](screenshots/16-search.png)。

### 2.3 真实 GPS 对齐

- 静止时点击「真实」，界面进入 `LOCKING GPS`，14 秒超时后回退到系统缓存位置。
- 卡片明确显示「缓存定位 · 12 分钟前」，坐标和地址解析完成后可点击「应用此点」或「取消」。
- 没有把缓存位置标成实时 GPS。真实 GPS 在室内不可得时，这条路径仍可操作。
- 证据：[`17-real-gps.png`](screenshots/17-real-gps.png)。

### 2.4 系统 mock 注入与停止

启动后，`dumpsys location` 同时出现 GPS / Network / Fused 三路 mock：

```text
last location=Location[gps 31.231741,121.472629 hAcc=3.2 ... mock {
  Bundle[{mockLocation=true,
          elsewhere.source=shizuku_mock_provider,
          elsewhere.motion_mode=WALK,
          elsewhere.step_count=7,
          ...}]}]
```

停止后：

- 前台服务记录消失。
- 测试提供者的 mock override 不再存在。
- `Location` 恢复由系统提供者产生；应用状态回到 STANDBY。

### 2.5 悬浮摇杆与路线

- 启动模拟后悬浮窗自动显示。
- 单指按住摇杆拖动可改变 bearing；松手减速。
- 拖动面板顶部可移动；`×` 收起；红方块停止。
- 保存的路线（3 航点 / 306 m）可一键巡航。
- 开始巡航后，悬浮窗自动切换为环形进度：`路线巡航 / AUTO MODE`、百分比、距离、`AUTO` 标签。
- 叠加的路线线、轨迹线和实时位置都按当前地图图层转换。
- 证据：[`26-route-overlay.png`](screenshots/26-route-overlay.png)。

### 2.6 路线编辑

- 地图「路线」进入编辑，连续点击地图添加航点。
- 底部实时显示航点数量与总里程；可命名、选模式、开启环线。
- `保存路线` 和 `保存并巡航` 都可用；已保存路线出现在「运动 → 模拟路线」，可编辑 / 删除。
- 快速连续点击时，地图自动缩放的动画会吞掉部分点击；按正常节奏点击时工作正常。这是已知交互问题，已记录在下一轮改进中。

### 2.7 环境参数

- 场景预设会填充 Wi-Fi / 蓝牙 / 基站字段，可直接编辑。
- 开启「写入 Location extras」后，`dumpsys location` 的 Bundle 中出现：

```text
wifi_ssid=SEVEN-ELEVEN-5G
wifi_bssid=A4:2B:8C:19:7D:02
wifi_rssi=-57
bluetooth_name=AirPods Pro
bluetooth_rssi=-62
cell_carrier=中国移动
cell_mcc=460 / cell_mnc=00 / cell_lac=21987 / cell_cid=3735921
cell_network_type=LTE
```

- 这些字段不会改变系统 Wi-Fi / 蓝牙 / 蜂窝扫描；界面用 `APP-SIDE EXTRAS` 和提示卡明确说明。
- 证据：[`21-environment.png`](screenshots/21-environment.png)、[`22-environment-sources.png`](screenshots/22-environment-sources.png)。

### 2.8 传感器

- 应用内传感器卡片在空闲时显示加速度 `z 9.81`、陀螺仪 `0`、步数 `0`、步伐检测 `WAIT`。
- 运动时数值由 `MotionEngine` 推演，不向系统或第三方提供传感器流；界面标注 `APP-ONLY` / `应用内模拟 / 非系统注入`。
- 本项只验证应用内显示和推演；没有、也不会声称第三方 App 能读到这些值。

### 2.9 应用与缓存

- 扫描 19 个带桌面入口的应用，DEX 扫描完成；Chrome 显示系统应用和备份标记。
- Chrome 缓存占用通过 UsageStats 读取为 `172.0 KB`。
- 手动清理弹窗打开系统 App Info 页面；流程文案明确提示「存储和缓存 → 清除缓存」。
- 备份生成 `com.android.chrome-<timestamp>.tgz`（测试样例 160 B）。
- 写回成功，提示：

```text
已写回「Chrome」的外部缓存快照。受系统文件所有权限制，目标应用不保证能读取。
```

- 写回后文件权限为 `-rw-rw-rw-`，所有者为 `shell:shell`；这验证了「写回不是完整恢复」的现实。
- 证据：[`08-apps.png`](screenshots/08-apps.png)、[`09-app-detail.png`](screenshots/09-app-detail.png)。

### 2.10 系统诊断

- Shizuku 服务/授权、Mock AppOp、精确位置、悬浮窗、通知、系统定位开关均显示 READY。
- 电池优化显示 LIMITED，可打开系统白名单页。
- Hook 检测在当前模拟器上显示「检测到 su」；LSPosed/Xposed 未检测到。这只是探测，不是依赖。
- 「反模拟检测」明确显示 LIMITED / 不绕过。
- 证据：[`07-settings.png`](screenshots/07-settings.png)、[`07b-settings-diagnostics.png`](screenshots/07b-settings-diagnostics.png)。

### 2.11 GitHub 更新检查

- 更新源已配置为 [`CalciumSparks/ELSEWHERE`](https://github.com/CalciumSparks/ELSEWHERE)，并已发布 `v1.0.0` Release（APK + SHA256SUMS 资产）。
- 应用版本同为 `1.0.0`，检查返回 `tag_name=v1.0.0`、发布说明和 APK 资产，界面正确显示「已是最新」而不会误报更新。
- 从 GitHub 下载的 Release APK SHA-256 与本地构建一致：`1d7e1f7a7fa15ac4be5a2dcbfef231434f20383c10d77807c2daa7c0dea256d5`。
- 无 Release / 网络异常路径此前也已验证，分别显示空状态和错误信息，不崩溃。
- 证据：[`29-update-check.png`](screenshots/29-update-check.png)。

---

## 3. 未验证 / 残余风险

- 没有物理设备矩阵；悬浮窗、AppOp、厂商后台限制在不同 OEM 上可能不同。
- 正式实时 GPS 未在户外真机验证；模拟器的 GPS 注入未能刷新，使用了缓存路径并正确标注。
- 没有 Compose instrumentation / Espresso 测试；大多依赖手工验收。
- 外部缓存写回没有用目标应用自身的正常进程读取验证；系统文件所有权限制仍然存在。
- 传感器与无线电注入按设计不实现，不是“待修 bug”。
- AMap 瓦片、Photon 地理编码、GitHub API 都依赖第三方网络与条款。
- 快速连续添加航点时，自动缩放动画会延迟后续点击的落点；属于已知交互问题。
- 8sp 微标签在小屏/强光下的可读性需要进一步真机评估。

---

## 4. 复现命令

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:ANDROID_HOME='D:\Android\sdk'
$env:ANDROID_SDK_ROOT='D:\Android\sdk'

.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
.\gradlew.bat :app:assembleRelease --no-daemon
```

安装：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
# 或
adb install dist\elsewhere-1.0.0.apk
```

---

## 5. 截图索引

完整截图目录见 [`screenshots/`](screenshots/)。重点是：

- 地图与准星：`03-map.png`
- 真实 GPS 缓存标注：`17-real-gps.png`
- 悬浮路线巡航：`26-route-overlay.png`
- 运动 / 传感器：`13-motion.png`、`14-motion-lower.png`
- 环境参数：`21-environment.png`、`22-environment-sources.png`
- 应用缓存：`08-apps.png`、`09-app-detail.png`
- 系统诊断：`07-settings.png`、`07b-settings-diagnostics.png`
- 更新检查：`29-update-check.png`
