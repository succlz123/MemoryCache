package org.succlz123.memorycache.sample

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import org.succlz123.memorycache.lib.core.MemoryCacheParams
import org.succlz123.memorycache.lib.core.MemoryCacheParamsProvider

class BitmapMemoryCacheParams(applicationContext: Context) : MemoryCacheParamsProvider {
    private val mActivityManager: ActivityManager

    private val maxCacheSize: Int
        get() {
            val maxMemory = Math.min(mActivityManager.memoryClass * MB, Integer.MAX_VALUE)
            return if (maxMemory < 32 * MB) {
                4 * MB
            } else if (maxMemory < 64 * MB) {
                6 * MB
            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                    8 * MB
                } else {
                    maxMemory / 4
                }
            }
        }

    init {
        mActivityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    override fun invoke(): MemoryCacheParams {
        return MemoryCacheParams(
                maxCacheSize,
                MAX_CACHE_ENTRIES,
                MAX_EVICTION_QUEUE_SIZE,
                MAX_EVICTION_QUEUE_ENTRIES,
                MAX_CACHE_ENTRY_SIZE)
    }

    companion object {
        private const val MAX_CACHE_ENTRIES = 256
        private const val MAX_EVICTION_QUEUE_SIZE = Integer.MAX_VALUE
        private const val MAX_EVICTION_QUEUE_ENTRIES = Integer.MAX_VALUE
        private const val MAX_CACHE_ENTRY_SIZE = Integer.MAX_VALUE

        private const val KB = 1024
        private const val MB = 1024 * KB
    }
}
