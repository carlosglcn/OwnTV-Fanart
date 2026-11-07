package tv.own.owntv.glcn

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * glcn: how bright a background picture is, so a light one under the dark theme is darkened by itself just enough
 * for the white text to read (the person's own Darken still counts when it is more). Measured once per picture on a
 * tiny copy, off the main thread.
 */
object PictureTone {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Float>()

    suspend fun luminance(context: android.content.Context, path: String): Float? = withContext(Dispatchers.IO) {
        cache[path] ?: runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = SAMPLE }
            val bmp = (
                if (path.startsWith("content:")) context.contentResolver.openInputStream(android.net.Uri.parse(path))?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                else android.graphics.BitmapFactory.decodeFile(path, opts)
                ) ?: return@runCatching null
            var sum = 0.0
            var n = 0
            var y = 0
            while (y < bmp.height) {
                var x = 0
                while (x < bmp.width) {
                    val c = bmp.getPixel(x, y)
                    sum += (0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)) / 255.0
                    n++
                    x += 2
                }
                y += 2
            }
            bmp.recycle()
            if (n == 0) null else (sum / n).toFloat()
        }.getOrNull()?.also { cache[path] = it }
    }

    /** The darkening a picture this bright needs under the dark theme (0 for a dark one). */
    fun autoDim(lum: Float?): Float = if (lum == null) 0f else ((lum - DARK_ENOUGH) * STRENGTH).coerceIn(0f, MAX_DIM)

    private const val SAMPLE = 16
    private const val DARK_ENOUGH = 0.35f
    private const val STRENGTH = 1.5f
    private const val MAX_DIM = 0.62f
}

/** glcn: [PictureTone.autoDim] for this picture, eased in once it is known. */
@Composable
fun rememberAutoDim(path: String): Float {
    val context = LocalContext.current
    val target by produceState(0f, path) { value = if (path.isBlank()) 0f else PictureTone.autoDim(PictureTone.luminance(context, path)) }
    return animateFloatAsState(target, tween(400), label = "autoDim").value
}
