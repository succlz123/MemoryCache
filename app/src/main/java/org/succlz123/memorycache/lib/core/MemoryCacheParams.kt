package org.succlz123.memorycache.lib.core

/**
 * Pass arguments to control the cache's behavior in the constructor.
 *
 * @param maxCacheSize The maximum size of the cache, in bytes.
 * @param maxCacheEntries The maximum number of items that can live in the cache.
 * @param maxEvictionQueueSize The eviction queue is an area of memory that stores items ready
 * for eviction but have not yet been deleted. This is the maximum
 * size of that queue in bytes.
 * @param maxEvictionQueueEntries The maximum number of entries in the eviction queue.
 * @param maxCacheEntrySize The maximum size of a single cache entry.
 */

class MemoryCacheParams(
        val maxCacheSize: Int,
        val maxCacheEntries: Int,
        val maxEvictionQueueSize: Int,
        val maxEvictionQueueEntries: Int,
        val maxCacheEntrySize: Int)

typealias MemoryCacheParamsProvider = () -> MemoryCacheParams