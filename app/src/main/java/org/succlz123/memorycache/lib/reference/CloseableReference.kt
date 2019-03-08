package org.succlz123.memorycache.lib.reference

import org.succlz123.memorycache.lib.MemoryCacheUtils
import java.io.Closeable
import java.io.IOException
import java.util.*

class CloseableReference<T> : Cloneable, Closeable {
    private val sharedReference: SharedReference<T>
    private var mIsClosed = false

    val isValid: Boolean
        get() = !mIsClosed

    val valueHash: Int
        get() = if (isValid) System.identityHashCode(sharedReference.get()) else 0

    internal constructor(sharedReference: SharedReference<T>) {
        this.sharedReference = sharedReference
        sharedReference.addReference()
    }

    internal constructor(t: T, resourceReleaser: ResourceReleaser<T>) {
        sharedReference = SharedReference(t, resourceReleaser)
    }

    override fun close() {
        synchronized(this) {
            if (mIsClosed) {
                return
            }
            mIsClosed = true
        }
        sharedReference.deleteReference()
    }

    @Synchronized
    fun get(): T? {
        if (mIsClosed) {
            return null
        }
        return sharedReference.get()
    }

    @Synchronized
    public override fun clone(): CloseableReference<T> {
        if (!isValid) {
            throw IllegalStateException()
        }
        return CloseableReference(sharedReference)
    }

    @Synchronized
    fun cloneOrNull(): CloseableReference<T>? {
        return if (isValid) {
            CloseableReference(sharedReference)
        } else {
            null
        }
    }

    protected fun finalize() {
        try {
            // We put synchronized here so that lint doesn't warn about accessing mIsClosed, which is
            // guarded by this. Lint isn't aware of finalize semantics.
            synchronized(this) {
                if (mIsClosed) {
                    return
                }
            }
//            val thisCode = System.identityHashCode(this)
//            val referenceCode = System.identityHashCode(sharedReference)
//            val shared = sharedReference.get()
//            val str = "Finalized without closing: $thisCode $referenceCode $shared"
            close()
        } catch (e: Exception) {
        }
    }

    companion object {

        private val DEFAULT_CLOSEABLE_RELEASER = { value: Closeable ->
            try {
                MemoryCacheUtils.close(value, true)
            } catch (ignore: IOException) {
                // This will not happen, Closeable.close swallows and logs IOExceptions
            }
        }

        @JvmStatic
        fun of(t: Closeable?): CloseableReference<Closeable>? {
            return if (t == null) {
                null
            } else {
                CloseableReference(t, DEFAULT_CLOSEABLE_RELEASER)
            }
        }

        @JvmStatic
        fun <T> of(t: T?, resourceReleaser: ResourceReleaser<T>): CloseableReference<T>? {
            return if (t == null) {
                null
            } else {
                CloseableReference(t, resourceReleaser)
            }
        }

        fun isValid(ref: CloseableReference<*>?): Boolean {
            return ref != null && ref.isValid
        }

        fun <T> cloneOrNull(ref: CloseableReference<T>?): CloseableReference<T>? {
            return ref?.cloneOrNull()
        }

        fun <T> cloneOrNull(refs: Collection<CloseableReference<T>>?): List<CloseableReference<T>>? {
            if (refs == null) {
                return null
            }
            val ret = ArrayList<CloseableReference<T>>(refs.size)
            for (ref in refs) {
                val result = cloneOrNull(ref)
                if (result != null) {
                    ret.add(result)
                }
            }
            return ret
        }

        fun closeSafely(ref: CloseableReference<*>?) {
            ref?.close()
        }

        fun closeSafely(references: Iterable<CloseableReference<*>>?) {
            if (references != null) {
                for (ref in references) {
                    closeSafely(ref)
                }
            }
        }
    }
}

typealias ResourceReleaser<T> = (value: T) -> Unit
