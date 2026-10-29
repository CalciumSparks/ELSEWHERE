# 第三方声明 / Third-Party Notices

「他乡 / ELSEWHERE」的源代码以 MIT 发布。以下第三方组件、服务和数据不适用 MIT，遵循各自条款。

## 运行时依赖

- **osmdroid** — Apache License 2.0  
  https://github.com/osmdroid/osmdroid
- **Shizuku API / provider（dev.rikka.shizuku）** — Apache License 2.0  
  应用仅内置客户端库，不分发 Shizuku 服务 APK。  
  https://github.com/RikkaApps/Shizuku
- **AndroidX / Jetpack Compose / Material 3** — Apache License 2.0  
  https://developer.android.com/jetpack
- **OkHttp** — Apache License 2.0  
  https://square.github.io/okhttp/
- **kotlinx.coroutines / kotlinx.serialization** — Apache License 2.0  
  https://github.com/Kotlin/kotlinx.coroutines  
  https://github.com/Kotlin/kotlinx.serialization
- **Kotlin / Gradle / Android Gradle Plugin** — 各自的开源许可。

## 字体

- **Space Grotesk** — SIL Open Font License 1.1  
  许可全文：`app/src/main/res/raw/font_license_space_grotesk.txt`
- **JetBrains Mono** — SIL Open Font License 1.1  
  许可全文：`app/src/main/res/raw/font_license_jetbrains_mono.txt`

中文正文使用 Android 系统字体（设备厂商提供），本应用不嵌入系统中文字体。

## 地图、地理编码与网络数据

- **高德地图栅格瓦片** — 归高德 / AutoNavi 所有。应用以在线瓦片方式显示，不内置 Key；使用需遵守高德的服务条款和配额规定。  
  https://lbs.amap.com/
- **OpenStreetMap** — 地图数据 © OpenStreetMap contributors，ODbL。地图内显示归属。  
  https://www.openstreetmap.org/copyright
- **Photon** — 公共地理编码服务；请求发往 photon.komoot.io。请遵守其使用政策。  
  https://photon.komoot.io/
- **高德 Web 服务 API（可选）** — 用户自行提供 Key，受高德 Web 服务条款约束。
- **GitHub REST API** — 更新检查使用公开 Release 端点，受 GitHub 服务条款约束。  
  https://docs.github.com/en/rest

## 商标

Android、Google 是 Google LLC 的商标。高德、百度、Shizuku、OpenStreetMap 等名称和标识属于各自权利人。本项目与上述公司无隶属或背书关系。

## 免责声明

第三方地图、瓦片、接口和服务的可用性、内容、合规性由对应提供方负责。使用者应自行确认其使用场景、应用服务条款与所在地区法律。详见根目录 `README.md` 与 `LICENSE`。
## 外部工具与随包组件

- **Frida** — 动态插桩工具，采用其自身的 wxWindows Library Licence / 相关许可。他乡只生成并分发自己的 JavaScript Hook 脚本；Frida 客户端、frida-server 和 Frida Gadget 由用户另行安装。此项目不包含 Frida 二进制。
- **frida-jdwp-loader** — 第三方 MIT 许可的 JDWP 注入脚本。该脚本打包在应用 assets 中，并会随「JDWP 一键注入包」导出；许可证 [`frida-jdwp-loader.LICENSE`](app/src/main/assets/frida/frida-jdwp-loader.LICENSE) 同时包含在 ZIP 内。  
  https://github.com/frankheat/frida-jdwp-loader
- **LSPatch / VirtualXposed / Xposed** — 未集成，也未随应用分发；它们需要 patch APK 或虚拟空间，并需要单独的 Xposed 模块。
- **PriviSense** — ICSE 2026 Demo / arXiv:2601.22414 的研究工作，运行于 rooted Android；本项目未复制或集成其代码。  
  https://arxiv.org/abs/2601.22414
- **Archify** — 用于生成 `docs/architecture` 中的架构图；不是应用的运行时依赖。

Frida 的动态 Hook 需要 JDWP（仅 debuggable 目标）、Root + frida-server，或一个已打包 Frida Gadget 的目标 APK。使用前请确认目标应用、设备和环境的授权。
