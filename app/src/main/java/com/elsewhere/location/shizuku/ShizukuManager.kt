package com.elsewhere.location.shizuku

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

data class ShizukuStatus(
    val installed: Boolean = false,
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val uid: Int? = null,
    val version: Int? = null,
    val initializing: Boolean = true,
    val error: String? = null,
) {
    val ready: Boolean get() = binderAlive && permissionGranted
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
) {
    val success: Boolean get() = exitCode == 0 && !timedOut
    val output: String get() = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n").trim()
}

data class GrantResult(
    val success: Boolean,
    val message: String,
)

class ShizukuManager(private val context: Context) {

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refresh()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        refresh()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            refresh()
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                _status.update { it.copy(error = "Shizuku 授权被拒绝，可以稍后在设置中重试。") }
            }
        }
    }

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        }
        refresh()
    }

    fun refresh() {
        val installed = isPackageInstalled(SHIZUKU_PACKAGE)
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = if (binderAlive) {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false)
        } else {
            false
        }
        val uid = if (binderAlive) runCatching { Shizuku.getUid() }.getOrNull() else null
        val version = if (binderAlive) runCatching { Shizuku.getVersion() }.getOrNull() else null
        _status.update {
            it.copy(
                installed = installed,
                binderAlive = binderAlive,
                permissionGranted = granted,
                uid = uid,
                version = version,
                initializing = false,
                error = when {
                    !installed -> "未安装 Shizuku。"
                    !binderAlive -> "Shizuku 服务未运行，请先在 Shizuku 中启动服务。"
                    !granted -> "尚未授予 Shizuku 访问权限。"
                    else -> null
                },
            )
        }
    }

    fun requestPermission() {
        refresh()
        if (!_status.value.binderAlive) {
            _status.update {
                it.copy(error = "请先启动 Shizuku 服务，再回到他乡授权。")
            }
            return
        }
        runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }.onFailure { error ->
            _status.update {
                it.copy(error = error.message ?: "无法发起 Shizuku 授权。")
            }
        }
    }

    fun isPackageInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    fun isMockLocationAllowed(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun isBatteryOptimizationIgnored(): Boolean {
        val power = context.getSystemService(android.os.PowerManager::class.java)
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    suspend fun grantMockLocation(): GrantResult {
        if (!_status.value.ready) return GrantResult(false, "请先完成 Shizuku 授权。")
        val packageName = context.packageName
        val result = runShell(
            "appops set $packageName android:mock_location allow",
            timeoutMs = 10_000,
        )
        if (!result.success) {
            return GrantResult(false, result.output.ifBlank { "模拟位置 AppOp 授权失败。" })
        }
        return if (isMockLocationAllowed()) {
            GrantResult(true, "已允许他乡作为模拟位置应用。")
        } else {
            GrantResult(
                false,
                "命令已执行，但系统仍未允许模拟位置。请到开发者选项 → 选择模拟位置信息应用 → 他乡。",
            )
        }
    }

    suspend fun grantOverlay(): GrantResult {
        if (!_status.value.ready) return GrantResult(false, "请先完成 Shizuku 授权。")
        val packageName = context.packageName
        val result = runShell(
            "appops set $packageName android:system_alert_window allow",
            timeoutMs = 10_000,
        )
        if (!result.success) {
            return GrantResult(false, result.output.ifBlank { "悬浮窗授权失败。" })
        }
        return if (canDrawOverlays()) {
            GrantResult(true, "已允许悬浮窗。")
        } else {
            GrantResult(false, "命令已执行，但系统未刷新悬浮窗权限。可在系统设置中手动开启。")
        }
    }

    suspend fun grantUsageStats(): GrantResult {
        if (!_status.value.ready) return GrantResult(false, "请先完成 Shizuku 授权。")
        val result = runShell(
            "appops set ${context.packageName} android:get_usage_stats allow",
            timeoutMs = 10_000,
        )
        return GrantResult(result.success, result.output.ifBlank { "UsageStats 授权命令已执行。" })
    }

    suspend fun forceStop(packageName: String): ShellResult {
        if (!isSafePackageName(packageName)) return ShellResult(-1, "", "非法的包名。")
        return runShell("am force-stop '$packageName'", timeoutMs = 20_000)
    }

    suspend fun collectDiagnosticsShell(): ShellResult {
        val packageName = context.packageName
        val script = """
            echo "prop.build.type=$(getprop ro.build.type)"
            echo "prop.build.version=$(getprop ro.build.version.release)"
            echo "prop.product=$(getprop ro.product.brand) $(getprop ro.product.model)"
            echo "which.su=$(which su 2>/dev/null || true)"
            echo "appop.mock=$(appops get $packageName android:mock_location 2>&1)"
            echo "appop.overlay=$(appops get $packageName android:system_alert_window 2>&1)"
            echo "lsposed=$(pm list packages org.lsposed.manager 2>/dev/null || true)"
            echo "xposed=$(pm list packages de.robv.android.xposed.installer 2>/dev/null || true)"
            echo "battery=$(dumpsys deviceidle whitelist 2>/dev/null | grep $packageName || true)"
        """.trimIndent()
        return runShell(script, timeoutMs = 30_000)
    }

    suspend fun runShell(command: String, timeoutMs: Long = 15_000): ShellResult {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return ShellResult(-1, "", "Shizuku 服务未运行。")
        }
        if (!runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false)
        ) {
            return ShellResult(-1, "", "尚未授予 Shizuku 权限。")
        }
        val factory = newProcessMethod ?: return ShellResult(
            -1,
            "",
            "当前 Shizuku 版本不提供 shell 进程入口，请升级 Shizuku 后重试。",
        )
        return withContext(Dispatchers.IO) {
            var process: java.lang.Process? = null
            try {
                val activeProcess = factory.invoke(
                    null,
                    arrayOf("sh", "-c", command),
                    null,
                    null,
                ) as? java.lang.Process
                    ?: return@withContext ShellResult(-1, "", "无法创建 Shizuku shell 进程。")
                process = activeProcess
                val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val work = ioScope.async {
                    val stdout = async { activeProcess.inputStream.bufferedReader().readText() }
                    val stderr = async { activeProcess.errorStream.bufferedReader().readText() }
                    val exited = try {
                        if (activeProcess is rikka.shizuku.ShizukuRemoteProcess) {
                            activeProcess.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        } else {
                            activeProcess.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                        }
                    } catch (_: IllegalThreadStateException) {
                        true
                    }
                    if (!exited) {
                        runCatching { activeProcess.destroy() }
                        ShellResult(
                            -1,
                            stdout.awaitOrEmpty(),
                            stderr.awaitOrEmpty(),
                            timedOut = true,
                        )
                    } else {
                        ShellResult(
                            runCatching { activeProcess.exitValue() }.getOrDefault(0),
                            stdout.awaitOrEmpty(),
                            stderr.awaitOrEmpty(),
                        )
                    }
                }
                val result = withTimeoutOrNull(timeoutMs + 6_000) { work.await() }
                if (result == null) {
                    runCatching { activeProcess.destroy() }
                    ioScope.cancel()
                    ShellResult(
                        -1,
                        "",
                        "Shell 命令执行超时（${timeoutMs / 1000}s），已尝试终止进程。",
                        timedOut = true,
                    )
                } else {
                    ioScope.cancel()
                    result
                }
            } catch (error: Exception) {
                process?.destroy()
                ShellResult(-1, "", error.message ?: "Shell 执行失败。")
            }
        }
    }

    /**
     * Shizuku 13 keeps its process factory private, but the returned type is a
     * public Process subclass. The same entry point is used by Shizuku's own
     * shell samples; reflection keeps this compatible and degrades to a clear
     * error if a future server release removes it.
     */
    private val newProcessMethod: java.lang.reflect.Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        }.getOrNull()
    }

    private suspend fun kotlinx.coroutines.Deferred<String>.awaitOrEmpty(): String =
        withTimeoutOrNull(2_500) { runCatching { this@awaitOrEmpty.await() }.getOrDefault("") } ?: ""

    fun settingsIntentForOverlay(): android.content.Intent? = if (Build.VERSION.SDK_INT >= 23) {
        android.content.Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        )
    } else {
        null
    }

    fun isSafePackageName(packageName: String): Boolean =
        packageName.isNotBlank() && packageName.length <= 200 &&
            packageName.matches(Regex("[A-Za-z0-9_.]+"))

    companion object {
        const val PERMISSION_REQUEST_CODE = 7_231
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_RELEASES_URL = "https://github.com/RikkaApps/Shizuku/releases"
    }
}
