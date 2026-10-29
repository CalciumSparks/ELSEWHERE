package com.elsewhere.location.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.core.SensorConfig
import com.elsewhere.location.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class FridaInjectionMode(
    val shortLabel: String,
    val title: String,
    val detail: String,
) {
    JDWP(
        shortLabel = "JDWP",
        title = "免 Root · JDWP",
        detail = "frida-jdwp-loader · 仅 debuggable App",
    ),
    ROOT_SERVER(
        shortLabel = "ROOT",
        title = "Root · frida-server",
        detail = "任意 App · 需要 Root frida-server",
    ),
    GADGET(
        shortLabel = "GADGET",
        title = "Frida Gadget",
        detail = "重打包目标 APK · 无需 Root",
    ),
}

data class FridaTargetInfo(
    val packageName: String,
    val installed: Boolean = false,
    val debuggable: Boolean = false,
    val label: String? = null,
    val note: String = "",
)

data class FridaEnvironment(
    val checked: Boolean = false,
    val shizukuReady: Boolean = false,
    val suBinary: Boolean = false,
    val fridaServer: Boolean = false,
    val debuggable: Boolean = false,
    val output: String = "",
)

data class FridaScriptArtifact(
    val targetPackage: String,
    val localPath: String,
    val deployedPath: String?,
    val hostCommand: String,
    val deviceCommand: String,
    val sensorSummary: String,
    val note: String,
    val generatedAtEpochMs: Long,
    val injectionMode: FridaInjectionMode,
    val targetInfo: FridaTargetInfo,
)

/**
 * Generates the external Frida sensor bridge.
 *
 * Three delivery paths exist:
 * - JDWP / frida-jdwp-loader: no root, but only debuggable target processes;
 * - root frida-server: any target, requires a rooted device;
 * - Frida Gadget: no root, but the target APK must be repackaged.
 *
 * Shizuku only deploys the generated script and checks the shell environment;
 * it cannot attach Frida to another UID itself.
 */
