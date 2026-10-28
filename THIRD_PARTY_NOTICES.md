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
