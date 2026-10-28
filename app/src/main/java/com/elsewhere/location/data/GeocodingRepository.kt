package com.elsewhere.location.data

import com.elsewhere.location.core.ChineseGeo
import com.elsewhere.location.core.Geo
import com.elsewhere.location.core.GeoPoint
import com.elsewhere.location.core.GeocodeResult
import com.elsewhere.location.core.ReverseGeocodeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class GeocodingRepository(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(query: String, amapKey: String = "", limit: Int = 6): Result<List<GeocodeResult>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext Result.success(emptyList())

            parseCoordinate(trimmed)?.let { point ->
                return@withContext Result.success(
                    listOf(
                        GeocodeResult(
                            title = "坐标 ${Geo.formatDecimal(point.latitude, point.longitude, 5)}",
                            subtitle = "按纬度, 经度直接定位",
                            point = point,
                            source = "坐标",
                        ),
                    ),
                )
            }

            val failures = mutableListOf<String>()
            if (amapKey.isNotBlank()) {
                runCatching { searchWithAmap(trimmed, amapKey, limit) }
                    .onSuccess { if (it.isNotEmpty()) return@withContext Result.success(it) }
                    .onFailure { failures += "高德：${it.message ?: "请求失败"}" }
            }
            runCatching { searchWithPhoton(trimmed, limit) }
                .onSuccess { if (it.isNotEmpty()) return@withContext Result.success(it) }
                .onFailure { failures += "Photon：${it.message ?: "请求失败"}" }

            Result.failure(
                IllegalStateException(
                    if (failures.isEmpty()) "没有找到匹配的位置，可直接输入“纬度, 经度”。"
                    else "地址服务暂不可用。可直接输入“纬度, 经度”。\n${failures.joinToString("\n")}",
                ),
            )
        }

    suspend fun reverse(point: GeoPoint, amapKey: String = ""): ReverseGeocodeResult =
        withContext(Dispatchers.IO) {
            if (amapKey.isNotBlank()) {
                runCatching { reverseWithAmap(point, amapKey) }
                    .getOrNull()
                    ?.let { return@withContext it }
            }
            runCatching { reverseWithPhoton(point) }
                .getOrNull()
                ?: ReverseGeocodeResult(
                    title = Geo.formatDecimal(point.latitude, point.longitude, 5),
                    subtitle = "未获取到地址，可手动调整坐标",
                )
        }

    private fun searchWithPhoton(query: String, limit: Int): List<GeocodeResult> {
        val url = "https://photon.komoot.io/api/".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", limit.coerceIn(1, 10).toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOExceptionWithMessage("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        val parsed = json.decodeFromString(PhotonResponse.serializer(), body)
        return parsed.features.mapNotNull { feature ->
            val coordinates = feature.geometry.coordinates
            if (coordinates.size < 2) return@mapNotNull null
            val point = GeoPoint(latitude = coordinates[1], longitude = coordinates[0])
            if (!point.isValid) return@mapNotNull null
            val props = feature.properties
            val title = props.name?.takeIf { it.isNotBlank() }
                ?: props.street?.takeIf { it.isNotBlank() }
                ?: props.city?.takeIf { it.isNotBlank() }
                ?: "未命名地点"
            val subtitleParts = buildList {
                if (!props.street.isNullOrBlank() && props.street != title) add(props.street)
                if (!props.district.isNullOrBlank() && props.district != title) add(props.district)
                if (!props.city.isNullOrBlank() && props.city != title) add(props.city)
                if (!props.state.isNullOrBlank() && props.state != props.city) add(props.state)
                if (!props.country.isNullOrBlank() && props.countrycode.equals("CN", true).not()) add(props.country)
            }.distinct()
            GeocodeResult(
                title = title,
                subtitle = subtitleParts.joinToString(" · "),
                point = point,
                source = "OpenStreetMap",
            )
        }
    }

    private fun reverseWithPhoton(point: GeoPoint): ReverseGeocodeResult {
        val url = "https://photon.komoot.io/reverse".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("lat", point.latitude.toString())
            .addQueryParameter("lon", point.longitude.toString())
            .addQueryParameter("limit", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOExceptionWithMessage("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        val parsed = json.decodeFromString(PhotonResponse.serializer(), body)
        val props = parsed.features.firstOrNull()?.properties ?: return fallbackReverse(point)
        val title = props.name?.takeIf { it.isNotBlank() }
            ?: props.street?.takeIf { it.isNotBlank() }
            ?: props.city?.takeIf { it.isNotBlank() }
            ?: Geo.formatDecimal(point.latitude, point.longitude, 5)
        val subtitle = listOfNotNull(
            props.street?.takeIf { it.isNotBlank() && it != title },
            props.locality?.takeIf { it.isNotBlank() },
            props.district?.takeIf { it.isNotBlank() },
            props.city?.takeIf { it.isNotBlank() && it != title },
            props.state?.takeIf { it.isNotBlank() && it != props.city },
        ).distinct().joinToString(" · ")
        return ReverseGeocodeResult(
            title = title,
            subtitle = subtitle.ifBlank { pointDisplay(point) },
        )
    }

    private fun searchWithAmap(query: String, key: String, limit: Int): List<GeocodeResult> {
        val url = "https://restapi.amap.com/v3/place/text".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("keywords", query)
            .addQueryParameter("key", key)
            .addQueryParameter("offset", limit.coerceIn(1, 10).toString())
            .addQueryParameter("page", "1")
            .addQueryParameter("extensions", "base")
            .build()
        val body = requestText(url.toString())
        val root = json.parseToJsonElement(body)
        val status = root.jsonObject["status"]?.toString()?.trim('"')
        if (status != "1") {
            val info = root.jsonObject["info"]?.toString()?.trim('"') ?: "高德接口返回异常"
            throw IOExceptionWithMessage(info)
        }
        val pois = root.jsonObject["pois"]?.jsonArray ?: return emptyList()
        return pois.mapNotNull { element ->
            val obj = element.jsonObject
            val location = obj["location"]?.toString()?.trim('"') ?: return@mapNotNull null
            val pieces = location.split(",")
            if (pieces.size < 2) return@mapNotNull null
            val longitude = pieces[0].toDoubleOrNull() ?: return@mapNotNull null
            val latitude = pieces[1].toDoubleOrNull() ?: return@mapNotNull null
            val rawPoint = GeoPoint(latitude, longitude)
            if (!rawPoint.isValid) return@mapNotNull null
            val point = ChineseGeo.gcj02ToWgs84(rawPoint)
            val title = obj["name"]?.toString()?.trim('"').orEmpty()
            val subtitleParts = listOfNotNull(
                obj["cityname"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "[]" },
                obj["adname"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "[]" },
                obj["address"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "[]" },
            ).distinct()
            GeocodeResult(
                title = title.ifBlank { "未命名地点" },
                subtitle = subtitleParts.joinToString(" · "),
                point = point,
                source = "高德 Web 服务",
            )
        }
    }

    private fun reverseWithAmap(point: GeoPoint, key: String): ReverseGeocodeResult {
        val url = "https://restapi.amap.com/v3/geocode/regeo".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("location", "${ChineseGeo.wgs84ToGcj02(point).longitude},${ChineseGeo.wgs84ToGcj02(point).latitude}")
            .addQueryParameter("key", key)
            .addQueryParameter("extensions", "base")
            .build()
        val body = requestText(url.toString())
        val root = json.parseToJsonElement(body)
        val status = root.jsonObject["status"]?.toString()?.trim('"')
        if (status != "1") throw IOExceptionWithMessage("高德逆地理编码失败")
        val regeocode = root.jsonObject["regeocode"]?.jsonObject ?: return fallbackReverse(point)
        val formatted = regeocode["formatted_address"]?.toString()?.trim('"').orEmpty()
        if (formatted.isBlank()) return fallbackReverse(point)
        val components = regeocode["addressComponent"]?.jsonObject
        val subtitle = components?.get("township")?.toString()?.trim('"')
            ?.takeIf { it.isNotBlank() && it != "[]" }
            ?: components?.get("city")?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "[]" }
            ?: pointDisplay(point)
        return ReverseGeocodeResult(title = formatted, subtitle = subtitle)
    }

    private fun requestText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOExceptionWithMessage("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    private fun fallbackReverse(point: GeoPoint) = ReverseGeocodeResult(
        title = Geo.formatDecimal(point.latitude, point.longitude, 5),
        subtitle = "经纬度坐标",
    )

    private fun pointDisplay(point: GeoPoint) = Geo.formatDecimal(point.latitude, point.longitude, 5)

    private fun parseCoordinate(query: String): GeoPoint? {
        val normalized = query.replace('，', ',').trim()
        val parts = normalized.split(',').map { it.trim() }
        if (parts.size != 2) return null
        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        val point = GeoPoint(latitude, longitude)
        return point.takeIf { it.isValid }
    }

    @Serializable
    private data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

    @Serializable
    private data class PhotonFeature(
        val properties: PhotonProperties = PhotonProperties(),
        val geometry: PhotonGeometry = PhotonGeometry(),
    )

    @Serializable
    private data class PhotonProperties(
        val name: String? = null,
        val street: String? = null,
        val locality: String? = null,
        val district: String? = null,
        val city: String? = null,
        val state: String? = null,
        val country: String? = null,
        val countrycode: String? = null,
    )

    @Serializable
    private data class PhotonGeometry(val coordinates: List<Double> = emptyList())

    private class IOExceptionWithMessage(message: String) : Exception(message)

    companion object {
        private const val USER_AGENT = "Elsewhere-Android/1.0 (virtual-location testing utility; contact: local build)"
    }
}
