package org.succlz123.memorycache.lib.core

import android.os.SystemClock
import org.succlz123.memorycache.lib.reference.CloseableReference
import org.succlz123.memorycache.lib.trimmable.MemoryTrimType
import org.succlz123.memorycache.lib.trimmable.MemoryTrimmable
import org.succlz123.memorycache.sample.BitmapMemoryCacheParams
import java.util.*
import java.util.concurrent.TimeUnit

class CountingMemoryCache<K, V>(
        private val valueDescriptor: ValueDescriptor<V>,
        private val cacheTrimStrategy: CacheTrimStrategy,
        private val memoryCacheParamsProvider: BitmapMemoryCacheParams) :
        MemoryCache<K, V>, MemoryTrimmable {

    // Contains the items that are not being used by any client and are hence viable for eviction
    internal val exclusiveEntries: CountingLruMap<K, Entry<K, V>>

    // Contains all the cached items including the exclusively owned ones.
    internal val cachedEntries: CountingLruMap<K, Entry<K, V>>

    protected var memoryCacheParams: MemoryCacheParams

    private var lastCacheParamsCheck: Long = 0L

    /** Gets the total number of all currently cached items. */
    val count: Int
        @Synchronized get() = cachedEntries.count

    /** Gets the total size in bytes of all currently cached items. */
    val sizeInBytes: Int
        @Synchronized get() = cachedEntries.sizeInBytes

    /** Gets the number of the cached items that are used by at least one client. */
    val inUseCount: Int
        @Synchronized get() = cachedEntries.count - exclusiveEntries.count

    /** Gets the total size in bytes of the cached items that are used by at least one client. */
    val inUseSizeInBytes: Int
        @Synchronized get() = cachedEntries.sizeInBytes - exclusiveEntries.sizeInBytes

    /** Gets the number of the exclusively owned items. */
    val evictionQueueCount: Int
        @Synchronized get() = exclusiveEntries.count

    /** Gets the total size in bytes of the exclusively owned items. */
    val evictionQueueSizeInBytes: Int
        @Synchronized get() = exclusiveEntries.sizeInBytes

    init {
        exclusiveEntries = CountingLruMap(wrapValueDescriptor(valueDescriptor))
        cachedEntries = CountingLruMap(wrapValueDescriptor(valueDescriptor))
        memoryCacheParams = memoryCacheParamsProvider.invoke()
        lastCacheParamsCheck = SystemClock.uptimeMillis()
    }

    private fun wrapValueDescriptor(evictableValueDescriptor: ValueDescriptor<V>): ValueDescriptor<Entry<K, V>> {
        return object : ValueDescriptor<Entry<K, V>> {
            override fun getSizeInBytes(value: Entry<K, V>): Int {
                val vRef = value.valueRef.get()
                if (vRef != null) {
                    return evictableValueDescriptor.getSizeInBytes(vRef)
                }
                return 0
            }
        }
    }

    override fun cache(key: K, valueRef: CloseableReference<V>): CloseableReference<V>? {
        return cache(key, valueRef, null)
    }

    /**
     * Caches the given key-value pair.
     **
     *  Important: the client should use the returned reference instead of the original one.
     * It is the caller's responsibility to close the returned reference once not needed anymore.
     *
     * @return the new reference to be used, null if the value cannot be cached
     */
    fun cache(key: K,
              valueRef: CloseableReference<V>,
              observer: EntryStateObserver<K>?): CloseableReference<V>? {
        maybeUpdateCacheParams()
        val oldExclusive: Entry<K, V>?
        var oldRefToClose: CloseableReference<V>? = null
        var clientRef: CloseableReference<V>? = null
        synchronized(this) {
            // remove the old item (if any) as it is stale now
            oldExclusive = exclusiveEntries.remove(key)
            val oldEntry = cachedEntries.remove(key)
            if (oldEntry != null) {
                makeOrphan(oldEntry)
                oldRefToClose = referenceToClose(oldEntry)
            }
            if (canCacheNewValue(valueRef.get())) {
                val newEntry = Entry.of(key, valueRef, observer)
                cachedEntries.put(key, newEntry)
                clientRef = newClientReference(newEntry)
            }
        }

        CloseableReference.closeSafely(oldRefToClose)
        maybeNotifyExclusiveEntryRemoval(oldExclusive)

        maybeEvictEntries()
        return clientRef
    }

    /**
     * Checks the cache constraints to determine whether the new value can be cached or not.
     */
    @Synchronized
    private fun canCacheNewValue(value: V?): Boolean {
        if (value == null) {
            return false
        }
        val newValueSize = valueDescriptor.getSizeInBytes(value)
        return newValueSize <= memoryCacheParams.maxCacheEntrySize &&
                inUseCount <= memoryCacheParams.maxCacheEntries - 1 &&
                inUseSizeInBytes <= memoryCacheParams.maxCacheSize - newValueSize
    }

    /**
     * Gets the item with the given key, or null if there is no such item.
     *
     * It is the caller's responsibility to close the returned reference once not needed anymore.
     */
    override fun get(key: K): CloseableReference<V>? {
        val oldExclusive: Entry<K, V>?
        var clientRef: CloseableReference<V>? = null
        synchronized(this) {
            oldExclusive = exclusiveEntries.remove(key)
            val entry = cachedEntries.get(key)
            if (entry != null) {
                clientRef = newClientReference(entry)
            }
        }

        maybeNotifyExclusiveEntryRemoval(oldExclusive)
        maybeUpdateCacheParams()
        maybeEvictEntries()
        return clientRef
    }

    /**
     * Creates a new reference for the client.
     */
    @Synchronized
    private fun newClientReference(entry: Entry<K, V>): CloseableReference<V>? {
        increaseClientCount(entry)
        return CloseableReference.of(entry.valueRef.get()) {
            releaseClientReference(entry)
        }
    }

    /** Called when the client closes its reference. */
    private fun releaseClientReference(entry: Entry<K, V>) {
        val isExclusiveAdded: Boolean
        val oldRefToClose: CloseableReference<V>?
        synchronized(this) {
            decreaseClientCount(entry)
            isExclusiveAdded = maybeAddToExclusives(entry)
            oldRefToClose = referenceToClose(entry)
        }
        CloseableReference.closeSafely(oldRefToClose)
        maybeNotifyExclusiveEntryInsertion(if (isExclusiveAdded) entry else null)

        maybeUpdateCacheParams()
        maybeEvictEntries()
    }

    /** Adds the entry to the exclusively owned queue if it is viable for eviction. */
    @Synchronized
    private fun maybeAddToExclusives(entry: Entry<K, V>): Boolean {
        if (!entry.isOrphan && entry.clientCount == 0) {
            exclusiveEntries.put(entry.key, entry)
            return true
        }
        return false
    }

    /**
     * Gets the value with the given key to be reused, or null if there is no such value.
     *
     * The item can be reused only if it is exclusively owned by the cache.
     */
    fun reuse(key: K): CloseableReference<V>? {
        var clientRef: CloseableReference<V>? = null
        var removed = false
        var oldExclusive: Entry<K, V>?
        synchronized(this) {
            oldExclusive = exclusiveEntries.remove(key)
            if (oldExclusive != null) {
                val entry = cachedEntries.remove(key)
                entry?.let {
                    if (it.clientCount == 0) {
                        // optimization: instead of cloning and then closing the original reference,
                        // we just do a move
                        clientRef = entry.valueRef
                        removed = true
                    }
                }
            }
        }
        if (removed) {
            maybeNotifyExclusiveEntryRemoval(oldExclusive)
        }
        return clientRef
    }

    /**
     * Removes all the items from the cache whose key matches the specified predicate.
     *
     * @param predicate returns true if an item with the given key should be removed
     * @return number of the items removed from the cache
     */
    override fun removeAll(predicate: Predicate<K>): Int {
        val oldExclusives: ArrayList<Entry<K, V>>
        val oldEntries: ArrayList<Entry<K, V>>
        synchronized(this) {
            oldExclusives = exclusiveEntries.removeAll(predicate)
            oldEntries = cachedEntries.removeAll(predicate)
            makeOrphans(oldEntries)
        }
        maybeClose(oldEntries)
        maybeNotifyExclusiveEntryRemoval(oldExclusives)
        maybeUpdateCacheParams()
        maybeEvictEntries()
        return oldEntries.size
    }

    /**
     * Removes all the items from the cache.
     */
    fun clear() {
        val oldExclusives: ArrayList<Entry<K, V>>
        val oldEntries: ArrayList<Entry<K, V>>
        synchronized(this) {
            oldExclusives = exclusiveEntries.clear()
            oldEntries = cachedEntries.clear()
            makeOrphans(oldEntries)
        }
        maybeClose(oldEntries)
        maybeNotifyExclusiveEntryRemoval(oldExclusives)
        maybeUpdateCacheParams()
    }

    /**
     * Check if any items from the cache whose key matches the specified predicate.
     *
     * @param predicate returns true if an item with the given key matches
     * @return true is any items matches from the cache
     */
    @Synchronized
    override fun contains(predicate: Predicate<K>): Boolean {
        return !cachedEntries.getMatchingEntries(predicate).isEmpty()
    }

    @Synchronized
    override fun contains(key: K): Boolean {
        return cachedEntries.contains(key)
    }

    /**
     * Trims the cache according to the specified trimming strategy and the given trim type.
     */
    override fun trim(trimType: MemoryTrimType) {
        val oldEntries: ArrayList<Entry<K, V>>?
        val trimRatio = cacheTrimStrategy.getTrimRatio(trimType)
        synchronized(this) {
            val targetCacheSize = (cachedEntries.sizeInBytes * (1 - trimRatio)).toInt()
            val targetEvictionQueueSize = Math.max(0, targetCacheSize - inUseSizeInBytes)
            oldEntries = trimExclusivelyOwnedEntries(Integer.MAX_VALUE, targetEvictionQueueSize)
            makeOrphans(oldEntries)
        }
        maybeClose(oldEntries)
        maybeNotifyExclusiveEntryRemoval(oldEntries)
        maybeUpdateCacheParams()
        maybeEvictEntries()
    }

    /**
     * Updates the cache params (constraints) if enough time has passed since the last update.
     */
    @Synchronized
    private fun maybeUpdateCacheParams() {
        if (lastCacheParamsCheck + PARAMS_INTERCHECK_INTERVAL_MS > SystemClock.uptimeMillis()) {
            return
        }
        lastCacheParamsCheck = SystemClock.uptimeMillis()
        memoryCacheParams = memoryCacheParamsProvider.invoke()
    }

    /**
     * Removes the exclusively owned items until the cache constraints are met.
     *
     * This method invokes the external [CloseableReference.close] method,
     * so it must not be called while holding the `this` lock.
     */
    private fun maybeEvictEntries() {
        val oldEntries: ArrayList<Entry<K, V>>?
        synchronized(this) {
            val maxCount = Math.min(
                    memoryCacheParams.maxEvictionQueueEntries,
                    memoryCacheParams.maxCacheEntries - inUseCount)
            val maxSize = Math.min(
                    memoryCacheParams.maxEvictionQueueSize,
                    memoryCacheParams.maxCacheSize - inUseSizeInBytes)
            oldEntries = trimExclusivelyOwnedEntries(maxCount, maxSize)
            makeOrphans(oldEntries)
        }
        maybeClose(oldEntries)
        maybeNotifyExclusiveEntryRemoval(oldEntries)
    }

    /**
     * Removes the exclusively owned items until there is at most `count` of them
     * and they occupy no more than `size` bytes.
     *
     * This method returns the removed items instead of actually closing them, so it is safe to
     * be called while holding the `this` lock.
     */
    @Synchronized
    private fun trimExclusivelyOwnedEntries(count: Int, size: Int): ArrayList<Entry<K, V>>? {
        val maxCount = Math.max(count, 0)
        val maxSize = Math.max(size, 0)
        // fast path without array allocation if no eviction is necessary
        if (exclusiveEntries.count <= maxCount && exclusiveEntries.sizeInBytes <= maxSize) {
            return null
        }
        val oldEntries = ArrayList<Entry<K, V>>()
        while (exclusiveEntries.count > maxCount || exclusiveEntries.sizeInBytes > maxSize) {
            val key = exclusiveEntries.firstKey
            exclusiveEntries.remove(key!!)
            val old = cachedEntries.remove(key)
            if (old != null) {
                oldEntries.add(old)
            }
        }
        return oldEntries
    }

    /**
     * Notifies the client that the cache no longer tracks the given items.
     *
     * This method invokes the external [CloseableReference.close] method,
     * so it must not be called while holding the `this` lock.
     */
    private fun maybeClose(oldEntries: ArrayList<Entry<K, V>>?) {
        if (oldEntries != null) {
            for (oldEntry in oldEntries) {
                CloseableReference.closeSafely(referenceToClose(oldEntry))
            }
        }
    }

    private fun maybeNotifyExclusiveEntryRemoval(entries: ArrayList<Entry<K, V>>?) {
        if (entries != null) {
            for (entry in entries) {
                maybeNotifyExclusiveEntryRemoval(entry)
            }
        }
    }

    private fun <K, V> maybeNotifyExclusiveEntryRemoval(entry: Entry<K, V>?) {
        entry?.observer?.onExclusivityChanged(entry.key, false)
    }

    private fun <K, V> maybeNotifyExclusiveEntryInsertion(entry: Entry<K, V>?) {
        entry?.observer?.onExclusivityChanged(entry.key, true)
    }

    /** Marks the given entries as orphans. */
    @Synchronized
    private fun makeOrphans(oldEntries: ArrayList<Entry<K, V>>?) {
        if (oldEntries != null) {
            for (oldEntry in oldEntries) {
                makeOrphan(oldEntry)
            }
        }
    }

    /** Marks the entry as orphan. */
    @Synchronized
    private fun makeOrphan(entry: Entry<K, V>) {
        if (entry.isOrphan) {
            throw IllegalStateException()
        }
        entry.isOrphan = true
    }

    /** Increases the entry's client count. */
    @Synchronized
    private fun increaseClientCount(entry: Entry<K, V>) {
        if (entry.isOrphan) {
            throw IllegalStateException()
        }
        entry.clientCount++
    }

    /** Decreases the entry's client count. */
    @Synchronized
    private fun decreaseClientCount(entry: Entry<K, V>) {
        if (entry.clientCount > 0) {
            throw IllegalStateException()
        }
        entry.clientCount--
    }

    /** Returns the value reference of the entry if it should be closed, null otherwise. */
    @Synchronized
    private fun referenceToClose(entry: Entry<K, V>): CloseableReference<V>? {
        return if (entry.isOrphan && entry.clientCount == 0) entry.valueRef else null
    }

    /**
     * Interface used to specify the trimming strategy for the cache.
     */
    interface CacheTrimStrategy {
        fun getTrimRatio(trimType: MemoryTrimType): Double
    }

    /**
     * Interface used to observe the state changes of an entry.
     */
    interface EntryStateObserver<K> {

        /**
         * Called when the exclusivity status of the entry changes.
         *
         * The item can be reused if it is exclusively owned by the cache.
         */
        fun onExclusivityChanged(key: K, isExclusive: Boolean)
    }

    /**
     * The internal representation of a key-value pair stored by the cache.
     */
    class Entry<K, V> private constructor(val key: K,
                                          val valueRef: CloseableReference<V>,
                                          val observer: EntryStateObserver<K>?) {
        var clientCount: Int = 0
        // Whether or not this entry is tracked by this cache. Orphans are not tracked by the cache and
        // as soon as the last client of an orphaned entry closes their reference, the entry's copy is
        // closed too.
        var isOrphan: Boolean = false

        companion object {

            fun <K, V> of(
                    key: K,
                    valueRef: CloseableReference<V>,
                    observer: EntryStateObserver<K>?): Entry<K, V> {
                return Entry(key, valueRef, observer)
            }
        }
    }

    companion object {
        private val PARAMS_INTERCHECK_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5)
    }
}

typealias Predicate<T> = (t: T) -> Boolean

interface ValueDescriptor<V> {

    fun getSizeInBytes(value: V): Int
}
