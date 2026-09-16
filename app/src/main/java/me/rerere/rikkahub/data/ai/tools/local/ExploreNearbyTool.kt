/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 *
 * 看看身边有什么：吃的、商店、医院。绕当前位置搜索，要高德 key。
 */

package me.rerere.rikkahub.data.ai.tools.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.AmapStore
import me.rerere.rikkahub.data.service.AmapService
import me.rerere.rikkahub.data.service.DeviceLocationFetcher

fun buildExploreNearbyTool(context: Context): Tool = Tool(
    name = "explore_nearby",
    description = "Explore nearby points of interest (POI) around the current location. " +
        "Needs location permission and an Amap API key. Returns restaurants, shops, hospitals, attractions, etc. " +
        "keyword: e.g. restaurant / coffee / hospital / bank. radius: meters (default 1000, max 50000). " +
        "type: Amap POI type code (e.g. 050000 restaurants, 060000 shopping, 080000 medical). limit: max results (default 10).",
    needsApproval = { true },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("keyword") {
                    put("type", "string")
                    put("description", "Search keyword (e.g. restaurant, coffee, hospital, bank)")
                }
                putJsonObject("radius") {
                    put("type", "integer")
                    put("description", "Search radius in meters (default 1000, max 50000)")
                }
                putJsonObject("type") {
                    put("type", "string")
                    put("description", "POI type code (e.g. 050000 for restaurants, 060000 for shopping, 080000 for medical)")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Maximum number of results (default 10)")
                }
            }
        )
    },
    execute = { args ->
        val params = args.jsonObject

        if (!hasLocationPermissionForNearby(context)) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", "没给定位权限")
                    }.toString()
                )
            )
        }

        val apiKey = AmapStore.readKey(context)
        if (apiKey.isBlank()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", "没填高德 key，搜不了附近")
                        put("hint", "去设置里的系统工具页把高德 key 填上")
                    }.toString()
                )
            )
        }

        try {
            val keyword = params["keyword"]?.jsonPrimitive?.content ?: ""
            val radius = params["radius"]?.jsonPrimitive?.intOrNull ?: 1000
            val type = params["type"]?.jsonPrimitive?.content ?: ""
            val limit = params["limit"]?.jsonPrimitive?.intOrNull ?: 10

            val loc = runBlocking { DeviceLocationFetcher.fetch(context) }?.location
            if (loc == null) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", false)
                            put("error", "拿不到当前位置")
                        }.toString()
                    )
                )
            }

            val amapService = AmapService(apiKey)
            val pois = runBlocking {
                amapService.searchNearbyPoi(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    keyword = keyword,
                    radius = radius.coerceIn(100, 50000),
                    type = type,
                    limit = limit
                )
            }

            if (pois.isEmpty()) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", true)
                            put("count", 0)
                            put("message", "附近没搜到：$keyword")
                        }.toString()
                    )
                )
            }

            val addressResult = runBlocking {
                amapService.getAddressFromGps(loc.latitude, loc.longitude)
            }

            val arr = buildJsonArray {
                pois.forEach { poi ->
                    add(
                        buildJsonObject {
                            put("name", poi.name)
                            put("address", poi.address)
                            put("distance_m", poi.distance)
                            put("type", poi.type)
                            if (poi.tel.isNotBlank()) put("tel", poi.tel)
                        }
                    )
                }
            }

            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("count", pois.size)
                        put("current_location", addressResult.formattedAddress ?: "${loc.latitude},${loc.longitude}")
                        put("search_radius", radius)
                        put("places", arr)
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", e.message ?: "Unknown error")
                    }.toString()
                )
            )
        }
    }
)

private fun hasLocationPermissionForNearby(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
