package org.succlz123.memorycache.sample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import org.succlz123.memorycache.R
import org.succlz123.memorycache.lib.MemoryCacheCallback
import org.succlz123.memorycache.lib.MemoryCacheManager
import org.succlz123.memorycache.lib.cacheKey.CacheKey
import org.succlz123.memorycache.lib.cacheKey.StringCacheKey
import org.succlz123.memorycache.lib.core.CountingMemoryCache
import org.succlz123.memorycache.lib.core.ValueDescriptor
import org.succlz123.memorycache.lib.reference.CloseableReference

class MainActivity : AppCompatActivity() {
    private lateinit var cacheManager: MemoryCacheManager<CacheKey, Bitmap>

    private lateinit var insert: Button
    private lateinit var get: Button
    private lateinit var delete: Button
    private lateinit var contains: Button
    private lateinit var clear: Button
    private lateinit var cacheImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        insert = findViewById(R.id.insert)
        get = findViewById(R.id.get)
        delete = findViewById(R.id.delete)
        clear = findViewById(R.id.clear)
        contains = findViewById(R.id.contains)
        cacheImage = findViewById(R.id.imageView)

        val vd = object : ValueDescriptor<Bitmap> {
            override fun getSizeInBytes(value: Bitmap): Int {
                return value.byteCount
            }
        }
        val dbmcps = BitmapMemoryCacheParams(applicationContext)
        val countingCache = CountingMemoryCache<CacheKey, Bitmap>(vd, MemoryCacheManager.DEFAULT_TRIM_STRATEGY,
                dbmcps)

        cacheManager = MemoryCacheManager(countingCache, object : MemoryCacheCallback<CacheKey> {

            override fun onCacheHit(cacheKey: CacheKey) {
                Toast.makeText(this@MainActivity, "hit", Toast.LENGTH_SHORT).show()
            }

            override fun onCacheMiss() {
                Toast.makeText(this@MainActivity, "miss", Toast.LENGTH_SHORT).show()
            }

            override fun onCacheInsert() {
                Toast.makeText(this@MainActivity, "insert", Toast.LENGTH_SHORT).show()
            }
        })

        insert.setOnClickListener { insertCache() }
        get.setOnClickListener { getCache() }
        contains.setOnClickListener {
            if (containsCache(StringCacheKey("1"))) {
                Toast.makeText(this@MainActivity, "key 1 existence", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "key 1 inexistence", Toast.LENGTH_SHORT).show()
            }
        }
        clear.setOnClickListener { clearCache() }
        delete.setOnClickListener {
            Toast.makeText(this@MainActivity, "delete" + deleteCache(StringCacheKey("1")), Toast.LENGTH_SHORT).show()
        }
    }

    private fun insertCache() {
        val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        val bcr = cacheManager.cache(StringCacheKey("1"),
                CloseableReference.of(bitmap) { value: Bitmap ->
                    try {
                        value.recycle()
                    } catch (ignored: Exception) {
                    }
                })
    }

    private fun getCache() {
        val closeableReference = cacheManager.get(StringCacheKey("1"))
        if (closeableReference != null) {
            cacheImage.setImageBitmap(closeableReference.get())
        } else {
            cacheImage.setImageBitmap(null)
        }
        closeableReference?.close()
    }

    private fun containsCache(cacheKey: CacheKey): Boolean {
        return cacheManager.contains {
            cacheKey == it
        }
    }

    private fun deleteCache(cacheKey: CacheKey): Int {
        return cacheManager.removeAll {
            cacheKey == it
        }
    }

    private fun clearCache() {
        cacheManager.clear()
    }
}
