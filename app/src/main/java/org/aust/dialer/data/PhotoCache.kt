package org.aust.dialer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache

/** Tiny in-memory contact-photo cache. Photos are read straight from the Contacts Provider. */
object PhotoCache {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing = HashSet<String>()

    private fun key(uri: String, px: Int) = "$uri@$px"

    fun peek(uri: String, px: Int): Bitmap? = cache.get(key(uri, px))

    /** Blocking; call from a background thread. Returns null if the photo is missing or unreadable. */
    fun load(context: Context, uriString: String, targetPx: Int): Bitmap? {
        val cacheKey = key(uriString, targetPx)
        cache.get(cacheKey)?.let { return it }
        synchronized(missing) { if (uriString in missing) return null }
        val bitmap = try {
            val uri = Uri.parse(uriString)
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            null
        }
        if (bitmap != null) cache.put(cacheKey, bitmap) else synchronized(missing) { missing.add(uriString) }
        return bitmap
    }
}
