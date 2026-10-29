package com.elsewhere.location.data

import android.content.Context
import com.elsewhere.location.core.EnvironmentProfile
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.core.SensorConfig
import com.elsewhere.location.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * LSPatch / VirtualXposed companion module.
 *
 * The module APK is bundled in assets and exported through the share sheet.
 * Its motion config is read from the global setting below; the app writes that
 * setting through Shizuku when available, or the user can paste the printed
 * `adb shell settings put` command.
 */
class XposedModuleRepository(
    private val context: Context,
    private val shizuku: ShizukuManager,
) {
    companion object {
        const val CONFIG_KEY = "elsewhere_sensor_config"
        const val MODULE_ASSET = "xposed/elsewhere-sensor-xposed.apk"
        const val MODULE_FILE = "elsewhere-sensor-xposed.apk"
    }

    suspend fun exportModule(): Result<File> = withContext(Dispatchers.IO) {
        val directory = context.getExternalFilesDir("xposed") ?: context.filesDir
        if (!directory.exists() && !directory.mkdirs()) {
            return@withContext Result.failure(IllegalStateException("无法创建 Xposed 模块目录。"))
        }
        val output = File(directory, MODULE_FILE)
        try {
            context.assets.open(MODULE_ASSET).use { input ->
                output.outputStream().use { stream -> input.copyTo(stream) }
            }
            Result.success(output)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("无法导出 Xposed 模块。", error))
        }
    }

    fun configCommand(
        mode: MotionMode,
        speedMps: Float,
        sensors: SensorConfig,
        environment: EnvironmentProfile,
    ): String {
        val json = configJson(mode, speedMps, sensors, environment)
        return "adb shell settings put global $CONFIG_KEY ${shellQuote(json)}"
    }

    suspend fun writeConfig(
        mode: MotionMode,
        speedMps: Float,
        sensors: SensorConfig,
        environment: EnvironmentProfile,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!shizuku.status.value.ready) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Shizuku 未授权；可复制 ADB 配置命令，或先完成 Shizuku 授权。",
                ),
            )
        }
        val json = configJson(mode, speedMps, sensors, environment)
        val result = shizuku.runShell(
            "settings put global $CONFIG_KEY ${shellQuote(json)}",
            timeoutMs = 10_000,
        )
        if (!result.success) {
            return@withContext Result.failure(
                IllegalStateException(result.output.ifBlank { "写入 Xposed 模块配置失败。" }),
            )
        }
        Result.success("Xposed 模块配置已写入；传感器与无线环境在下次 dispatch / 系统 API 调用后生效。")
    }

    private fun configJson(
        mode: MotionMode,
        speedMps: Float,
        sensors: SensorConfig,
        environment: EnvironmentProfile,
    ): String = JSONObject().apply {
        put("enabled", true)
        put("mode", mode.name)
        put("speedMps", speedMps.toDouble())
        put("amplitude", 1.0)
        put(
            "sensors",
            JSONObject().apply {
                put("accelerometer", sensors.accelerometer)
                put("gyroscope", sensors.gyroscope)
                put("gravity", sensors.gravity)
                put("linearAcceleration", sensors.linearAcceleration)
                put("stepCounter", sensors.stepCounter)
                put("stepDetector", sensors.stepDetector)
            },
        )
        put(
            "environment",
            JSONObject().apply {
                put(
                    "wifi",
                    JSONObject().apply {
                        put("enabled", environment.wifi.enabled)
                        put("ssid", environment.wifi.ssid)
                        put("bssid", environment.wifi.bssid)
                        put("rssi", environment.wifi.rssi)
                        put("channel", environment.wifi.channel)
                    },
                )
                put(
                    "bluetooth",
                    JSONObject().apply {
                        put("enabled", environment.bluetooth.enabled)
                        put("name", environment.bluetooth.name)
                        put("address", environment.bluetooth.address)
                        put("rssi", environment.bluetooth.rssi)
                    },
                )
                put(
                    "cell",
                    JSONObject().apply {
                        put("enabled", environment.cell.enabled)
                        put("carrier", environment.cell.carrier)
                        put("mcc", environment.cell.mcc)
                        put("mnc", environment.cell.mnc)
                        put("areaCode", environment.cell.areaCode)
                        put("cellId", environment.cell.cellId)
                        put("networkType", environment.cell.networkType)
                        put("signalDbm", environment.cell.signalDbm)
                    },
                )
            },
        )
    }.toString()

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
