package org.succlz123.memorycache.lib.reference

import android.graphics.Bitmap

import java.io.Closeable
import java.io.IOException

class CloseableBitmap(var bitmap: Bitmap?) : Closeable {

    @Throws(IOException::class)
    override fun close() {
        bitmap?.recycle()
    }
}
