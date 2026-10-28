package com.elsewhere.location.core

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    val isValid: Boolean get() = Geo.isValid(this)
}

@Serializable
enum class MotionMode(
    val title: String,
    val englishLabel: String,
    val defaultSpeedMps: Float,
) {
    WALK("走路", "WALK", 1.35f),
    RUN("跑步", "RUN", 3.2f),
    CYCLE("骑行", "CYCLE", 5.8f);

    val speedRange: ClosedFloatingPointRange<Float>
        get() = when (this) {
            WALK -> 0.6f..2.4f
            RUN -> 2.0f..6.5f
            CYCLE -> 3.0f..12.0f
        }
}

@Serializable
enum class MapLayer(val title: String, val attribution: String) {
    GAODE("高德地图", "© 高德地图"),
    OSM("OpenStreetMap", "© OpenStreetMap contributors"),
}

@Serializable
enum class DriftTempo(val title: String, val subtitle: String, val frequency: Double) {
    SLOW("沉缓", "低频漂移，像 GPS 在楼下迟疑", 0.12),
    NATURAL("自然", "城市峡谷常见的细碎移动", 0.32),
    RESTLESS("躁动", "更快、更明显的定位游移", 0.75),
}

@Serializable
data class DriftConfig(
    val enabled: Boolean = false,
    val amplitudeMeters: Float = 1.2f,
    val tempo: DriftTempo = DriftTempo.NATURAL,
)

@Serializable
data class WifiProfile(
    val enabled: Boolean = false,
    val ssid: String = "SEVEN-ELEVEN-5G",
    val bssid: String = "A4:2B:8C:19:7D:02",
    val rssi: Int = -57,
    val channel: Int = 149,
)

@Serializable
data class BluetoothProfile(
    val enabled: Boolean = false,
    val name: String = "AirPods Pro",
    val address: String = "3C:2A:4F:81:9B:20",
    val rssi: Int = -62,
)

@Serializable
data class CellProfile(
    val enabled: Boolean = false,
    val carrier: String = "中国移动",
    val mcc: String = "460",
    val mnc: String = "00",
    val areaCode: Int = 21987,
    val cellId: Int = 3735921,
    val networkType: String = "LTE",
    val signalDbm: Int = -91,
)

@Serializable
data class EnvironmentProfile(
    val wifi: WifiProfile = WifiProfile(),
    val bluetooth: BluetoothProfile = BluetoothProfile(),
    val cell: CellProfile = CellProfile(),
    val writeLocationExtras: Boolean = true,
    val preset: String = "quiet",
)

@Serializable
data class SensorConfig(
    val accelerometer: Boolean = true,
    val gyroscope: Boolean = true,
    val gravity: Boolean = true,
    val linearAcceleration: Boolean = true,
    val stepCounter: Boolean = true,
    val stepDetector: Boolean = true,
)

@Serializable
data class SpeedSettings(
    val walkMps: Float = 1.35f,
    val runMps: Float = 3.2f,
    val cycleMps: Float = 5.8f,
) {
    fun forMode(mode: MotionMode): Float = when (mode) {
        MotionMode.WALK -> walkMps
        MotionMode.RUN -> runMps
        MotionMode.CYCLE -> cycleMps
    }

    fun withModeSpeed(mode: MotionMode, speed: Float): SpeedSettings = when (mode) {
        MotionMode.WALK -> copy(walkMps = speed)
        MotionMode.RUN -> copy(runMps = speed)
        MotionMode.CYCLE -> copy(cycleMps = speed)
    }
}

@Serializable
data class RoutePlan(
    val id: String,
    val name: String,
    val points: List<GeoPoint>,
    val loop: Boolean = false,
    val mode: MotionMode = MotionMode.WALK,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    val distanceMeters: Double
        get() {
            val open = Geo.haversinePolyline(points)
            val closing = if (loop && points.size >= 2) {
                Geo.distanceMeters(points.last(), points.first())
            } else {
                0.0
            }
            return open + closing
        }
}

