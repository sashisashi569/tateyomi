package app.tateyomi.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun peek(file: File, maxDimension: Int): Bitmap? {
        if (!file.isFile) return null
        return cache.get("${file.absolutePath}@$maxDimension")
    }

    suspend fun load(file: File, maxDimension: Int): Bitmap? {
        if (!file.isFile) return null
        val key = "${file.absolutePath}@$maxDimension"
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { decode(file, maxDimension) }.getOrNull()
        } ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun decode(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension &&
            bounds.outHeight / (sample * 2) >= maxDimension
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val largest = maxOf(decoded.width, decoded.height)
        if (largest <= maxDimension) return decoded
        val scale = maxDimension.toFloat() / largest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}

@Composable
fun rememberBitmap(file: File?, maxDimension: Int): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, file?.absolutePath, maxDimension) {
        if (file == null) return@produceState
        value = ImageLoader.peek(file, maxDimension)?.asImageBitmap()
            ?: ImageLoader.load(file, maxDimension)?.asImageBitmap()
    }
    return state.value
}
