package tv.own.owntv.glcn

import android.graphics.Color as AColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * glcn: what a programme's picture is like, read from a small copy of it: how bright it is (its brighter quarter, which
 * is where text gets lost) and its most vivid colour. Panels use it to pick a card tone that belongs to the picture and
 * a veil just strong enough for text over the picture to read — no stronger, so the picture stays seen.
 */
class ArtTone(val brightness: Float, val vivid: Color?) {

    /** A dark card in the picture's own colour; null when the picture has no colour to speak of. */
    fun darkCard(): Color? = vivid?.let {
        val hsv = FloatArray(3)
        AColor.colorToHSV(it.toArgbInt(), hsv)
        hsv[1] = (hsv[1] * 0.85f).coerceIn(0.28f, 0.6f)
        hsv[2] = DARK_CARD_VALUE
        Color(AColor.HSVToColor(hsv))
    }

    /** The veil over the picture, under dark text-on-light ([light]) or light text-on-dark: enough for text to read. */
    fun veil(light: Boolean): Float =
        if (light) ((LIGHT_TARGET - brightness) / (1f - brightness).coerceAtLeast(0.01f)).coerceIn(LIGHT_VEIL_MIN, VEIL_MAX)
        else (1f - DARK_TARGET / brightness.coerceAtLeast(0.01f)).coerceIn(DARK_VEIL_MIN, VEIL_MAX)

    companion object {
        private const val DARK_CARD_VALUE = 0.17f
        /** Under light text the picture is kept at or below this brightness; under dark text, at or above. */
        private const val DARK_TARGET = 0.22f
        private const val LIGHT_TARGET = 0.84f
        private const val DARK_VEIL_MIN = 0.30f
        private const val LIGHT_VEIL_MIN = 0.50f
        private const val VEIL_MAX = 0.88f
        private const val SAMPLE_W = 64
        private const val SAMPLE_H = 36
        private const val MIN_VIVID_WEIGHT = 0.04f
        private val cache = ConcurrentHashMap<String, ArtTone>()

        /** The tone of [url]'s picture, once read (null until then, and for no picture). Read once per picture. */
        @Composable
        fun of(url: String?): State<ArtTone?> {
            val ctx = LocalContext.current
            val state = remember(url) { mutableStateOf(url?.let { cache[it] }) }
            LaunchedEffect(url) {
                if (url == null || state.value != null) return@LaunchedEffect
                read(ctx, url)?.let { cache[url] = it; state.value = it }
            }
            return state
        }

        private suspend fun read(ctx: android.content.Context, url: String): ArtTone? = runCatching {
            val request = ImageRequest.Builder(ctx).data(url).size(SAMPLE_W, SAMPLE_H).allowHardware(false).build()
            val result = SingletonImageLoader.get(ctx).execute(request) as? SuccessResult ?: return null
            val bmp = result.image.toBitmap()
            withContext(Dispatchers.Default) {
                val w = bmp.width; val h = bmp.height
                if (w <= 0 || h <= 0) return@withContext null
                val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
                val lum = FloatArray(px.size)
                val hsv = FloatArray(3)
                var wr = 0f; var wg = 0f; var wb = 0f; var wt = 0f
                for (i in px.indices) {
                    val c = px[i]
                    val r = AColor.red(c) / 255f; val g = AColor.green(c) / 255f; val b = AColor.blue(c) / 255f
                    lum[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    AColor.colorToHSV(c, hsv)
                    val wgt = hsv[1] * hsv[1] * hsv[2]
                    wr += r * wgt; wg += g * wgt; wb += b * wgt; wt += wgt
                }
                lum.sort()
                val bright = lum[(lum.size * 3) / 4]
                val vivid = if (wt / px.size >= MIN_VIVID_WEIGHT) Color(wr / wt, wg / wt, wb / wt) else null
                ArtTone(bright, vivid)
            }
        }.getOrNull()

        private fun Color.toArgbInt(): Int =
            AColor.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
    }
}