@Serializable
data class SavedPlace(
    val label: String,
    val detail: String,
    val latitude: Double,
    val longitude: Double,
) {
    val point: GeoPoint get() = GeoPoint(latitude, longitude)
}

@Serializable
data class CacheBackupRecord(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val fileName: String,
    val sizeBytes: Long,
    val createdAtEpochMs: Long,
)

@Serializable
data class AppSettings(
    val onboarded: Boolean = false,
    val mapLayer: MapLayer = MapLayer.GAODE,
    val lastPoint: GeoPoint = GeoPoint(31.23040, 121.47370),
    val lastPointLabel: String = "上海市 · 人民广场",
    val motionMode: MotionMode = MotionMode.WALK,
    val speeds: SpeedSettings = SpeedSettings(),
    val drift: DriftConfig = DriftConfig(),
    val environment: EnvironmentProfile = EnvironmentProfile(),
    val sensors: SensorConfig = SensorConfig(),
    val overlayEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val recentPlaces: List<SavedPlace> = emptyList(),
    val routes: List<RoutePlan> = emptyList(),
    val backups: List<CacheBackupRecord> = emptyList(),
    val updateRepo: String = "",
    val amapWebKey: String = "",
    val showSensorNotice: Boolean = true,
    val lastBackupAtEpochMs: Long = 0L,
)

@Serializable
data class SessionStartConfig(
    val point: GeoPoint,
    val mode: MotionMode,
    val baseSpeedMps: Float,
    val speedSettings: SpeedSettings = SpeedSettings(),
    val overlayEnabled: Boolean,
    val drift: DriftConfig,
    val environment: EnvironmentProfile,
    val sensors: SensorConfig,
    val route: RoutePlan? = null,
)

@Serializable
data class SensorTelemetry(
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 9.80665f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val gravityX: Float = 0f,
    val gravityY: Float = 0f,
    val gravityZ: Float = 9.80665f,
    val linearX: Float = 0f,
    val linearY: Float = 0f,
    val linearZ: Float = 0f,
    val stepDetected: Boolean = false,
    val stepCount: Long = 0L,
)

data class MockSessionState(
    val running: Boolean = false,
    val starting: Boolean = false,
    val basePoint: GeoPoint? = null,
    val effectivePoint: GeoPoint? = null,
    val mode: MotionMode = MotionMode.WALK,
    val speedMps: Float = 0f,
    val targetSpeedMps: Float = 0f,
    val baseSpeedMps: Float = 1.35f,
    val bearingDeg: Float = 0f,
    val elapsedSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val steps: Long = 0L,
    val cadenceSpm: Float = 0f,
    val routeId: String? = null,
    val routeName: String? = null,
    val routeProgress: Float = 0f,
    val routeComplete: Boolean = false,
    val overlayVisible: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val telemetry: SensorTelemetry = SensorTelemetry(),
    val track: List<GeoPoint> = emptyList(),
    val startedAtEpochMs: Long = 0L,
    val servedProviderCount: Int = 0,
    val driftActive: Boolean = false,
) {
    val isActive: Boolean get() = running || starting
    val currentPoint: GeoPoint? get() = effectivePoint ?: basePoint
}

data class GeocodeResult(
    val title: String,
    val subtitle: String,
    val point: GeoPoint,
    val source: String,
)

data class ReverseGeocodeResult(
    val title: String,
    val subtitle: String,
)

enum class CapabilityState { READY, LIMITED, BLOCKED, UNKNOWN }

data class CapabilityItem(
    val id: String,
    val title: String,
    val summary: String,
    val detail: String,
    val state: CapabilityState,
    val actionLabel: String? = null,
)

data class SystemReport(
    val items: List<CapabilityItem> = emptyList(),
    val generatedAtEpochMs: Long = 0L,
    val deviceSummary: String = "",
    val shizukuUid: Int? = null,
    val hookSignals: List<String> = emptyList(),
)

data class UpdateInfo(
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val downloadUrl: String?,
    val publishedAt: String,
    val isNewer: Boolean,
)

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<GeocodeResult> = emptyList(),
    val error: String? = null,
)


enum class BackupState { MISSING, READY }
