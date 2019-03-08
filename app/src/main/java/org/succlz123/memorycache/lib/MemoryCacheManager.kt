package org.succlz123.memorycache.lib

import org.succlz123.memorycache.lib.core.CountingMemoryCache
import org.succlz123.memorycache.lib.core.MemoryCache
import org.succlz123.memorycache.lib.core.Predicate
import org.succlz123.memorycache.lib.reference.CloseableReference
import org.succlz123.memorycache.lib.trimmable.MemoryTrimType

class MemoryCacheManager<K, V>(val delegate: MemoryCache<K, V>, val callback: MemoryCacheCallback<K>) {

    fun get(key: K): CloseableReference<V>? {
        val result = delegate.get(key)
        if (result == null) {
            callback.onCacheMiss()
        } else {
            callback.onCacheHit(key)
        }
        return result
    }

    fun cache(key: K, value: CloseableReference<V>?): CloseableReference<V>? {
        if (value == null) {
            return null
        }
        callback.onCacheInsert()
        return delegate.cache(key, value)
    }

    fun removeAll(predicate: Predicate<K>): Int {
        return delegate.removeAll(predicate)
    }

    fun contains(predicate: Predicate<K>): Boolean {
        return delegate.contains(predicate)
    }

    fun clear() {
        (delegate as? CountingMemoryCache)?.clear()
    }

    companion object {

        val DEFAULT_TRIM_STRATEGY = object : CountingMemoryCache.CacheTrimStrategy {
            override fun getTrimRatio(trimType: MemoryTrimType): Double {
                return trimType.suggestedTrimRatio
            }
        }
    }
}

interface MemoryCacheCallback<K> {

    fun onCacheHit(cacheKey: K)

    fun onCacheMiss()

    fun onCacheInsert()
}

