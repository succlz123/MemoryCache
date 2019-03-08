package org.succlz123.memorycache.lib.reference

import java.util.*

class SharedReference<T>(private var value: T?, private val resourceReleaser: ResourceReleaser<T>) {
    @get:Synchronized
    var refCount: Int = 1
        private set

    val isValid: Boolean
        @Synchronized get() = refCount > 0

    init {
        addLiveReference(value as Any)
    }

    @Synchronized
    fun get(): T? {
        return value
    }

    @Synchronized
    fun addReference() {
        ensureValid()
        refCount++
    }

    fun deleteReference() {
        if (decreaseRefCount() == 0) {
            val deleted: T?
            synchronized(this) {
                deleted = value
                value = null
            }
            deleted?.let {
                resourceReleaser.invoke(deleted)
                removeLiveReference(deleted)
            }
        }
    }

    @Synchronized
    private fun decreaseRefCount(): Int {
        ensureValid()
        if (refCount <= 0) {
            throw IllegalArgumentException()
        }
        refCount--
        return refCount
    }

    private fun ensureValid() {
        if (!isValid(this)) {
            throw NullReferenceException()
        }
    }

    class NullReferenceException : RuntimeException("Null shared reference")

    companion object {
        private val sLiveObjects = IdentityHashMap<Any, Int>()

        private fun addLiveReference(value: Any) {
            synchronized(sLiveObjects) {
                val count = sLiveObjects[value]
                if (count == null) {
                    sLiveObjects.put(value, 1)
                } else {
                    sLiveObjects.put(value, count + 1)
                }
            }
        }

        private fun removeLiveReference(value: Any?) {
            if (value == null) {
                return
            }
            synchronized(sLiveObjects) {
                val count = sLiveObjects[value]
                when (count) {
                    null -> {
                        // Uh oh.
                        // "No entry in sLiveObjects for value of type ${value.javaClass}"
                    }
                    1 -> sLiveObjects.remove(value)
                    else -> sLiveObjects.put(value, count - 1)
                }
            }
        }

        fun isValid(ref: SharedReference<*>?): Boolean {
            return ref != null && ref.isValid
        }
    }
}
