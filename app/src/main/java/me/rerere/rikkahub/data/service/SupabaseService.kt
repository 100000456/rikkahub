/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 *
 * 把设备当前的状态打成一条记录，POST 到 Supabase 的 rest/v1/<table>。
 */

package me.rerere.rikkahub.data.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.datastore.AmapStore
import me.rerere.rikkahub.data.gadgetbridge.GadgetbridgeReader
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Serializable
data class SupabaseSyncData(
    val timestamp: String,
    val location: SupabaseLocationData? = null,
    val appUsage: List<SupabaseAppUsageData> = emptyList(),
    val notifications: List<SupabaseNotificationData> = emptyList(),
    val foregroundApp: String = "",
    val deviceEvent: String? = null,
    val health: SupabaseHealthData? = null,
)

@Serializable
data class SupabaseLocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val city: String = "",
    val district: String = "",
    val street: String = "",
)

@Serializable
data class SupabaseAppUsageData(
    val packageName: String,
    val appName: String,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long,
)

@Serializable
data class SupabaseNotificationData(
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val category: String? = null,
)

@Serializable
data class SupabaseHealthData(
    val heartRate: Int? = null,
    val stepsToday: Int? = null,
    val caloriesToday: Int? = null,
    val hrRestingToday: Int? = null,
    val hrMaxToday: Int? = null,
    val hrMinToday: Int? = null,
    val hrAvgToday: Int? = null,
    val spo2: Int? = null,
    val spo2AvgToday: Int? = null,
    val stress: Int? = null,
    val stressAvgToday: Int? = null,
    val sleepStartMs: Long? = null,
    val sleepWakeupMs: Long? = null,
    val sleepTotalMinutes: Int? = null,
    val sleepDeepMinutes: Int? = null,
    val sleepLightMinutes: Int? = null,
    val sleepRemMinutes: Int? = null,
)

private val syncDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

