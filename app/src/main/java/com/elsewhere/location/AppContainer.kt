package com.elsewhere.location

import android.content.Context
import com.elsewhere.location.data.CacheBackupRepository
import com.elsewhere.location.data.DiagnosticsRepository
import com.elsewhere.location.data.GeocodingRepository
import com.elsewhere.location.data.SettingsRepository
import com.elsewhere.location.data.TargetAppRepository
import com.elsewhere.location.data.UpdateRepository
import com.elsewhere.location.location.RealLocationProbe
import com.elsewhere.location.shizuku.ShizukuManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)
    val shizukuManager: ShizukuManager = ShizukuManager(appContext)
    val geocodingRepository: GeocodingRepository = GeocodingRepository(httpClient)
    val realLocationProbe: RealLocationProbe = RealLocationProbe(appContext)
    val updateRepository: UpdateRepository = UpdateRepository(httpClient)
    val targetAppRepository: TargetAppRepository = TargetAppRepository(appContext)
    val cacheBackupRepository: CacheBackupRepository = CacheBackupRepository(
        context = appContext,
        settingsRepository = settingsRepository,
        shizukuManager = shizukuManager,
    )
    val diagnosticsRepository: DiagnosticsRepository = DiagnosticsRepository(
        context = appContext,
        shizukuManager = shizukuManager,
    )
}
