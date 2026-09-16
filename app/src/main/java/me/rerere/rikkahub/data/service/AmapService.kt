package me.rerere.rikkahub.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 *
 * 高德地图 API：坐标转换、逆地理编码、周边 POI 搜索。
 */
class AmapService(
    private val apiKey: String,
    private val okHttpClient: OkHttpClient = defaultClient
) {
    companion object {
        private const val BASE_URL = "https://restapi.amap.com/v3"

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        enum class CoordType(val code: String) {
            GPS("gps"),
            AMAP("autonavi"),
            BAIDU("baidu")
        }
    }

    @Serializable
    data class RegeoResponse(
        val status: String,
        val info: String,
        val infocode: String,
        val regeocode: RegeoCode? = null
    )

    @Serializable
    data class RegeoCode(
        val formatted_address: String? = null,
        val addressComponent: AddressComponent? = null
    )

    @Serializable
    data class AddressComponent(
        val country: String? = null,
        val province: String? = null,
        val city: String? = null,
        val citycode: String? = null,
        val district: String? = null,
        val adcode: String? = null,
        val street: String? = null,
        val streetNumber: String? = null,
        val neighborhood: Neighborhood? = null,
        val building: Building? = null,
        val township: String? = null
    )

    @Serializable
    data class Neighborhood(
        val name: String? = null,
        val type: String? = null
    )

    @Serializable
    data class Building(
        val name: String? = null,
        val type: String? = null
    )

    @Serializable
    data class ConvertResponse(
        val status: String,
        val info: String,
        val infocode: String,
        val locations: String? = null
    )

    @Serializable
    data class AddressResult(
        val success: Boolean,
        val formattedAddress: String? = null,
        val province: String? = null,
        val city: String? = null,
        val district: String? = null,
        val street: String? = null,
        val streetNumber: String? = null,
        val neighborhood: String? = null,
        val building: String? = null,
        val adcode: String? = null,
        val citycode: String? = null,
        val error: String? = null
    )

    /** GPS 坐标换成高德坐标，返回 (纬度, 经度) */
    suspend fun convertToAmapCoord(latitude: Double, longitude: Double): Pair<Double, Double>? {
        val url = "$BASE_URL/assistant/coordinate/convert?" +
            "key=$apiKey&" +
            "locations=$longitude,$latitude&" +
            "coordsys=${CoordType.GPS.code}&" +
            "output=JSON"

        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val result = Json { ignoreUnknownKeys = true }.decodeFromString<ConvertResponse>(body)

                if (result.status == "1" && result.locations != null) {
                    val parts = result.locations.split(",")
                    if (parts.size == 2) {
                        Pair(parts[1].toDouble(), parts[0].toDouble())
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /** 逆地理编码：坐标换地址（入参要是高德坐标） */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): AddressResult {
        val url = "$BASE_URL/geocode/regeo?" +
            "key=$apiKey&" +
            "location=$longitude,$latitude&" +
            "extensions=all&" +
            "output=JSON"

        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: return AddressResult(
                    success = false,
                    error = "Empty response body"
                )

                val result = Json { ignoreUnknownKeys = true }.decodeFromString<RegeoResponse>(body)

                if (result.status == "1" && result.regeocode != null) {
                    val regeo = result.regeocode
                    val addr = regeo.addressComponent

                    AddressResult(
                        success = true,
                        formattedAddress = regeo.formatted_address,
                        province = addr?.province,
                        city = addr?.city ?: addr?.province,
                        district = addr?.district,
                        street = addr?.street,
                        streetNumber = addr?.streetNumber,
                        neighborhood = addr?.neighborhood?.name,
                        building = addr?.building?.name,
                        adcode = addr?.adcode,
                        citycode = addr?.citycode
                    )
                } else {
                    AddressResult(
                        success = false,
                        error = "API returned error: ${result.info} (${result.infocode})"
                    )
                }
            } else {
                AddressResult(
                    success = false,
                    error = "HTTP error: ${response.code}"
                )
            }
        } catch (e: Exception) {
            AddressResult(
                success = false,
                error = "Exception: ${e.message}"
            )
        }
    }

    /** 拿 GPS 原始坐标直接换地址，内部先把坐标转成高德系 */
    suspend fun getAddressFromGps(latitude: Double, longitude: Double): AddressResult {
        val amapCoord = convertToAmapCoord(latitude, longitude)

        if (amapCoord == null) {
            return AddressResult(
                success = false,
                error = "Failed to convert GPS coordinates to Amap coordinates"
            )
        }

        return reverseGeocode(amapCoord.first, amapCoord.second)
    }

    data class PoiResult(
        val name: String,
        val address: String,
        val distance: Int,
        val latitude: Double,
        val longitude: Double,
        val type: String = "",
        val tel: String = ""
    )

    /** 周边 POI 搜索 */
    suspend fun searchNearbyPoi(
        latitude: Double,
        longitude: Double,
        keyword: String = "",
        radius: Int = 1000,
        type: String = "",
        limit: Int = 10
    ): List<PoiResult> = withContext(Dispatchers.IO) {
        try {
            val amapCoord = convertToAmapCoord(latitude, longitude)
            val searchLat = amapCoord?.first ?: latitude
            val searchLon = amapCoord?.second ?: longitude

            val urlBuilder = StringBuilder().apply {
                append("$BASE_URL/place/around?")
                append("key=$apiKey")
                append("&location=$searchLon,$searchLat")
                append("&radius=$radius")
                append("&offset=$limit")
                append("&page=1")
                append("&extensions=base")
                if (keyword.isNotBlank()) append("&keywords=${URLEncoder.encode(keyword, "UTF-8")}")
                if (type.isNotBlank()) append("&types=${URLEncoder.encode(type, "UTF-8")}")
            }

            val request = Request.Builder().url(urlBuilder.toString()).build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val json = JSONObject(body)
            if (json.optString("status") != "1") {
                return@withContext emptyList()
            }

            val pois = json.getJSONArray("pois")
            (0 until minOf(pois.length(), limit)).mapNotNull { i ->
                try {
                    val poi = pois.getJSONObject(i)
                    val location = poi.optString("location", "").split(",")
                    PoiResult(
                        name = poi.optString("name", ""),
                        address = poi.optString("address", ""),
                        distance = poi.optString("distance", "0").toIntOrNull() ?: 0,
                        latitude = location.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                        longitude = location.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
                        type = poi.optString("type", ""),
                        tel = poi.optString("tel", "")
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
