package org.succlz123.memorycache.lib.core

import java.util.*

/**
 * Map that keeps track of the elements order (according to the LRU policy) and their size.
 */

class CountingLruMap<K, V>(private val valueDescriptor: ValueDescriptor<V>) {
    private val map = LinkedHashMap<K, V>()

    /** Gets the total size in bytes of the elements in the map. */
    @get:Synchronized
    var sizeInBytes = 0
        private set

    val keys: ArrayList<K>
        @Synchronized get() = ArrayList(map.keys)

    val values: ArrayList<V>
        @Synchronized get() = ArrayList(map.values)

    /** Gets the count of the elements in the map. */
    val count: Int
        @Synchronized get() = map.size

    /** Gets the key of the first element in the map. */
    val firstKey: K?
        @Synchronized get() = if (map.isEmpty()) null else map.keys.iterator().next()

    /** Gets the all matching elements. */
    @Synchronized
    fun getMatchingEntries(predicate: Predicate<K>?): ArrayList<MutableMap.MutableEntry<K, V>> {
        val matchingEntries = ArrayList<MutableMap.MutableEntry<K, V>>(map.entries.size)
        for (entry in map.entries) {
            if (predicate == null || predicate.invoke(entry.key)) {
                matchingEntries.add(entry)
            }
        }
        return matchingEntries
    }

    /** Returns whether the map contains an element with the given key. */
    @Synchronized
    operator fun contains(key: K): Boolean {
        return map.containsKey(key)
    }

    /** Gets the element from the map. */
    @Synchronized
    operator fun get(key: K): V? {
        return map[key]
    }

    /** Adds the element to the map, and removes the old element with the same key if any. */
    @Synchronized
    fun put(key: K, value: V): V? {
        // We do remove and insert instead of just replace, in order to cause a structural change
        // to the map, as we always want the latest inserted element to be last in the queue.
        val oldValue = map.remove(key)
        sizeInBytes -= getValueSizeInBytes(oldValue)
        map[key] = value
        sizeInBytes += getValueSizeInBytes(value)
        return oldValue
    }

    /** Removes the element from the map. */
    @Synchronized
    fun remove(key: K): V? {
        val oldValue = map.remove(key)
        sizeInBytes -= getValueSizeInBytes(oldValue)
        return oldValue
    }

    /** Removes all the matching elements from the map. */
    @Synchronized
    fun removeAll(predicate: Predicate<K>?): ArrayList<V> {
        val oldValues = ArrayList<V>()
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (predicate == null || predicate.invoke(entry.key)) {
                oldValues.add(entry.value)
                sizeInBytes -= getValueSizeInBytes(entry.value)
                iterator.remove()
            }
        }
        return oldValues
    }

    /** Clears the map. */
    @Synchronized
    fun clear(): ArrayList<V> {
        val oldValues = ArrayList(map.values)
        map.clear()
        sizeInBytes = 0
        return oldValues
    }

    private fun getValueSizeInBytes(value: V?): Int {
        return if (value == null) 0 else valueDescriptor.getSizeInBytes(value)
    }
}
