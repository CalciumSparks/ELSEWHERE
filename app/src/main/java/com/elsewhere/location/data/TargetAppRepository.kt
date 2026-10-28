package com.elsewhere.location.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.zip.ZipFile

enum class LocationSdkTag(val label: String) {
    AMAP("高德 SDK"),
    BAIDU("百度 SDK"),
}

data class SdkScanResult(
    val tags: Set<LocationSdkTag> = emptySet(),
    val reliable: Boolean = true,
)

data class TargetAppInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val isSystem: Boolean,
    val icon: ImageBitmap?,
    val sdkScan: SdkScanResult? = null,
    val cacheBytes: Long? = null,
) {
    val supportsLocationCacheMaintenance: Boolean
        get() = sdkScan?.tags?.isNotEmpty() == true
}

class TargetAppRepository(private val context: Context) {

    private val packageManager = context.packageManager
    private val sourceDirs = mutableMapOf<String, List<String>>()

    suspend fun loadInstalledApps(): List<TargetAppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(intent, 0)
        val byPackage = linkedMapOf<String, TargetAppInfo>()
        resolved.forEach { resolveInfo ->
            val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@forEach
            val packageName = appInfo.packageName
            if (packageName == context.packageName || byPackage.containsKey(packageName)) return@forEach
            if (!appInfo.enabled) return@forEach
            runCatching {
                sourceDirs[packageName] = buildList {
                    appInfo.sourceDir?.let { add(it) }
                    appInfo.splitSourceDirs?.let { addAll(it) }
                }.distinct()
                val label = appInfo.loadLabel(packageManager).toString().ifBlank { packageName }
                val version = runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName
                }.getOrNull().orEmpty()
                val icon = runCatching {
                    appInfo.loadIcon(packageManager)
                        .toBitmap(width = 96, height = 96)
                        .asImageBitmap()
                }.getOrNull()
                byPackage[packageName] = TargetAppInfo(
                    packageName = packageName,
                    label = label,
                    versionName = version,
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    icon = icon,
                )
            }
        }
        val collator = Collator.getInstance(Locale.CHINA)
        byPackage.values.sortedWith(
            compareByDescending<TargetAppInfo> { it.supportsLocationCacheMaintenance }
                .thenComparator { a, b -> collator.compare(a.label, b.label) },
        )
    }

    suspend fun scanLocationSdk(packageName: String): SdkScanResult = withContext(Dispatchers.IO) {
        val files = sourceDirs[packageName] ?: run {
            val appInfo = runCatching {
                packageManager.getApplicationInfo(packageName, 0)
            }.getOrNull() ?: return@withContext SdkScanResult(reliable = false)
            buildList {
                appInfo.sourceDir?.let { add(it) }
                appInfo.splitSourceDirs?.let { addAll(it) }
            }.distinct().also { sourceDirs[packageName] = it }
        }
        val found = mutableSetOf<LocationSdkTag>()
        var reliable = true
        files.forEach { path ->
            if (found.size >= LocationSdkTag.entries.size) return@forEach
            try {
                ZipFile(File(path)).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory || !entry.name.endsWith(".dex", ignoreCase = true)) continue
                        if (entry.size > MAX_DEX_BYTES) {
                            reliable = false
                            continue
                        }
                        val tags = zip.getInputStream(entry).use { stream ->
                            streamContainsAny(
                                stream = stream,
                                needles = SDK_NEEDLES,
                                limitBytes = MAX_DEX_BYTES,
                            )
                        }
                        found += tags
                        if (found.size >= LocationSdkTag.entries.size) break
                    }
                }
            } catch (_: Exception) {
                reliable = false
            }
        }
        SdkScanResult(tags = found.toSet(), reliable = reliable)
    }

    suspend fun loadCacheBytes(packageName: String): Long? = withContext(Dispatchers.IO) {
        try {
            val manager = context.getSystemService(StorageStatsManager::class.java)
            val stats = manager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                packageName,
                Process.myUserHandle(),
            )
            stats.cacheBytes
        } catch (error: Exception) {
            android.util.Log.w("ElsewhereCache", "queryStatsForPackage($packageName) failed", error)
            null
        }
    }

    fun isCacheStatsAvailable(): Boolean = runCatching {
        val manager = context.getSystemService(StorageStatsManager::class.java)
        // A successful query of our own package proves the UsageStats app-op is available.
        manager.queryStatsForPackage(
            StorageManager.UUID_DEFAULT,
            context.packageName,
            Process.myUserHandle(),
        )
        true
    }.getOrDefault(false)

    private fun streamContainsAny(
        stream: java.io.InputStream,
        needles: List<Pair<LocationSdkTag, ByteArray>>,
        limitBytes: Long,
    ): Set<LocationSdkTag> {
        val found = mutableSetOf<LocationSdkTag>()
        val maxNeedle = needles.maxOf { it.second.size }
        val buffer = ByteArray(64 * 1024)
        var carry = ByteArray(0)
        var readTotal = 0L
        while (readTotal < limitBytes) {
            val read = stream.read(buffer)
            if (read <= 0) break
            readTotal += read
            val chunk = ByteArray(buffer.size + read)
            System.arraycopy(buffer, 0, chunk, 0, read)
            val searchable = if (carry.isNotEmpty()) {
                ByteArray(carry.size + read).also {
                    System.arraycopy(carry, 0, it, 0, carry.size)
                    System.arraycopy(buffer, 0, it, carry.size, read)
                }
            } else {
                chunk.copyOf(read)
            }
            needles.forEach { (tag, needle) ->
                if (tag !in found && indexOf(searchable, needle) >= 0) found += tag
            }
            if (found.size >= needles.size) break
            carry = if (searchable.size >= maxNeedle) {
                searchable.copyOfRange(searchable.size - maxNeedle + 1, searchable.size)
            } else {
                searchable
            }
        }
        return found
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        val limit = haystack.size - needle.size
        outer@ for (i in 0..limit) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    companion object {
        private const val MAX_DEX_BYTES = 52L * 1024 * 1024
        private val SDK_NEEDLES = listOf(
            LocationSdkTag.AMAP to "com/amap/api/location/".toByteArray(Charsets.UTF_8),
            LocationSdkTag.AMAP to "Lcom/amap/api/location/".toByteArray(Charsets.UTF_8),
            LocationSdkTag.BAIDU to "com/baidu/location/".toByteArray(Charsets.UTF_8),
            LocationSdkTag.BAIDU to "Lcom/baidu/location/".toByteArray(Charsets.UTF_8),
        )

        fun formatCacheSize(bytes: Long?): String = when {
            bytes == null -> "—"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}
