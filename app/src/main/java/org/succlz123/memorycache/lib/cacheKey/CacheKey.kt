package org.succlz123.memorycache.lib.cacheKey

import android.net.Uri

interface CacheKey {

    /**
     * Returns a string representation of the URI at the heart of the cache key. In cases of multiple
     * keys being contained, the first is returned.
     */
    val uriString: String

    /**
     * This is useful for instrumentation and debugging purposes.
     */
    override fun toString(): String

    /**
     * This method must be implemented, otherwise the cache keys will be be compared by reference.
     */
    override fun equals(other: Any?): Boolean

    /**
     * This method must be implemented with accordance to the [.equals] method.
     */
    override fun hashCode(): Int

    /**
     * Returns true if this key was constructed from this [Uri].
     *
     * Used for cases like deleting all keys for a given uri.
     */
    fun containsUri(uri: Uri): Boolean
}
