/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 */

package me.rerere.rikkahub.data.gadgetbridge

import java.time.LocalDate

data class DailySummary(
    val timestamp: Long,
    val date: LocalDate,
    val steps: Int,
    val hrResting: Int?,
    val hrMax: Int?,
    val hrMin: Int?,
    val hrAvg: Int?,
    val stressAvg: Int?,
    val calories: Int?,
    val spo2Avg: Int?,
)

data class ActivitySample(
    val timestamp: Long,
    val heartRate: Int?,
    val steps: Int?,
    val stress: Int?,
    val spo2: Int?,
    val rawIntensity: Int?,
)

data class SleepSummary(
    val timestamp: Long,
    val wakeupTime: Long,
    val totalDuration: Int,
    val deepSleep: Int,
    val lightSleep: Int,
    val remSleep: Int,
    val awakeDuration: Int,
    val isAwake: Boolean,
) {
    val isNap: Boolean get() = isAwake && deepSleep == 0 && lightSleep == 0 && remSleep == 0
}
