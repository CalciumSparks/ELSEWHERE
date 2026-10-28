package com.elsewhere.location.location

import android.Manifest
import android.app.AppOpsManager
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.elsewhere.location.core.EnvironmentProfile
import com.elsewhere.location.core.GeoPoint
import com.elsewhere.location.core.SensorTelemetry
import kotlin.math.cos
import kotlin.math.sin

data class MockProviderInstallResult(
    val providers: List<String>,
    val failures: List<String>,
) {
    val success: Boolean get() = providers.isNotEmpty()
}

class MockLocationProvider(private val context: Context) {

    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val activeProviders = mutableListOf<String>()

    @SuppressLint("WrongConstant")
    fun install(): Result<MockProviderInstallResult> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(SecurityException("尚未授予精确位置权限。"))
        }
        if (!mockLocationAppOpAllowed()) {
            return Result.failure(
                IllegalStateException(
                    "系统尚未允许他乡模拟位置。请在开发者选项中选择模拟位置信息应用，或使用 Shizuku 授权。",
                ),
            )
        }
        val failures = mutableListOf<String>()
        val installed = mutableListOf<String>()

        val specs = buildList {
            add(ProviderSpec(LocationManager.GPS_PROVIDER, requiresNetwork = false, requiresSatellite = true))
            add(ProviderSpec(LocationManager.NETWORK_PROVIDER, requiresNetwork = true, requiresSatellite = false))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(ProviderSpec(LocationManager.FUSED_PROVIDER, requiresNetwork = false, requiresSatellite = false))
            }
        }

        specs.forEach { spec ->
            runCatching {
                @Suppress("DEPRECATION")
                runCatching { locationManager.removeTestProvider(spec.name) }
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    spec.name,
                    spec.requiresNetwork,
                    spec.requiresSatellite,
                    false,
                    false,
                    true,
                    true,
                    true,
                    Criteria.POWER_LOW,
                    if (spec.name == LocationManager.NETWORK_PROVIDER) Criteria.ACCURACY_COARSE
                    else Criteria.ACCURACY_FINE,
                )
                locationManager.setTestProviderEnabled(spec.name, true)
                installed += spec.name
            }.onFailure { error ->
                failures += "${spec.name}: ${error.message ?: error.javaClass.simpleName}"
            }
        }

        activeProviders.clear()
        activeProviders.addAll(installed)
        if (installed.isEmpty()) {
            return Result.failure(
                IllegalStateException(
                    "无法启用测试定位提供者。请确认开发者选项中的模拟位置应用已设为他乡。" +
                        failures.joinToString("；"),
                ),
            )
        }
        return Result.success(MockProviderInstallResult(installed.toList(), failures.toList()))
    }

    fun push(
        point: GeoPoint,
        speedMps: Float,
        bearingDeg: Float,
        environment: EnvironmentProfile,
        telemetry: SensorTelemetry,
        modeLabel: String,
    ): Result<Int> {
        if (activeProviders.isEmpty()) return Result.failure(IllegalStateException("测试定位提供者未启用。"))
        var delivered = 0
        val failures = mutableListOf<String>()
        activeProviders.forEach { provider ->
            runCatching {
                val location = Location(provider).apply {
                    latitude = point.latitude
                    longitude = point.longitude
                    accuracy = if (provider == LocationManager.GPS_PROVIDER) 3.2f else 12.0f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    altitude = 18.0
                    speed = speedMps.coerceAtLeast(0f)
                    bearing = bearingDeg
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        verticalAccuracyMeters = 4.0f
                        speedAccuracyMetersPerSecond = 0.35f
                        bearingAccuracyDegrees = 2.0f
                    }
                    extras = buildExtras(environment, telemetry, modeLabel)
                }
                locationManager.setTestProviderLocation(provider, location)
                delivered += 1
            }.onFailure { failures += "$provider: ${it.message}" }
        }
        return if (delivered == 0) {
            Result.failure(IllegalStateException(failures.joinToString("；").ifBlank { "定位注入失败。" }))
        } else {
            Result.success(delivered)
        }
    }

    fun teardown() {
        activeProviders.forEach { provider ->
            runCatching {
                @Suppress("DEPRECATION")
                locationManager.setTestProviderEnabled(provider, false)
                @Suppress("DEPRECATION")
                locationManager.removeTestProvider(provider)
            }
        }
        activeProviders.clear()
    }

    fun providerCount(): Int = activeProviders.size

    private fun mockLocationAppOpAllowed(): Boolean {
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

    private fun buildExtras(
        environment: EnvironmentProfile,
        telemetry: SensorTelemetry,
        modeLabel: String,
    ): Bundle {
        val extras = Bundle().apply {
            putString("elsewhere.source", "shizuku_mock_provider")
            putString("elsewhere.motion_mode", modeLabel)
            putLong("elsewhere.step_count", telemetry.stepCount)
            putBoolean("mockLocation", true)
        }
        if (!environment.writeLocationExtras) return extras

        if (environment.wifi.enabled) {
            extras.putString("wifi_ssid", environment.wifi.ssid)
            extras.putString("wifi_bssid", environment.wifi.bssid)
            extras.putInt("wifi_rssi", environment.wifi.rssi)
            extras.putInt("wifi_channel", environment.wifi.channel)
            extras.putString("elsewhere.wifi.ssid", environment.wifi.ssid)
            extras.putString("elsewhere.wifi.bssid", environment.wifi.bssid)
        }
        if (environment.bluetooth.enabled) {
            extras.putString("bluetooth_name", environment.bluetooth.name)
            extras.putString("bluetooth_address", environment.bluetooth.address)
            extras.putInt("bluetooth_rssi", environment.bluetooth.rssi)
            extras.putString("elsewhere.bluetooth.name", environment.bluetooth.name)
        }
        if (environment.cell.enabled) {
            extras.putString("cell_carrier", environment.cell.carrier)
            extras.putString("cell_mcc", environment.cell.mcc)
            extras.putString("cell_mnc", environment.cell.mnc)
            extras.putInt("cell_lac", environment.cell.areaCode)
            extras.putInt("cell_cid", environment.cell.cellId)
            extras.putString("cell_network_type", environment.cell.networkType)
            extras.putInt("cell_signal_dbm", environment.cell.signalDbm)
            extras.putString("elsewhere.cell.carrier", environment.cell.carrier)
        }
        return extras
    }

    private data class ProviderSpec(
        val name: String,
        val requiresNetwork: Boolean,
        val requiresSatellite: Boolean,
    )
}
