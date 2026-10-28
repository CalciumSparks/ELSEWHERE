package com.elsewhere.location.data

import com.elsewhere.location.core.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateRepository(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun checkForUpdate(repo: String, currentVersion: String): Result<UpdateInfo?> =
        withContext(Dispatchers.IO) {
            val clean = repo.trim().trim('/')
            if (!clean.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
                return@withContext Result.failure(
                    IllegalArgumentException("更新源需为 GitHub 的 owner/repo 格式。"),
                )
            }
            val request = Request.Builder()
                .url("https://api.github.com/repos/$clean/releases/latest")
                .header("User-Agent", "Elsewhere-Android")
                .header("Accept", "application/vnd.github+json")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        return@withContext Result.success(null)
                    }
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("GitHub 返回 HTTP ${response.code}，请稍后重试。"),
                        )
                    }
                    val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                    val tag = root["tag_name"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val name = root["name"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val body = root["body"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val releaseUrl = root["html_url"]?.jsonPrimitive?.content.orEmpty()
                    val publishedAt = root["published_at"]?.jsonPrimitive?.content.orEmpty()
                    val assets = root["assets"]?.jsonArray
                    val apkUrl = assets?.firstOrNull { element ->
                        val asset = element.jsonObject
                        val assetName = asset["name"]?.jsonPrimitive?.content.orEmpty()
                        assetName.endsWith(".apk", ignoreCase = true)
                    }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
                    val latestVersion = tag.removePrefix("v").removePrefix("V")
                    val isNewer = compareVersions(latestVersion, currentVersion) > 0
                    Result.success(
                        UpdateInfo(
                            versionName = latestVersion.ifBlank { tag },
                            releaseName = name.ifBlank { tag },
                            releaseNotes = body.take(3_000),
                            releaseUrl = releaseUrl,
                            downloadUrl = apkUrl,
                            publishedAt = publishedAt,
                            isNewer = isNewer,
                        ),
                    )
                }
            } catch (error: Exception) {
                Result.failure(
                    IllegalStateException(
                        "无法连接 GitHub 更新服务。请检查网络，或直接在 GitHub Releases 中查看。",
                        error,
                    ),
                )
            }
        }

    companion object {
        fun compareVersions(left: String, right: String): Int {
            val leftParts = numericParts(left)
            val rightParts = numericParts(right)
            val size = maxOf(leftParts.size, rightParts.size)
            for (index in 0 until size) {
                val a = leftParts.getOrElse(index) { 0 }
                val b = rightParts.getOrElse(index) { 0 }
                if (a != b) return a.compareTo(b)
            }
            return 0
        }

        private fun numericParts(version: String): List<Long> =
            version.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .split('.')
                .map { it.filter(Char::isDigit).toLongOrNull() ?: 0L }
    }
}
