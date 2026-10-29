package com.elsewhere.location.ui

import com.elsewhere.location.core.CacheBackupRecord
import com.elsewhere.location.core.GeoPoint
import com.elsewhere.location.core.GeocodeResult
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.core.SavedPlace
import com.elsewhere.location.core.SystemReport
import com.elsewhere.location.core.UpdateInfo
import com.elsewhere.location.data.FridaEnvironment
import com.elsewhere.location.data.FridaInjectionMode
import com.elsewhere.location.data.FridaScriptArtifact
import com.elsewhere.location.data.FridaTargetInfo
import com.elsewhere.location.data.TargetAppInfo

sealed interface MapCommand {
    data class MoveTo(
        val point: GeoPoint,
        val zoom: Double? = null,
        val animate: Boolean = true,
    ) : MapCommand

    data class FitRoute(val points: List<GeoPoint>) : MapCommand
}

data class MapUiState(
    val center: GeoPoint = GeoPoint(31.23040, 121.47370),
    val addressTitle: String = "上海市 · 人民广场",
    val addressSubtitle: String = "拖动地图移动准星，或搜索一个地址",
    val reverseLoading: Boolean = false,
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchResults: List<GeocodeResult> = emptyList(),
    val searchError: String? = null,
    val recentPlaces: List<SavedPlace> = emptyList(),
    val pendingRealPoint: GeoPoint? = null,
    val pendingRealTitle: String = "",
    val pendingRealSubtitle: String = "",
    val pendingRealTitleLoading: Boolean = false,
    val pendingRealIsCached: Boolean = false,
    val pendingRealAccuracyMeters: Float? = null,
    val pendingRealAgeSeconds: Long = 0L,
    val locatingReal: Boolean = false,
    val preparingStart: Boolean = false,
    val routeEditing: Boolean = false,
    val routeDraft: List<GeoPoint> = emptyList(),
    val routeName: String = "",
    val routeLoop: Boolean = false,
    val routeMode: MotionMode = MotionMode.WALK,
    val routeError: String? = null,
) {
    val routeDistanceMeters: Double
        get() {
            var total = 0.0
            for (index in 0 until routeDraft.lastIndex) {
                total += com.elsewhere.location.core.Geo.distanceMeters(
                    routeDraft[index],
                    routeDraft[index + 1],
                )
            }
            if (routeLoop && routeDraft.size >= 2) {
                total += com.elsewhere.location.core.Geo.distanceMeters(
                    routeDraft.last(),
                    routeDraft.first(),
                )
            }
            return total
        }
}

data class PermissionUiState(
    val fineLocation: Boolean = false,
    val notifications: Boolean = false,
    val overlay: Boolean = false,
    val mockLocation: Boolean = false,
)

enum class AppFilter(val label: String) {
    ALL("全部"),
    LOCATION_SDK("高德 / 百度"),
    WITH_BACKUP("有备份"),
}

data class AppsUiState(
    val loading: Boolean = true,
    val scanning: Boolean = false,
    val scannedCount: Int = 0,
    val scanTotal: Int = 0,
    val apps: List<TargetAppInfo> = emptyList(),
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val selectedPackage: String? = null,
    val cacheReading: Boolean = false,
    val cacheBytes: Long? = null,
    val cacheError: String? = null,
    val clearing: Boolean = false,
    val restoring: Boolean = false,
    val operationMessage: String? = null,
    val error: String? = null,
) {
    val selectedApp: TargetAppInfo? get() = apps.firstOrNull { it.packageName == selectedPackage }

    fun visibleApps(backups: List<CacheBackupRecord>): List<TargetAppInfo> {
        val backedUp = backups.mapTo(hashSetOf()) { it.packageName }
        return apps.filter { app ->
            val queryMatches = query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            val filterMatches = when (filter) {
                AppFilter.ALL -> true
                AppFilter.LOCATION_SDK -> app.supportsLocationCacheMaintenance
                AppFilter.WITH_BACKUP -> app.packageName in backedUp
            }
            queryMatches && filterMatches
        }
    }
}

data class RouteRecorderUiState(
    val recording: Boolean = false,
    val pointCount: Int = 0,
    val elapsedSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val error: String? = null,
)

data class FridaUiState(
    val targetPackage: String = "",
    val checkingEnvironment: Boolean = false,
    val environment: FridaEnvironment? = null,
    val targetInfo: FridaTargetInfo? = null,
    val injectionMode: FridaInjectionMode = FridaInjectionMode.JDWP,
    val generating: Boolean = false,
    val artifact: FridaScriptArtifact? = null,
    val exportingKit: Boolean = false,
    val kitPath: String? = null,
    val error: String? = null,
    val notice: String? = null,
)

data class DiagnosticsUiState(
    val loading: Boolean = false,
    val report: SystemReport? = null,
    val error: String? = null,
)

data class UpdateUiState(
    val checking: Boolean = false,
    val info: UpdateInfo? = null,
    val error: String? = null,
    val checked: Boolean = false,
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data object RequestRuntimePermissions : UiEvent
    data object RequestLocationPermission : UiEvent
    data object RequestNotificationPermission : UiEvent
    data object OpenOverlaySettings : UiEvent
    data object OpenShizukuApp : UiEvent
    data object OpenDeveloperOptions : UiEvent
    data object OpenLocationSettings : UiEvent
    data object OpenNotificationSettings : UiEvent
    data object OpenBatteryOptimizationSettings : UiEvent
    data class OpenUrl(val url: String) : UiEvent
    data class ShareFile(val path: String) : UiEvent
    data object NavigateToMap : UiEvent
    data class OpenAppDetails(val packageName: String) : UiEvent
}
