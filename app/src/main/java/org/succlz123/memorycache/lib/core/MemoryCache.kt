package org.succlz123.memorycache.lib.core

import org.succlz123.memorycache.lib.reference.CloseableReference

/**
 * Interface for the image pipeline memory cache.
 *
 * @param <K> the key type
 * @param <V> the value type
 */

interface MemoryCache<K, V> {

    /**
     * Caches the the given key-value pair.
     *
     *
     *  The cache returns a new copy of the provided reference which should be used instead of the
     * original one. The client should close the returned reference when it is not required anymore.
     *
     *
     *  If the cache failed to cache the given value, then the null reference is returned.
     *
     * @param key
     * @param value
     * @return a new reference to be used, or null if the caching failed
     */
    fun cache(key: K, value: CloseableReference<V>): CloseableReference<V>?

    /**
     * Gets the item with the given key, or null if there is no such item.
     *
     * @param key
     * @return a reference to the cached value, or null if the item was not found
     */
    operator fun get(key: K): CloseableReference<V>?

    /**
     * Removes all the items from the cache whose keys match the specified predicate.
     *
     * @param predicate returns true if an item with the given key should be removed
     * @return number of the items removed from the cache
     */
    fun removeAll(predicate: Predicate<K>): Int

    /**
     * Find if any of the items from the cache whose keys match the specified predicate.
     *
     * @param predicate returns true if an item with the given key matches
     * @return true if the predicate was found in the cache, false otherwise
     */
    operator fun contains(predicate: Predicate<K>): Boolean

    /**
     * Check if the cache contains an item for the given key.
     *
     * @param key
     * @return true if the key was found in the cache, false otherwise
     */
    operator fun contains(key: K): Boolean
}
