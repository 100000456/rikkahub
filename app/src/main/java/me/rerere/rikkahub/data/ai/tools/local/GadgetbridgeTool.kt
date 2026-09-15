/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 *
 * 读 Gadgetbridge 自动导出的库里已经有的事实数据，不主动连手表。
 */

package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.gadgetbridge.GadgetbridgeReader
import java.time.Instant
import java.time.ZoneId

fun buildGadgetbridgeTool(context: Context): Tool = Tool(
    name = "get_gadgetbridge_data",
    description = "Get health and fitness data from Gadgetbridge (wearable device companion app). " +
        "Returns step count, heart rate, sleep data, blood oxygen, stress, and calories. " +
        "Reads from Gadgetbridge's auto-exported database. " +
        "Requires storage access permission for the exported db file. " +
        "data_type: all | steps | heart_rate | sleep | daily_summary. days: how many days back (default 7).",
    needsApproval = { true },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("data_type") {
                    put("type", "string")
                    put("description", "all (default) | steps | heart_rate | sleep | daily_summary")
                }
                putJsonObject("days") {
                    put("type", "integer")
                    put("description", "How many days back to read (default 7, max 60)")
                }
                putJsonObject("path") {
                    put("type", "string")
                    put("description", "Optional custom path of the exported db file")
                }
            }
        )
    },
    execute = { args ->
        try {
            val params = args.jsonObject
            val dataType = params["data_type"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "all"
            val days = (params["days"]?.jsonPrimitive?.intOrNull ?: 7).coerceIn(1, 60)
            val customPath = params["path"]?.jsonPrimitive?.contentOrNull.orEmpty()

            if (!GadgetbridgeReader.dbFileExists(customPath)) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", false)
                            put("error", "Gadgetbridge 导出的数据库没找到")
                            put(
                                "hint",
                                "默认找 Download/手环/Gadgetbridge.db（也找 .sqlite3 变体和 Download 下的同名文件）。" +
                                    "找不到时把文件路径用 path 参数传进来，另外确认已给兔兔开「所有文件访问」权限。"
                            )
                        }.toString()
                    )
                )
            }

            val needAll = dataType == "all"
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

            val result = buildJsonObject {
                put("success", true)
                put("data_type", dataType)
                put("db_path", GadgetbridgeReader.lastDbPath ?: "")

                if (needAll || dataType == "daily_summary" || dataType == "steps") {
                    val summaries = GadgetbridgeReader.readDailySummaries(days, customPath)
                    putJsonObject("daily_summaries") {
                        put("days_requested", days)
                        put("count", summaries.size)
                        put("list", buildJsonArray {
                            summaries.forEach { item ->
                                add(buildJsonObject {
                                    put("date", item.date.toString())
                                    put("steps", item.steps)
                                    item.hrResting?.let { put("hr_resting", it) }
                                    item.hrMax?.let { put("hr_max", it) }
                                    item.hrMin?.let { put("hr_min", it) }
                                    item.hrAvg?.let { put("hr_avg", it) }
                                    item.stressAvg?.let { put("stress_avg", it) }
                                    item.calories?.let { put("active_calories_kcal", it) }
                                    item.spo2Avg?.let { put("spo2_avg", it) }
                                })
                            }
                        })
                    }
                }

                if (needAll || dataType == "heart_rate" || dataType == "steps") {
                    val sample = GadgetbridgeReader.readLatestActivitySample(customPath)
                    putJsonObject("latest_sample") {
                        if (sample == null) {
                            put("available", false)
                        } else {
                            put("available", true)
                            put("time", dateFormat.format(java.util.Date(sample.timestamp)))
                            sample.heartRate?.let { put("heart_rate", it) }
                            sample.steps?.let { put("steps", it) }
                            sample.stress?.let { put("stress", it) }
                            sample.spo2?.let { put("spo2", it) }
                        }
                    }
                }

                if (needAll || dataType == "sleep") {
                    val sleeps = GadgetbridgeReader.readSleepSummaries(days, customPath)
                    putJsonObject("sleep") {
                        put("count", sleeps.size)
                        put("list", buildJsonArray {
                            sleeps.forEach { item ->
                                add(buildJsonObject {
                                    put("sleep_start", dateFormat.format(java.util.Date(item.timestamp)))
                                    put("wakeup", dateFormat.format(java.util.Date(item.wakeupTime)))
                                    put("total_minutes", item.totalDuration)
                                    put("deep_minutes", item.deepSleep)
                                    put("light_minutes", item.lightSleep)
                                    put("rem_minutes", item.remSleep)
                                    put("awake_minutes", item.awakeDuration)
                                })
                            }
                        })
                    }
                }

                if (needAll || dataType == "heart_rate") {
                    val (spo2, stress) = GadgetbridgeReader.readLatestSpo2AndStress(customPath)
                    putJsonObject("today_latest") {
                        spo2?.let { put("spo2", it) }
                        stress?.let { put("stress", it) }
                    }
                }

                val zone = ZoneId.systemDefault()
                put("read_at", dateFormat.format(java.util.Date(Instant.now().atZone(zone).toInstant().toEpochMilli())))
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
