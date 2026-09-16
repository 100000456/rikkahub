package me.rerere.rikkahub.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 *
 * 定位结果里写明来源和新鲜度，调用方别看到坐标就当新的用。
 */
data class FetchedLocation(
    val location: Location,
    /** true = 这次已经拿到新定位，或者缓存还在新鲜期内；false = 只是过期的系统缓存兜底 */
    val isFresh: Boolean,
    /** 这份数据距今多久（毫秒） */
    val ageMs: Long,
)

internal object DeviceLocationFetcher {

    /** 缓存定位还算新鲜、不用重新定位的时长 */
    private const val MAX_FRESH_AGE_MS = 5 * 60 * 1000L

    /** 单个 provider 等新定位的超时 */
    private const val PER_PROVIDER_TIMEOUT_MS = 8_000L

    @SuppressLint("MissingPermission")
    suspend fun fetch(context: Context): FetchedLocation? = withContext(Dispatchers.IO) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val cached = getCachedLocation(lm)
        val now = System.currentTimeMillis()
        val cachedAge = cached?.let { now - it.time }

        if (cached != null && cachedAge != null && cachedAge <= MAX_FRESH_AGE_MS) {
            return@withContext FetchedLocation(cached, isFresh = true, ageMs = cachedAge)
        }

        // 缓存空或者过期：GPS 和 NETWORK 两个一起问，谁先回来用谁，
        // 室内或者信号不好的时候别死等一个 provider 等到超时。
        val fresh = requestFreshLocationRacing(lm)
        if (fresh != null) {
            return@withContext FetchedLocation(fresh, isFresh = true, ageMs = 0L)
        }

        // 新定位彻底失败：没缓存就返回 null；有缓存但过期，照样返回，
        // 但如实标上 isFresh=false 和真实年龄，要不要用交给调用方决定。
        if (cached != null && cachedAge != null) {
            return@withContext FetchedLocation(cached, isFresh = false, ageMs = cachedAge)
        }
        null
    }

    @SuppressLint("MissingPermission")
    private fun getCachedLocation(lm: LocationManager): Location? {
        return try {
            val providers = lm.getProviders(true)
            providers.mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.accuracy }
        } catch (e: Exception) {
            android.util.Log.e("DeviceLocationFetcher", "getCachedLocation failed", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocationRacing(lm: LocationManager): Location? = coroutineScope {
        val availableProviders = try {
            lm.getProviders(true)
        } catch (e: Exception) {
            android.util.Log.e("DeviceLocationFetcher", "getProviders failed", e)
            emptyList()
        }

        val candidateProviders = listOfNotNull(
            LocationManager.NETWORK_PROVIDER.takeIf { it in availableProviders },
            LocationManager.GPS_PROVIDER.takeIf { it in availableProviders },
        ).ifEmpty { availableProviders }

        if (candidateProviders.isEmpty()) return@coroutineScope null

        val deferredResults = candidateProviders.map { provider ->
            async { requestSingleProviderLocation(lm, provider) }
        }

        var result: Location? = null
        for (deferred in deferredResults) {
            val loc = try {
                deferred.await()
            } catch (e: Exception) {
                android.util.Log.e("DeviceLocationFetcher", "provider request failed", e)
                null
            }
            if (loc != null && result == null) {
                result = loc
            }
        }
        // 没结束的几个请求撤掉，别留着一堆 LocationListener 挂着
        deferredResults.forEach { if (it.isActive) it.cancel() }
        result
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleProviderLocation(lm: LocationManager, provider: String): Location? {
        return try {
            withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }

                        override fun onProviderDisabled(p: String) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(null)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(p: String?, status: Int, extras: Bundle?) {}

                        override fun onProviderEnabled(p: String) {}
                    }

                    cont.invokeOnCancellation {
                        try {
                            lm.removeUpdates(listener)
                        } catch (e: Exception) {
                            android.util.Log.e("DeviceLocationFetcher", "removeUpdates on cancel failed, provider=$provider", e)
                        }
                    }

                    try {
                        lm.requestLocationUpdates(provider, 0L, 0f, listener)
                    } catch (e: Exception) {
                        android.util.Log.e("DeviceLocationFetcher", "requestLocationUpdates failed, provider=$provider", e)
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DeviceLocationFetcher", "requestSingleProviderLocation failed, provider=$provider", e)
            null
        }
    }
}
