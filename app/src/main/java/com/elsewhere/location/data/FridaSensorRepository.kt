package com.elsewhere.location.data

import android.content.Context
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.core.SensorConfig
import com.elsewhere.location.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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
)

/**
 * Generates the external Frida sensor bridge. It can deploy the script through
 * Shizuku's shell channel, but it cannot attach to another app: that requires
 * a root frida-server or a Frida Gadget build of the target.
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

    suspend fun generate(
        targetPackage: String,
        mode: MotionMode,
        speedMps: Float,
        sensors: SensorConfig,
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

        val hostSource = deployedPath ?: localFile.absolutePath
        val hostCommand = buildString {
            append("adb pull ").append(shellQuote(hostSource)).append(" ./elsewhere-sensor.js\n")
            append("frida -U -f ").append(packageName)
                .append(" -l ./elsewhere-sensor.js --no-pause")
        }
        val deviceCommand = "frida -U -f $packageName -l " +
            (deployedPath ?: "/data/local/tmp/elsewhere-sensor.js") + " --no-pause"
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
                note = deployNote,
                generatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
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