class SupabaseService(
    private val supabaseUrl: String,
    private val supabaseApiKey: String,
    private val tableName: String,
) {
    companion object {
        private const val TAG = "SupabaseService"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun insertRow(data: SupabaseSyncData): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (supabaseUrl.isBlank() || supabaseApiKey.isBlank()) {
                throw IllegalArgumentException("Supabase URL and API key must not be blank")
            }

            val baseUrl = supabaseUrl.trimEnd('/')
            val url = URL("$baseUrl/rest/v1/$tableName")

            val jsonString = json.encodeToString(JsonObject.serializer(), buildJsonObject(data))

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", supabaseApiKey)
                setRequestProperty("Authorization", "Bearer $supabaseApiKey")
                setRequestProperty("Prefer", "return=minimal")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
            }

            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(jsonString)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("Supabase API error ($responseCode): $errorBody")
            }

            Log.d(TAG, "Successfully inserted row into $tableName")
            Unit
        }
    }

    suspend fun insertDeviceEvent(eventType: String): Result<Unit> = insertRow(
        SupabaseSyncData(
            timestamp = syncDateFormat.format(Date()),
            deviceEvent = eventType,
        )
    ).onFailure { e ->
        Log.e(TAG, "insertDeviceEvent failed, eventType=$eventType", e)
    }

    private fun buildJsonObject(data: SupabaseSyncData): JsonObject {
        val map = mutableMapOf<String, JsonPrimitive>()
        map["timestamp"] = JsonPrimitive(data.timestamp)
        map["foreground_app"] = JsonPrimitive(data.foregroundApp)

        data.location?.let { loc ->
            map["location_latitude"] = JsonPrimitive(loc.latitude)
            map["location_longitude"] = JsonPrimitive(loc.longitude)
            map["location_address"] = JsonPrimitive(loc.address)
            map["location_city"] = JsonPrimitive(loc.city)
            map["location_district"] = JsonPrimitive(loc.district)
            map["location_street"] = JsonPrimitive(loc.street)
        }

        if (data.appUsage.isNotEmpty()) {
            map["app_usage"] = JsonPrimitive(
                json.encodeToString(
                    kotlinx.serialization.serializer<List<SupabaseAppUsageData>>(),
                    data.appUsage
                )
            )
        }

        if (data.notifications.isNotEmpty()) {
            map["notifications"] = JsonPrimitive(
                json.encodeToString(
                    kotlinx.serialization.serializer<List<SupabaseNotificationData>>(),
                    data.notifications
                )
            )
        }

        data.deviceEvent?.let { event ->
            map["device_event"] = JsonPrimitive(event)
        }

        data.health?.let { h ->
            map["health_data"] = JsonPrimitive(json.encodeToString(SupabaseHealthData.serializer(), h))
        }

        return JsonObject(map)
    }

    /** 把当前状态收一遍再传上去，哪一块拿不到就跳过哪一块，不因为一块失败整条不发 */
    suspend fun collectAndUpload(context: Context): Result<Unit> = runCatching {
        val timestamp = syncDateFormat.format(Date())

        // 位置：先拿坐标，有高德 key 再把地址翻出来
        var locationData: SupabaseLocationData? = null
        try {
            val fetched = DeviceLocationFetcher.fetch(context)
            if (fetched != null) {
                val loc = fetched.location
                var address = ""
                var city = ""
                var district = ""
                var street = ""
                val amapKey = AmapStore.readKey(context)
                if (amapKey.isNotBlank()) {
                    val result = AmapService(amapKey).getAddressFromGps(loc.latitude, loc.longitude)
                    if (result.success) {
                        address = result.formattedAddress.orEmpty()
                        city = result.city.orEmpty()
                        district = result.district.orEmpty()
                        street = result.street.orEmpty()
                    }
                }
                locationData = SupabaseLocationData(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    address = address,
                    city = city,
                    district = district,
                    street = street,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect location", e)
        }

        // 应用使用
        var appUsageData = emptyList<SupabaseAppUsageData>()
        var foregroundApp = ""
        try {
            if (context.hasUsageStatsPermission()) {
                appUsageData = collectAppUsage(context)
                foregroundApp = collectForegroundApp(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect app usage", e)
        }

        // 通知
        var notificationData = emptyList<SupabaseNotificationData>()
        try {
            notificationData = RikkaNotificationListenerService.getTodayNotifications().take(20).map { notif ->
                SupabaseNotificationData(
                    packageName = notif.packageName,
                    appName = notif.appName,
                    title = notif.title,
                    content = notif.content,
                    timestamp = notif.timestamp,
                    category = notif.category,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect notifications", e)
        }

        // 手表数据
        var healthData: SupabaseHealthData? = null
        try {
            if (GadgetbridgeReader.dbFileExists("")) {
                val summaries = GadgetbridgeReader.readDailySummaries(7, "")
                val today = summaries.lastOrNull()
                val latestActivity = GadgetbridgeReader.readLatestActivitySample("")
                val (latestSpo2, latestStress) = GadgetbridgeReader.readLatestSpo2AndStress("")
                val latestSleep = GadgetbridgeReader.readSleepSummaries(7, "").firstOrNull()

                healthData = SupabaseHealthData(
                    heartRate = latestActivity?.heartRate,
                    stepsToday = today?.steps,
                    caloriesToday = today?.calories,
                    hrRestingToday = today?.hrResting,
                    hrMaxToday = today?.hrMax,
                    hrMinToday = today?.hrMin,
                    hrAvgToday = today?.hrAvg,
                    spo2 = latestSpo2,
                    spo2AvgToday = today?.spo2Avg,
                    stress = latestStress,
                    stressAvgToday = today?.stressAvg,
                    sleepStartMs = latestSleep?.timestamp,
                    sleepWakeupMs = latestSleep?.wakeupTime,
                    sleepTotalMinutes = latestSleep?.totalDuration,
                    sleepDeepMinutes = latestSleep?.deepSleep,
                    sleepLightMinutes = latestSleep?.lightSleep,
                    sleepRemMinutes = latestSleep?.remSleep,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect health data", e)
        }

        insertRow(
            SupabaseSyncData(
                timestamp = timestamp,
                location = locationData,
                appUsage = appUsageData,
                notifications = notificationData,
                foregroundApp = foregroundApp,
                health = healthData,
            )
        ).getOrThrow()
    }

    private fun collectAppUsage(context: Context, limit: Int = 10): List<SupabaseAppUsageData> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            System.currentTimeMillis()
        ).filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .map { stat ->
                SupabaseAppUsageData(
                    packageName = stat.packageName,
                    appName = appLabel(context, stat.packageName),
                    totalTimeInForeground = stat.totalTimeInForeground,
                    lastTimeUsed = stat.lastTimeUsed,
                )
            }
    }

    private fun collectForegroundApp(context: Context): String {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ""
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 60_000L, end)
        val event = UsageEvents.Event()
        var last = ""
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                last = event.packageName
            }
        }
        return last
    }

    private fun appLabel(context: Context, packageName: String): String = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        packageName
    }
}
