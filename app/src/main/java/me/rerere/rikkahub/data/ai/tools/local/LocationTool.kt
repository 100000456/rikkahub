/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 *
 * 拿当前位置。坐标一定有，填了高德 key 的话顺带把地址翻出来。
 */

package me.rerere.rikkahub.data.ai.tools.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
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

fun buildLocationTool(context: Context): Tool = Tool(
    name = "get_location",
    description = "Get the current location of the device. Returns latitude, longitude, accuracy, " +
        "whether the fix is fresh, and (if an Amap API key is configured) the readable address. " +
        "Needs location permission. include_address: whether to reverse geocode the address (default true).",
    needsApproval = { true },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("include_address") {
                    put("type", "boolean")
                    put("description", "Whether to turn the coordinates into a readable address (default true)")
                }
            }
        )
    },
    execute = { args ->
        try {
            if (!hasLocationPermission(context)) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", false)
                            put("error", "没给定位权限")
                            put("hint", "在系统设置里把位置权限给兔兔，选「仅使用期间允许」就行")
                        }.toString()
                    )
                )
            }

            val params = args.jsonObject
            val wantAddress = params["include_address"]?.jsonPrimitive?.booleanOrNull ?: true

            val fetched = runBlocking { DeviceLocationFetcher.fetch(context) }
            if (fetched == null) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", false)
                            put("error", "拿不到位置")
                            put("hint", "确认手机定位开关是开的，且给了位置权限")
                        }.toString()
                    )
                )
            }

            val loc = fetched.location
            val result = buildJsonObject {
                put("success", true)
                put("latitude", loc.latitude)
                put("longitude", loc.longitude)
                if (!loc.accuracy.isNaN()) put("accuracy_m", loc.accuracy)
                put("is_fresh", fetched.isFresh)
                put("age_seconds", fetched.ageMs / 1000)

                if (wantAddress) {
                    val apiKey = AmapStore.readKey(context)
                    if (apiKey.isBlank()) {
                        put("address", "")
                        put("address_error", "没填高德 key，只有坐标")
                    } else {
                        val address = runBlocking {
                            AmapService(apiKey).getAddressFromGps(loc.latitude, loc.longitude)
                        }
                        if (address.success) {
                            put("address", address.formattedAddress ?: "")
                            address.district?.let { put("district", it) }
                            address.city?.let { put("city", it) }
                        } else {
                            put("address", "")
                            put("address_error", address.error ?: "逆地理编码失败")
                        }
                    }
                }
            }

            listOf(UIMessagePart.Text(result.toString()))
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

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
