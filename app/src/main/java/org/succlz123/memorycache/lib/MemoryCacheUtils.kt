package org.succlz123.memorycache.lib

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.support.v4.util.Pools
import android.util.Pair
import java.io.*
import java.nio.ByteBuffer

internal object MemoryCacheUtils {

    @Throws(IOException::class)
    fun close(closeable: Closeable?, swallowIOException: Boolean) {
        if (closeable == null) {
            return
        }
        try {
            closeable.close()
        } catch (e: IOException) {
            if (swallowIOException) {
            } else {
                throw e
            }
        }
    }
}

internal object BitmapUtil {
    private val DECODE_BUFFER_SIZE = 16 * 1024
    private val POOL_SIZE = 12
    private val DECODE_BUFFERS = Pools.SynchronizedPool<ByteBuffer>(POOL_SIZE)

    /**
     * Bytes per pixel definitions
     */
    val ALPHA_8_BYTES_PER_PIXEL = 1
    val ARGB_4444_BYTES_PER_PIXEL = 2
    val ARGB_8888_BYTES_PER_PIXEL = 4
    val RGB_565_BYTES_PER_PIXEL = 2

    val MAX_BITMAP_SIZE = 2048f

    /**
     * @return size in bytes of the underlying bitmap
     */
    @SuppressLint("NewApi")
    fun getSizeInBytes(bitmap: Bitmap?): Int {
        if (bitmap == null) {
            return 0
        }

        // There's a known issue in KitKat where getAllocationByteCount() can throw an NPE. This was
        // apparently fixed in MR1: http://bit.ly/1IvdRpd. So we do a version check here, and
        // catch any potential NPEs just to be safe.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            try {
                return bitmap.allocationByteCount
            } catch (npe: NullPointerException) {
                // Swallow exception and try fallbacks.
            }

        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            bitmap.byteCount
        } else bitmap.rowBytes * bitmap.height

        // Estimate for earlier platforms. Same code as getByteCount() for Honeycomb.
    }

    /**
     * Decodes only the bounds of an image and returns its width and height or null if the size can't
     * be determined
     * @param bytes the input byte array of the image
     * @return dimensions of the image
     */
    fun decodeDimensions(bytes: ByteArray): Pair<Int, Int>? {
        // wrapping with ByteArrayInputStream is cheap and we don't have duplicate implementation
        return decodeDimensions(ByteArrayInputStream(bytes))
    }

    /**
     * Decodes only the bounds of an image and returns its width and height or null if the size can't
     * be determined
     * @param is the InputStream containing the image data
     * @return dimensions of the image
     */
    fun decodeDimensions(inputStream: InputStream): Pair<Int, Int>? {
        var byteBuffer = DECODE_BUFFERS.acquire()
        if (byteBuffer == null) {
            byteBuffer = ByteBuffer.allocate(DECODE_BUFFER_SIZE)
        }
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        try {
            options.inTempStorage = byteBuffer!!.array()
            BitmapFactory.decodeStream(inputStream, null, options)
            return if (options.outWidth == -1 || options.outHeight == -1)
                null
            else
                Pair(options.outWidth, options.outHeight)
        } finally {
            DECODE_BUFFERS.release(byteBuffer!!)
        }
    }

    /**
     * Returns the amount of bytes used by a pixel in a specific
     * [android.graphics.Bitmap.Config]
     * @param bitmapConfig the [android.graphics.Bitmap.Config] for which the size in byte
     * will be returned
     * @return
     */
    fun getPixelSizeForBitmapConfig(bitmapConfig: Bitmap.Config): Int {
        when (bitmapConfig) {
            Bitmap.Config.ARGB_8888 -> return ARGB_8888_BYTES_PER_PIXEL
            Bitmap.Config.ALPHA_8 -> return ALPHA_8_BYTES_PER_PIXEL
            Bitmap.Config.ARGB_4444 -> return ARGB_4444_BYTES_PER_PIXEL
            Bitmap.Config.RGB_565 -> return RGB_565_BYTES_PER_PIXEL
        }
        throw UnsupportedOperationException("The provided Bitmap.Config is not supported")
    }

    /**
     * Returns the size in byte of an image with specific size
     * and [android.graphics.Bitmap.Config]
     * @param width the width of the image
     * @param height the height of the image
     * @param bitmapConfig the [android.graphics.Bitmap.Config] for which the size in byte
     * will be returned
     * @return
     */
    fun getSizeInByteForBitmap(width: Int, height: Int, bitmapConfig: Bitmap.Config): Int {
        return width * height * getPixelSizeForBitmapConfig(bitmapConfig)
    }
}
