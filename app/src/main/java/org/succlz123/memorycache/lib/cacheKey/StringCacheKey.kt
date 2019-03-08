package org.succlz123.memorycache.lib.cacheKey

import android.net.Uri

class StringCacheKey(private val key: String) : CacheKey {

    override val uriString: String
        get() {
            return key
        }

    override fun toString(): String {
        return uriString
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other is StringCacheKey) {
            return uriString == other.uriString
        }
        return false
    }

    override fun hashCode(): Int {
        return uriString.hashCode()
    }

    override fun containsUri(uri: Uri): Boolean {
        return uriString.contains(uri.toString())
    }
}
