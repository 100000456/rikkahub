/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 *
 * 读取 Gadgetbridge 自动导出的数据库（只读，不申请写权限）。
 * 小米分支按 XIAOMI_* 表直读；华为分支按华为表结构聚合，
 * 其中华为的时间单位/占位行/-1 哨兵值等实测结论保留在下方注释里。
 */

package me.rerere.rikkahub.data.gadgetbridge

import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "GadgetbridgeReader"

object GadgetbridgeDbPath {
    val DB_PATH: String
        get() = File(
            Environment.getExternalStorageDirectory(),
            "Download/手环/Gadgetbridge.db"
        ).absolutePath

    fun getPossiblePaths(customPath: String = ""): List<String> {
        val paths = mutableListOf<String>()

        if (customPath.isNotBlank()) {
            paths.add(customPath)
        }

        val defaultPaths = listOf(
            DB_PATH,
            "/sdcard/Download/手环/Gadgetbridge.db",
            "/storage/emulated/0/Download/手环/Gadgetbridge.db",
            "/sdcard/下载/手环/Gadgetbridge.db",
            "/storage/emulated/0/下载/手环/Gadgetbridge.db",
            File(Environment.getExternalStorageDirectory(), "下载/手环/Gadgetbridge.db").absolutePath,
        )
        paths.addAll(defaultPaths)

        val sqlite3Variants = paths
            .filter { it.endsWith(".db") }
            .map { it.removeSuffix(".db") + ".sqlite3" }
        paths.addAll(sqlite3Variants)

        // 兜底：默认路径都不中时，去 Download 及其一级子目录里认文件名
        paths.addAll(scanDownloadDir())

        return paths.distinct()
    }

