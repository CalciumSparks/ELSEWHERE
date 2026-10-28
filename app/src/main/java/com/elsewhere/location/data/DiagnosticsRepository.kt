package com.elsewhere.location.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.elsewhere.location.core.CapabilityItem
import com.elsewhere.location.core.CapabilityState
import com.elsewhere.location.core.SystemReport
import com.elsewhere.location.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DiagnosticsRepository(
    private val context: Context,
    private val shizukuManager: ShizukuManager,
) {

    suspend fun buildReport(): SystemReport = withContext(Dispatchers.IO) {
        val shizuku = shizukuManager.status.value
        val shell = if (shizuku.ready) {
            runCatching { shizukuManager.collectDiagnosticsShell() }.getOrNull()
        } else {
            null
        }
        val shellLines = shell?.stdout.orEmpty()
            .lineSequence()
            .filter { '=' in it }
            .associate { line ->
                val key = line.substringBefore('=')
                val value = line.substringAfter('=', "")
                key to value
            }
        val hasSu = shellLines["which.su"]?.isNotBlank() == true || runCatching {
            java.io.File("/system/bin/su").exists() || java.io.File("/system/xbin/su").exists()
        }.getOrDefault(false)
        val hasLsposed = shellLines["lsposed"]?.contains("org.lsposed.manager") == true ||
            shizukuManager.isPackageInstalled("org.lsposed.manager")
        val hasXposed = shellLines["xposed"]?.contains("xposed") == true ||
            shizukuManager.isPackageInstalled("de.robv.android.xposed.installer")

        val locationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val locationManager = context.getSystemService(LocationManager::class.java)
        val systemLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
                .getOrDefault(false)
        }
        val mockAllowed = shizukuManager.isMockLocationAllowed()
        val overlayAllowed = shizukuManager.canDrawOverlays()
        val batteryIgnored = shizukuManager.isBatteryOptimizationIgnored()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notificationsEnabled = notificationsGranted &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                notificationManager.areNotificationsEnabled())

        val items = buildList {
            add(
                CapabilityItem(
                    id = "shizuku_service",
                    title = "Shizuku 服务",
                    summary = if (shizuku.binderAlive) "已连接" else if (shizuku.installed) "已安装 · 未运行" else "未安装",
                    detail = when {
                        shizuku.binderAlive -> "Binder 正常工作，API v${shizuku.version ?: "?"}，UID ${shizuku.uid ?: "?"}。"
                        shizuku.installed -> "打开 Shizuku 并按其中的说明启动服务，然后回到他乡授权。"
                        else -> "他乡的无 Root 注入依赖 Shizuku，请安装并启动它的服务。"
                    },
                    state = if (shizuku.binderAlive) CapabilityState.READY else CapabilityState.BLOCKED,
                    actionLabel = if (!shizuku.binderAlive) {
                        if (shizuku.installed) "启动说明" else "安装 Shizuku"
                    } else {
                        null
                    },
                ),
            )
            add(
                CapabilityItem(
                    id = "shizuku_permission",
                    title = "Shizuku 授权",
                    summary = if (shizuku.permissionGranted) "已授权" else "等待授权",
                    detail = if (shizuku.permissionGranted) {
                        "他乡可以执行受控的 shell 命令，仅用于模拟位置、悬浮窗与缓存维护。"
                    } else {
                        "授权后，他乡才能自动完成模拟位置 AppOp、悬浮窗与外部缓存快照。"
                    },
                    state = if (shizuku.permissionGranted) CapabilityState.READY else CapabilityState.BLOCKED,
                    actionLabel = if (shizuku.permissionGranted) null else "请求授权",
                ),
            )
            add(
                CapabilityItem(
                    id = "mock_appop",
                    title = "模拟位置 AppOp",
                    summary = if (mockAllowed) "已允许" else "未允许",
                    detail = if (mockAllowed) {
                        "系统允许他乡注册测试位置提供者。此通路不依赖 Hook 或 Root。"
                    } else {
                        "可在 Shizuku 中一键授予，也可在开发者选项 → 选择模拟位置信息应用中选择“他乡”。"
                    },
                    state = if (mockAllowed) CapabilityState.READY else CapabilityState.BLOCKED,
                    actionLabel = if (mockAllowed) null else "自动授权",
                ),
            )
            add(
                CapabilityItem(
                    id = "fine_location",
                    title = "精确位置权限",
                    summary = if (locationPermission) "已授予" else "未授予",
                    detail = if (locationPermission) {
                        "用于读取真实 GPS，以及让前台定位服务稳定持有模拟通道。"
                    } else {
                        "系统要求前台定位服务与测试 Provider 具备精确位置权限。"
                    },
                    state = if (locationPermission) CapabilityState.READY else CapabilityState.BLOCKED,
                    actionLabel = if (locationPermission) null else "授予权限",
                ),
            )
            add(
                CapabilityItem(
                    id = "overlay",
                    title = "悬浮摇杆",
                    summary = if (overlayAllowed) "已允许" else "未允许",
                    detail = if (overlayAllowed) {
                        "可从运动控制台打开悬浮摇杆，在其他应用上方控制方向。"
                    } else {
                        "悬浮摇杆需要“显示在其他应用上层”。可通过 Shizuku 自动授权或跳转系统设置。"
                    },
                    state = if (overlayAllowed) CapabilityState.READY else CapabilityState.LIMITED,
                    actionLabel = if (overlayAllowed) null else "授权悬浮窗",
                ),
            )
            add(
                CapabilityItem(
                    id = "notifications",
                    title = "前台服务通知",
                    summary = if (notificationsEnabled) "可见" else "受限",
                    detail = if (notificationsEnabled) {
                        "模拟运行时的常驻通知可让你随时停止服务。"
                    } else {
                        "通知被关闭时，服务和悬浮摇杆仍可运行，但系统入口可能只在通知栏底部显示。"
                    },
                    state = if (notificationsEnabled) CapabilityState.READY else CapabilityState.LIMITED,
                    actionLabel = if (notificationsEnabled) null else "通知设置",
                ),
            )
            add(
                CapabilityItem(
                    id = "battery",
                    title = "后台持续运行",
                    summary = if (batteryIgnored) "不受电池优化限制" else "可能被系统回收",
                    detail = if (batteryIgnored) {
                        "已忽略电池优化。长时间移动模拟仍会耗电，这是预期行为。"
                    } else {
                        "建议为长时间路线巡航将本应用加入电池优化白名单。"
                    },
                    state = if (batteryIgnored) CapabilityState.READY else CapabilityState.LIMITED,
                    actionLabel = if (batteryIgnored) null else "加入白名单",
                ),
            )
            add(
                CapabilityItem(
                    id = "system_location",
                    title = "系统位置开关",
                    summary = if (systemLocation) "已开启" else "已关闭",
                    detail = if (systemLocation) {
                        "系统定位服务可用；模拟位置会覆盖向监听者分发的位置。"
                    } else {
                        "系统定位总开关关闭时，部分消费者应用可能不会请求位置。"
                    },
                    state = if (systemLocation) CapabilityState.READY else CapabilityState.LIMITED,
                    actionLabel = if (systemLocation) null else "位置设置",
                ),
            )
            add(
                CapabilityItem(
                    id = "hook",
                    title = "系统 Hook 状态",
                    summary = when {
                        hasLsposed -> "检测到 LSPosed"
                        hasXposed -> "检测到 Xposed"
                        hasSu -> "检测到 su"
                        else -> "未检测到 Hook / Root"
                    },
                    detail = buildString {
                        append("本版本使用 Shizuku 测试 Provider，不依赖 Hook。")
                        append("系统级 Hook 只能改变第三方传感器与无线扫描结果，无 Root 方案不提供。")
                        if (hasSu || hasLsposed || hasXposed) {
                            append("检测到系统修改环境；他乡不会自动调用它。")
                        }
                    },
                    state = if (hasLsposed || hasXposed || hasSu) CapabilityState.READY else CapabilityState.LIMITED,
                ),
            )
            add(
                CapabilityItem(
                    id = "anti_mock",
                    title = "反模拟检测",
                    summary = "不绕过",
                    detail = "部分应用会通过 Play Integrity、Mock 标记或业务风控识别虚拟位置。本应用不会且无法通过 Shizuku 绕过这些检测。",
                    state = CapabilityState.LIMITED,
                ),
            )
        }

        val hookSignals = buildList {
            add(if (hasSu) "检测到 su 可执行文件" else "未检测到 su")
            add(if (hasLsposed) "LSPosed 管理器存在" else "未检测到 LSPosed")
            add(if (hasXposed) "Xposed 管理器存在" else "未检测到 Xposed")
            add("Mock AppOp: " + if (mockAllowed) "ALLOWED" else "DENIED")
            add("Shizuku: " + if (shizuku.ready) "READY" else "NOT_READY")
            add(shellLines["appop.mock"]?.takeIf { it.isNotBlank() } ?: "appops get 未执行")
        }

        val device = shellLines["prop.product"]?.takeIf { it.isNotBlank() }
            ?: "${Build.BRAND} ${Build.MODEL}"
        SystemReport(
            items = items,
            generatedAtEpochMs = System.currentTimeMillis(),
            deviceSummary = "$device · Android ${Build.VERSION.RELEASE}",
            shizukuUid = shizuku.uid,
            hookSignals = hookSignals,
        )
    }
}