class FridaSensorRepository(
    private val context: Context,
    private val shizuku: ShizukuManager,
) {
    private val json = Json { encodeDefaults = true; prettyPrint = false }

    suspend fun inspectEnvironment(): Result<FridaEnvironment> = withContext(Dispatchers.IO) {
        if (!shizuku.status.value.ready) {
            return@withContext Result.success(
                FridaEnvironment(
                    checked = true,
                    shizukuReady = false,
                    output = "Shizuku 未授权；仍可生成脚本，但无法自动部署到 /data/local/tmp。",
                ),
            )
        }
        val result = shizuku.runShell(
            """
            printf 'SU='; (command -v su || true)
            printf '\nFRIDA='; (ps -A 2>/dev/null | grep -i '[f]rida-server' | head -n 1 || true)
            printf '\nDEBUG='; getprop ro.debuggable
            """.trimIndent(),
            timeoutMs = 10_000,
        )
        if (!result.success) {
            return@withContext Result.failure(
                IllegalStateException(result.output.ifBlank { "无法读取 Frida 环境。" }),
            )
        }
        val lines = result.output.lineSequence().toList()
        val su = lines.firstOrNull { it.startsWith("SU=") }?.removePrefix("SU=")?.trim().orEmpty()
        val frida = lines.firstOrNull { it.startsWith("FRIDA=") }?.removePrefix("FRIDA=")?.trim().orEmpty()
        val debug = lines.firstOrNull { it.startsWith("DEBUG=") }?.removePrefix("DEBUG=")?.trim().orEmpty()
        Result.success(
            FridaEnvironment(
                checked = true,
                shizukuReady = true,
                suBinary = su.isNotBlank() && su != "none",
                fridaServer = frida.isNotBlank(),
                debuggable = debug == "1",
                output = result.output.take(1_000),
            ),
        )
    }

    /**
     * PackageManager's FLAG_DEBUGGABLE is the practical no-root precondition for
     * frida-jdwp-loader. On a userdebug image ro.debuggable=1 can relax it; that
     * device-level fact is reported separately by [inspectEnvironment].
     */
    suspend fun inspectTarget(packageName: String): Result<FridaTargetInfo> =
        withContext(Dispatchers.IO) {
            if (!shizuku.isSafePackageName(packageName)) {
                return@withContext Result.failure(
                    IllegalArgumentException("目标包名格式无效，例如 com.example.app。"),
                )
            }
            val appInfo = try {
                context.packageManager.getApplicationInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return@withContext Result.success(
                    FridaTargetInfo(
                        packageName = packageName,
                        installed = false,
                        note = "目标包未安装，或当前用户不可见。",
                    ),
                )
            }
            val debuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            Result.success(
                FridaTargetInfo(
                    packageName = packageName,
                    installed = true,
                    debuggable = debuggable,
                    label = runCatching {
                        context.packageManager.getApplicationLabel(appInfo).toString()
                    }.getOrNull(),
                    note = if (debuggable) {
                        "目标声明 FLAG_DEBUGGABLE，可使用 JDWP 免 Root 路径。"
                    } else {
                        "目标不是 debuggable；JDWP 路径不可用（除非设备 ro.debuggable=1 或改用 Root/Gadget）。"
                    },
                ),
            )
        }

    suspend fun generate(
        targetPackage: String,
        mode: MotionMode,
        speedMps: Float,
        sensors: SensorConfig,
        injectionMode: FridaInjectionMode = FridaInjectionMode.JDWP,
        targetInfo: FridaTargetInfo? = null,
        deviceDebuggable: Boolean = false,
    ): Result<FridaScriptArtifact> = withContext(Dispatchers.IO) {
        val packageName = targetPackage.trim()
        if (!shizuku.isSafePackageName(packageName)) {
            return@withContext Result.failure(
                IllegalArgumentException("目标包名格式无效，例如 com.example.app。"),
            )
        }
        val config = HookConfig(
            targetPackage = packageName,
            mode = mode.name,
            speedMps = speedMps.toDouble(),
            sensors = sensors,
        )
        val template = try {
            context.assets.open("frida/elsewhere_sensor_hook.js")
                .bufferedReader()
                .use { it.readText() }
        } catch (error: Exception) {
            return@withContext Result.failure(
                IllegalStateException("无法读取内置 Frida 脚本。", error),
            )
        }
        val script = template.replace("__ELSEWHERE_CONFIG__", json.encodeToString(config))
        val directory = context.getExternalFilesDir("frida")
            ?: context.filesDir
        if (!directory.exists() && !directory.mkdirs()) {
            return@withContext Result.failure(IllegalStateException("无法创建 Frida 脚本目录。"))
        }
        val localFile = File(directory, "elsewhere-sensor.js")
        localFile.writeText(script)

        val target = targetInfo
            ?.takeIf { it.packageName == packageName }
            ?: inspectTarget(packageName).getOrNull()
            ?: FridaTargetInfo(packageName, installed = false)

        var deployedPath: String? = null
        var deployNote = ""
        if (shizuku.status.value.ready) {
            val remote = "/data/local/tmp/elsewhere-sensor.js"
            val command = "cp ${shellQuote(localFile.absolutePath)} $remote && chmod 644 $remote"
            val result = shizuku.runShell(command, timeoutMs = 12_000)
            if (result.success) {
                deployedPath = remote
            } else {
                deployNote = result.output.ifBlank { "Shizuku 部署脚本失败。" }
            }
        } else {
            deployNote = "Shizuku 未就绪；脚本只保存在应用外部目录。"
        }

        val source = deployedPath ?: localFile.absolutePath
        val pullCommand = "adb pull ${shellQuote(source)} ./elsewhere-sensor.js"
        val hostCommand = when (injectionMode) {
            FridaInjectionMode.JDWP -> buildString {
                appendLine("# Requires Python 3, adb, and frida-jdwp-loader in this directory")
                appendLine(pullCommand)
                append("python frida-jdwp-loader.py frida -n ")
                    .append(packageName)
                    .append(" -i script -l ./elsewhere-sensor.js")
            }
            FridaInjectionMode.ROOT_SERVER -> buildString {
                appendLine(pullCommand)
                append("frida -U -f ")
                    .append(packageName)
                    .append(" -l ./elsewhere-sensor.js --no-pause")
            }
            FridaInjectionMode.GADGET -> buildString {
                appendLine(pullCommand)
                append("frida -U -n ")
                    .append(packageName)
                    .append(" -l ./elsewhere-sensor.js")
            }
        }
        val deviceCommand = when (injectionMode) {
            FridaInjectionMode.ROOT_SERVER ->
                "frida -U -f $packageName -l /data/local/tmp/elsewhere-sensor.js --no-pause"
            FridaInjectionMode.JDWP -> ""
            FridaInjectionMode.GADGET -> ""
        }
        val targetWarning = when {
            !target.installed -> "目标包未安装或不可见；脚本仍已生成。"
            injectionMode == FridaInjectionMode.JDWP &&
                !target.debuggable &&
                !deviceDebuggable -> "JDWP 仅适用于 debuggable 目标；该包当前不可 JDWP 调试。"
            injectionMode == FridaInjectionMode.JDWP && !target.debuggable ->
                "设备 ro.debuggable=1；目标未标记 debuggable，JDWP 是否可用取决于系统。"
            injectionMode == FridaInjectionMode.JDWP ->
                "JDWP 可调试；将脚本拉到电脑后运行 frida-jdwp-loader。"
            injectionMode == FridaInjectionMode.GADGET ->
                "Gadget 路径要求目标 APK 已重打包并包含 Frida Gadget。"
            else -> ""
        }
        val note = listOf(deployNote, targetWarning)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val sensorSummary = buildList {
            if (sensors.accelerometer) add("ACCEL")
            if (sensors.gyroscope) add("GYRO")
            if (sensors.gravity) add("GRAVITY")
            if (sensors.linearAcceleration) add("LINEAR")
            if (sensors.stepCounter) add("STEPS")
            if (sensors.stepDetector) add("DETECT")
        }.joinToString(" · ")

        Result.success(
            FridaScriptArtifact(
                targetPackage = packageName,
                localPath = localFile.absolutePath,
                deployedPath = deployedPath,
                hostCommand = hostCommand,
                deviceCommand = deviceCommand,
                sensorSummary = sensorSummary,
                note = note,
                generatedAtEpochMs = System.currentTimeMillis(),
                injectionMode = injectionMode,
                targetInfo = target,
            ),
        )
    }

    /**
     * Builds a self-contained host kit for the JDWP path. Android cannot act as
     * an ADB host or forward JDWP for another app; the kit contains the
     * generated script, the vendored loader, launcher scripts and a README so
     * the host-side half is one command instead of manual assembly.
     */
    suspend fun createJdwpKit(artifact: FridaScriptArtifact): Result<File> =
        withContext(Dispatchers.IO) {
            if (artifact.injectionMode != FridaInjectionMode.JDWP) {
                return@withContext Result.failure(
                    IllegalStateException("只有 JDWP 路径可以生成免 Root 注入包。"),
                )
            }
            val scriptFile = File(artifact.localPath)
            if (!scriptFile.isFile) {
                return@withContext Result.failure(
                    IllegalStateException("注入脚本不存在，请重新生成 Hook 脚本。"),
                )
            }
            val directory = context.getExternalFilesDir("frida") ?: context.filesDir
            if (!directory.exists() && !directory.mkdirs()) {
                return@withContext Result.failure(IllegalStateException("无法创建注入包目录。"))
            }
            val kit = File(directory, "elsewhere-jdwp-kit.zip")
            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(kit))).use { zip ->
                    addFileEntry(zip, "elsewhere-sensor.js", scriptFile)
                    addTextEntry(zip, "target.txt", artifact.targetPackage + "\n")
                    addAssetEntry(zip, "frida/frida-jdwp-loader.py", "frida-jdwp-loader.py")
                    addAssetEntry(
                        zip,
                        "frida/frida-jdwp-loader.LICENSE",
                        "LICENSE-frida-jdwp-loader.txt",
                    )
                    addAssetEntry(zip, "frida/JDWP-KIT-README.txt", "README.txt")
                    addAssetEntry(zip, "frida/run-jdwp-windows.ps1", "run-jdwp-windows.ps1")
                    addAssetEntry(zip, "frida/run-jdwp-unix.sh", "run-jdwp-unix.sh")
                    addTextEntry(
                        zip,
                        "KIT-INFO.txt",
                        buildString {
                            appendLine("ELSEWHERE JDWP no-root kit")
                            appendLine("target=${artifact.targetPackage}")
                            appendLine("mode=${artifact.injectionMode.shortLabel}")
                            appendLine("sensors=${artifact.sensorSummary}")
                            appendLine("generated=${artifact.generatedAtEpochMs}")
                            appendLine("targetDebuggable=${artifact.targetInfo.debuggable}")
                            appendLine()
                            appendLine("Requires: adb, Python 3, and a debuggable target.")
                            appendLine("No root; no APK repackaging.")
                        },
                    )
                }
            } catch (error: Exception) {
                kit.delete()
                return@withContext Result.failure(
                    IllegalStateException("生成 JDWP 注入包失败。", error),
                )
            }
            Result.success(kit)
        }

    private fun addFileEntry(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addTextEntry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun addAssetEntry(zip: ZipOutputStream, assetPath: String, name: String) {
        zip.putNextEntry(ZipEntry(name))
        context.assets.open(assetPath).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    @Serializable
    private data class HookConfig(
        val targetPackage: String,
        val mode: String,
        val speedMps: Double,
        val sensors: SensorConfig,
        val amplitude: Double = 1.0,
    )
}