    /**
     * 在 Download（含一级子目录）里找名字像 Gadgetbridge 的数据库文件。
     * 用户自己换了导出目录时靠这个兜住，不需要手填路径。
     */
    private fun scanDownloadDir(): List<String> {
        val result = mutableListOf<String>()
        try {
            val root = File(Environment.getExternalStorageDirectory(), "Download")
            val dirs = mutableListOf(root)
            root.listFiles()?.filter { it.isDirectory }?.let { dirs.addAll(it) }
            dirs.forEach { dir ->
                try {
                    dir.listFiles()?.forEach { file ->
                        val name = file.name.lowercase()
                        if (file.isFile && name.contains("gadgetbridge") &&
                            (name.endsWith(".db") || name.endsWith(".sqlite3"))
                        ) {
                            result.add(file.absolutePath)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return result
    }
}

object GadgetbridgeReader {

    // 缓存上次找到的路径，避免重复搜索
    private var cachedDbPath: String? = null

    /** 最近一次真正读到数据的库文件路径，给上层报错时用 */
    val lastDbPath: String? get() = cachedDbPath

    fun dbFileExists(customPath: String = ""): Boolean {
        val paths = GadgetbridgeDbPath.getPossiblePaths(customPath)
        for (path in paths) {
            try {
                val file = File(path)
                Log.d(TAG, "检查数据库文件: $path, exists=${file.exists()}, length=${if (file.exists()) file.length() else 0}")
                if (file.exists() && file.length() > 0) {
                    cachedDbPath = path
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查数据库文件失败: $path", e)
            }
        }
        return false
    }

    private fun findDbPath(customPath: String = ""): String? {
        cachedDbPath?.let { cached ->
            if (File(cached).exists() && File(cached).length() > 0) {
                return cached
            }
            cachedDbPath = null
        }

        val paths = GadgetbridgeDbPath.getPossiblePaths(customPath)
        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "找到数据库文件: $path")
                    cachedDbPath = path
                    return path
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun <T> withDatabase(customPath: String = "", block: (SQLiteDatabase) -> T): Result<T> {
        val dbPath = findDbPath(customPath) ?: return Result.failure(
            IllegalStateException("Gadgetbridge 数据库文件不存在")
        )
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                dbPath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            Result.success(block(db))
        } catch (e: Exception) {
            Log.e(TAG, "打开数据库失败: $dbPath", e)
            Result.failure(e)
        } finally {
            db?.close()
        }
    }

    // ==================== 厂商路由 ====================

    private enum class Manufacturer { XIAOMI, HUAWEI }

    /**
     * 取 DEVICE 表最新一条的 MANUFACTURER 判断厂商。
     * 查不到 / 认不出来时一律走小米逻辑，保证向后兼容。
     */
    private fun detectManufacturer(db: SQLiteDatabase): Manufacturer {
        return try {
            val cursor = db.query(
                "DEVICE",
                arrayOf("MANUFACTURER"),
                null, null, null, null,
                "_id DESC", "1"
            )
            cursor.use {
                if (!it.moveToFirst()) {
                    Log.w(TAG, "厂商判断: DEVICE 表为空, 默认走小米逻辑")
                    Manufacturer.XIAOMI
                } else {
                    val manufacturer = it.getString(0)?.lowercase()?.trim().orEmpty()
                    Log.d(TAG, "厂商判断: MANUFACTURER=$manufacturer")
                    when {
                        manufacturer.contains("huawei") -> Manufacturer.HUAWEI
                        else -> Manufacturer.XIAOMI
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "厂商判断失败, 默认走小米逻辑", e)
            Manufacturer.XIAOMI
        }
    }

    // ==================== 公开方法 ====================

    fun readDailySummaries(days: Int, customPath: String = ""): List<DailySummary> {
        return withDatabase(customPath) { db ->
            when (detectManufacturer(db)) {
                Manufacturer.HUAWEI -> readDailySummariesHuawei(db, days)
                Manufacturer.XIAOMI -> readDailySummariesXiaomi(db, days)
            }
        }.getOrDefault(emptyList())
    }

    fun readLatestActivitySample(customPath: String = ""): ActivitySample? {
        return withDatabase(customPath) { db ->
            when (detectManufacturer(db)) {
                Manufacturer.HUAWEI -> readLatestActivitySampleHuawei(db)
                Manufacturer.XIAOMI -> readLatestActivitySampleXiaomi(db)
            }
        }.getOrDefault(null)
    }

    fun readSleepSummaries(days: Int, customPath: String = ""): List<SleepSummary> {
        return withDatabase(customPath) { db ->
            when (detectManufacturer(db)) {
                Manufacturer.HUAWEI -> readSleepSummariesHuawei(db, days)
                Manufacturer.XIAOMI -> readSleepSummariesXiaomi(db, days)
            }
        }.getOrDefault(emptyList())
    }

    fun readLatestSpo2AndStress(customPath: String = ""): Pair<Int?, Int?> {
        return withDatabase(customPath) { db ->
            when (detectManufacturer(db)) {
                Manufacturer.HUAWEI -> readLatestSpo2AndStressHuawei(db)
                Manufacturer.XIAOMI -> readLatestSpo2AndStressXiaomi(db)
            }
        }.getOrDefault(null to null)
    }

    // ==================== 小米实现 ====================

    private fun readDailySummariesXiaomi(db: SQLiteDatabase, days: Int): List<DailySummary> {
        val now = LocalDate.now()
        val startTime = now.minusDays(days.toLong())
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val summaries = mutableListOf<DailySummary>()
        val cursor = db.query(
            "XIAOMI_DAILY_SUMMARY_SAMPLE",
            arrayOf("TIMESTAMP", "STEPS", "HR_RESTING", "HR_MAX", "HR_MIN", "HR_AVG", "STRESS_AVG", "CALORIES", "SPO2_AVG"),
            "TIMESTAMP >= ?",
            arrayOf(startTime.toString()),
            null, null, "TIMESTAMP ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val timestamp = it.getLong(0)
                val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                summaries.add(DailySummary(timestamp, date, it.getInt(1), getIntOrNull(it, 2), getIntOrNull(it, 3), getIntOrNull(it, 4), getIntOrNull(it, 5), getIntOrNull(it, 6), getIntOrNull(it, 7), getIntOrNull(it, 8)))
            }
        }
        return summaries
    }

    private fun readLatestActivitySampleXiaomi(db: SQLiteDatabase): ActivitySample? {
        val cursor = db.query("XIAOMI_ACTIVITY_SAMPLE", arrayOf("TIMESTAMP", "HEART_RATE", "STEPS", "STRESS", "SPO2", "RAW_INTENSITY"), "HEART_RATE IS NOT NULL AND HEART_RATE > 0", null, null, null, "TIMESTAMP DESC", "1")
        cursor.use {
            return if (it.moveToFirst()) ActivitySample(it.getLong(0), getIntOrNull(it, 1), getIntOrNull(it, 2), getIntOrNull(it, 3), getIntOrNull(it, 4), getIntOrNull(it, 5)) else null
        }
    }

    private fun readSleepSummariesXiaomi(db: SQLiteDatabase, days: Int): List<SleepSummary> {
        val now = System.currentTimeMillis()
        val startTime = now - days.toLong() * 24 * 60 * 60 * 1000L
        val summaries = mutableListOf<SleepSummary>()
        val cursor = db.query(
            "XIAOMI_SLEEP_TIME_SAMPLE",
            arrayOf("TIMESTAMP", "WAKEUP_TIME", "TOTAL_DURATION", "DEEP_SLEEP_DURATION",
                "LIGHT_SLEEP_DURATION", "REM_SLEEP_DURATION", "AWAKE_DURATION", "IS_AWAKE"),
            "TIMESTAMP >= ?",
            arrayOf(startTime.toString()),
            null, null, "TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                summaries.add(SleepSummary(
                    timestamp = it.getLong(0),
                    wakeupTime = it.getLong(1),
                    totalDuration = it.getInt(2),
                    deepSleep = it.getInt(3),
                    lightSleep = it.getInt(4),
                    remSleep = it.getInt(5),
                    awakeDuration = it.getInt(6),
                    isAwake = it.getInt(7) == 1,
                ))
            }
        }
        return summaries
    }

    private fun readLatestSpo2AndStressXiaomi(db: SQLiteDatabase): Pair<Int?, Int?> {
        val startSec = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond
        var spo2: Int? = null
        var stress: Int? = null
        val c1 = db.query("XIAOMI_ACTIVITY_SAMPLE", arrayOf("SPO2"), "TIMESTAMP >= ? AND SPO2 IS NOT NULL AND SPO2 > 0", arrayOf(startSec.toString()), null, null, "TIMESTAMP DESC", "1")
        c1.use { if (it.moveToFirst()) spo2 = getIntOrNull(it, 0) }
        val c2 = db.query("XIAOMI_ACTIVITY_SAMPLE", arrayOf("STRESS"), "TIMESTAMP >= ? AND STRESS IS NOT NULL AND STRESS > 0", arrayOf(startSec.toString()), null, null, "TIMESTAMP DESC", "1")
        c2.use { if (it.moveToFirst()) stress = getIntOrNull(it, 0) }
        return Pair(spo2, stress)
    }

    // ==================== 华为实现 ====================
    //
    // 实测结论（HUAWEI Band 9 / Band 8 真实库 + Gadgetbridge 官方 HuaweiSampleProvider.java）：
    // 1. HUAWEI_ACTIVITY_SAMPLE.TIMESTAMP 单位是【秒】
    // 2. 无数据用 -1（NOT_MEASURED）表示，不是 SQL NULL
    // 3. 每条真实采样同时写一条占位行，与 otherTimestamp 互指；
    //    官方判断真实行的方法：TIMESTAMP <= OTHER_TIMESTAMP
    // 4. CALORIES 原始整数 /1000 才是 kcal（活跃消耗，不含基础代谢）
    // 5. DISTANCE 单位是米；HEART_RATE / RESTING_HEART_RATE / SPO 有效性用 > 0
    // 6. STEPS / CALORIES / DISTANCE 有效性用 != -1（0 是合法值）
    //
    // 时间单位各表不同，必须分别换算范围：
    // - HUAWEI_ACTIVITY_SAMPLE    秒
    // - HUAWEI_STRESS_SAMPLE      毫秒
    // - HUAWEI_SLEEP_STATS_SAMPLE 毫秒

    private fun readDailySummariesHuawei(db: SQLiteDatabase, days: Int): List<DailySummary> {
        val summaries = mutableListOf<DailySummary>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        for (i in 0 until days) {
            try {
                val date = today.minusDays(i.toLong())
                val dayStartSec = date.atStartOfDay(zone).toInstant().epochSecond
                val dayEndSec = date.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
                val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

                val realRowFilter = "TIMESTAMP <= OTHER_TIMESTAMP"

                val steps = try {
                    db.query(
                        "HUAWEI_ACTIVITY_SAMPLE",
                        arrayOf("SUM(STEPS)"),
                        "$realRowFilter AND STEPS != -1 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartSec.toString(), dayEndSec.toString()),
                        null, null, null
                    ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else 0 }
                } catch (e: Exception) {
                    Log.e(TAG, "华为 daily 步数查询失败 date=$date", e); 0
                }

                val calories = try {
                    db.query(
                        "HUAWEI_ACTIVITY_SAMPLE",
                        arrayOf("SUM(CALORIES)"),
                        "$realRowFilter AND CALORIES != -1 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartSec.toString(), dayEndSec.toString()),
                        null, null, null
                    ).use { c ->
                        if (c.moveToFirst() && !c.isNull(0)) (c.getInt(0) / 1000) else null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "华为 daily 卡路里查询失败 date=$date", e); null
                }

                val (hrMax, hrMin, hrAvg) = try {
                    db.query(
                        "HUAWEI_ACTIVITY_SAMPLE",
                        arrayOf("MAX(HEART_RATE)", "MIN(HEART_RATE)", "AVG(HEART_RATE)"),
                        "$realRowFilter AND HEART_RATE > 0 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartSec.toString(), dayEndSec.toString()),
                        null, null, null
                    ).use { c ->
                        if (c.moveToFirst()) Triple(
                            if (c.isNull(0)) null else c.getInt(0),
                            if (c.isNull(1)) null else c.getInt(1),
                            if (c.isNull(2)) null else c.getDouble(2).toInt()
                        ) else Triple(null, null, null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "华为 daily 心率查询失败 date=$date", e)
                    Triple(null, null, null)
                }

                val hrResting = try {
                    db.query(
                        "HUAWEI_ACTIVITY_SAMPLE",
                        arrayOf("RESTING_HEART_RATE"),
                        "$realRowFilter AND RESTING_HEART_RATE > 0 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartSec.toString(), dayEndSec.toString()),
                        null, null, "TIMESTAMP DESC", "1"
                    ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else null }
                } catch (e: Exception) {
                    Log.e(TAG, "华为 daily 静息心率查询失败 date=$date", e); null
                }

                val spo2Avg = try {
                    db.query(
                        "HUAWEI_ACTIVITY_SAMPLE",
                        arrayOf("AVG(SPO)"),
                        "$realRowFilter AND SPO > 0 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartSec.toString(), dayEndSec.toString()),
                        null, null, null
                    ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0).toInt() else null }
                } catch (e: Exception) {
                    Log.e(TAG, "华为 daily 血氧查询失败 date=$date", e); null
                }

                // 压力表无占位行问题，时间戳是毫秒
                val stressAvg = try {
                    db.query(
                        "HUAWEI_STRESS_SAMPLE",
                        arrayOf("AVG(STRESS)"),
                        "TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartMs.toString(), dayEndMs.toString()),
                        null, null, null
                    ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0).toInt() else null }
                } catch (e: Exception) {
                    Log.e(TAG, "华为 daily 压力查询失败 date=$date", e); null
                }

                val timestampMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                summaries.add(
                    DailySummary(
                        timestamp = timestampMs,
                        date = date,
                        steps = steps,
                        hrResting = hrResting,
                        hrMax = hrMax,
                        hrMin = hrMin,
                        hrAvg = hrAvg,
                        stressAvg = stressAvg,
                        calories = calories,
                        spo2Avg = spo2Avg,
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "华为 daily 汇总失败 date=${today.minusDays(i.toLong())}", e)
            }
        }

        return summaries.sortedBy { it.timestamp }
    }

    private fun readLatestActivitySampleHuawei(db: SQLiteDatabase): ActivitySample? {
        return try {
            val cursor = db.query(
                "HUAWEI_ACTIVITY_SAMPLE",
                arrayOf(
                    "TIMESTAMP", "HEART_RATE", "STEPS", "SPO", "RAW_INTENSITY"
                ),
                "TIMESTAMP <= OTHER_TIMESTAMP AND HEART_RATE > 0",
                null, null, null,
                "TIMESTAMP DESC", "1"
            )
            cursor.use {
                if (!it.moveToFirst()) return null
                val timestampSec = it.getLong(0)
                val heartRate = if (it.isNull(1) || it.getInt(1) <= 0) null else it.getInt(1)
                val stepsRaw = if (it.isNull(2)) null else it.getInt(2)
                val steps = if (stepsRaw != null && stepsRaw != -1) stepsRaw else null
                val spoRaw = if (it.isNull(3)) null else it.getInt(3)
                val spo = if (spoRaw != null && spoRaw > 0) spoRaw else null
                val rawIntensity = if (it.isNull(4)) null else it.getInt(4)
                ActivitySample(
                    timestamp = timestampSec * 1000L,
                    heartRate = heartRate,
                    steps = steps,
                    stress = null,
                    spo2 = spo,
                    rawIntensity = rawIntensity,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "华为 latest activity 查询失败", e)
            null
        }
    }

    /**
     * 华为睡眠，实测结论：
     * - 整表时间戳单位毫秒；BED_TIME 实测是坏的（-1000 或 0），不读。
     * - 入睡时刻 = 本行 TIMESTAMP；WAKEUP_TIME 可靠。
     * - 总时长 = (WAKEUP_TIME - TIMESTAMP) / 1000 / 60。
     * - 深/浅/REM/清醒 无现成字段，改查 HUAWEI_SLEEP_STAGE_SAMPLE（每行 1 分钟）：
     *   STAGE 1=浅睡 2=REM 3=深睡 4=清醒 5=小睡（归入浅睡），其它值忽略。
     *   边界：起点向下取整到整分钟，终点向下取整后扣 1 分钟。
     * - DEEP_PART 是 0-100 的占比，不能当分钟用。
     */
    private fun readSleepSummariesHuawei(db: SQLiteDatabase, days: Int): List<SleepSummary> {
        val summaries = mutableListOf<SleepSummary>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        val startMs = today.minusDays(days.toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()

        return try {
            val cursor = db.query(
                "HUAWEI_SLEEP_STATS_SAMPLE",
                arrayOf("TIMESTAMP", "WAKEUP_TIME"),
                "TIMESTAMP >= ?",
                arrayOf(startMs.toString()),
                null, null, "TIMESTAMP DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    try {
                        val sleepStartMs = it.getLong(it.getColumnIndexOrThrow("TIMESTAMP"))
                        val wakeupTimeMs = it.getLong(it.getColumnIndexOrThrow("WAKEUP_TIME"))

                        val totalDuration = try {
                            if (wakeupTimeMs > sleepStartMs) {
                                ((wakeupTimeMs - sleepStartMs) / 1000L / 60L).toInt()
                            } else {
                                0
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "华为 sleep 总时长计算失败 sleepStartMs=$sleepStartMs wakeupTimeMs=$wakeupTimeMs", e)
                            0
                        }

                        var deepMinutes = 0
                        var lightMinutes = 0
                        var remMinutes = 0
                        var awakeMinutes = 0
                        try {
                            val stageFromMs = (sleepStartMs / 60000L) * 60000L
                            val stageToMs = (wakeupTimeMs / 60000L) * 60000L - 60000L
                            db.query(
                                "HUAWEI_SLEEP_STAGE_SAMPLE",
                                arrayOf("STAGE", "COUNT(*)"),
                                "TIMESTAMP >= ? AND TIMESTAMP <= ?",
                                arrayOf(stageFromMs.toString(), stageToMs.toString()),
                                "STAGE", null, null
                            ).use { c ->
                                while (c.moveToNext()) {
                                    val stage = c.getInt(0)
                                    val count = c.getInt(1)
                                    when (stage) {
                                        3 -> deepMinutes += count
                                        1, 5 -> lightMinutes += count
                                        2 -> remMinutes += count
                                        4 -> awakeMinutes += count
                                        else -> Log.w(TAG, "出现未知STAGE值=$stage, 忽略")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "华为 sleep 阶段查询失败 sleepStartMs=$sleepStartMs wakeupTimeMs=$wakeupTimeMs", e)
                        }

                        summaries.add(
                            SleepSummary(
                                timestamp = sleepStartMs,
                                wakeupTime = wakeupTimeMs,
                                totalDuration = totalDuration,
                                deepSleep = deepMinutes,
                                lightSleep = lightMinutes,
                                remSleep = remMinutes,
                                awakeDuration = awakeMinutes,
                                isAwake = false,
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "华为 sleep 单条映射失败", e)
                    }
                }
            }
            summaries
        } catch (e: Exception) {
            Log.e(TAG, "华为 sleep 查询失败 startMs=$startMs", e)
            summaries
        }
    }

    private fun readLatestSpo2AndStressHuawei(db: SQLiteDatabase): Pair<Int?, Int?> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        val activityStartSec = today.atStartOfDay(zone).toInstant().epochSecond
        val stressStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()

        var spo2: Int? = null
        var stress: Int? = null

        try {
            db.query(
                "HUAWEI_ACTIVITY_SAMPLE",
                arrayOf("SPO"),
                "TIMESTAMP <= OTHER_TIMESTAMP AND SPO > 0 AND TIMESTAMP >= ?",
                arrayOf(activityStartSec.toString()),
                null, null, "TIMESTAMP DESC", "1"
            ).use { if (it.moveToFirst() && !it.isNull(0)) spo2 = it.getInt(0) }
        } catch (e: Exception) {
            Log.e(TAG, "华为 latest spo2 查询失败 startSec=$activityStartSec", e)
        }

        try {
            db.query(
                "HUAWEI_STRESS_SAMPLE",
                arrayOf("STRESS"),
                "TIMESTAMP >= ?",
                arrayOf(stressStartMs.toString()),
                null, null, "TIMESTAMP DESC", "1"
            ).use { if (it.moveToFirst() && !it.isNull(0)) stress = it.getInt(0) }
        } catch (e: Exception) {
            Log.e(TAG, "华为 latest stress 查询失败 startMs=$stressStartMs", e)
        }

        return Pair(spo2, stress)
    }

    private fun getIntOrNull(cursor: android.database.Cursor, index: Int): Int? {
        return try { if (cursor.isNull(index)) null else cursor.getInt(index) } catch (_: Exception) { null }
    }
}
