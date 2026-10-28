package com.elsewhere.location.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.elsewhere.location.core.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Reads the platform's real location. The caller must ensure no mock session is
 * active, otherwise the platform would return our own injected location.
 */
class RealLocationProbe(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<RealLocationFix> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(SecurityException("尚未授予精确位置权限。"))
        }
        val manager = context.getSystemService(LocationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !manager.isLocationEnabled) {
            return Result.failure(IllegalStateException("系统位置服务已关闭，请先开启定位。"))
        }

        val lastKnown = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            return lastKnown?.let {
                Result.success(
                    RealLocationFix(
                        point = GeoPoint(it.latitude, it.longitude),
                        isCached = true,
                        accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                        ageMillis = (System.currentTimeMillis() - it.time).coerceAtLeast(0L),
                    ),
                )
            } ?: Result.failure(IllegalStateException("没有可用的定位提供者，请检查系统定位开关。"))
        }

        val fresh = withTimeoutOrNull(14_000) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                var finished = false
                continuation.invokeOnCancellation { cancellation.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    providers.first(),
                    cancellation,
                    ContextCompat.getMainExecutor(context),
                ) { location ->
                    if (!finished) {
                        finished = true
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                }
            }
        }

        val chosen = fresh ?: lastKnown
        return if (chosen != null) {
            val ageMillis = (System.currentTimeMillis() - chosen.time).coerceAtLeast(0L)
            Result.success(
                RealLocationFix(
                    point = GeoPoint(chosen.latitude, chosen.longitude),
                    isCached = fresh == null || ageMillis > 12_000L,
                    accuracyMeters = if (chosen.hasAccuracy()) chosen.accuracy else null,
                    ageMillis = ageMillis,
                ),
            )
        } else {
            Result.failure(IllegalStateException("暂时未获取到真实位置，请到开阔处或检查 GPS 信号后重试。"))
        }
    }
}


data class RealLocationFix(
    val point: GeoPoint,
    val isCached: Boolean,
    val accuracyMeters: Float?,
    val ageMillis: Long,
)