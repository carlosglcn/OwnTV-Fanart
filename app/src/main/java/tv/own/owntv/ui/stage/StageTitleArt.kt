package tv.own.owntv.ui.stage

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.StageColors

/**
 * TMDB title art (a film's or show's logo). In dark it is drawn as it is. On the light page white
 * lettering would vanish, so each logo is looked at once when it loads: if it sits on a transparent
 * background, its white and near-white pixels are redrawn in the text colour and coloured parts keep their
 * colour (INSIDIOUS' red letters stay red); art with its own solid background is left as it is (Bambi).
 * [onFailed] lets the caller fall back to the text title.
 */
@Composable
fun StageTitleArt(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart,
    onFailed: () -> Unit,
    onDarkScreen: Boolean = false,
) {
    // On a screen that is dark whatever the style (the programme info), near-black lettering is the
    // one that vanishes, so it is redrawn light; the light-page treatment must not apply there.
    val light = !onDarkScreen && LocalStagePalette.current.isLight
    val ink = StageColors.Text.toArgb()
    val lift = onDarkScreen
    var inked by remember(url, light, lift) { mutableStateOf(if (lift) LightArt.cacheDark[url] else if (light) LightArt.cache[url] else null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Pixels can only be read from a software bitmap, so light asks for one; dark keeps the default.
    val model = remember(url, light, lift) {
        if (light || lift) ImageRequest.Builder(context).data(url).allowHardware(false).build() else url
    }
    val shown = inked
    if (shown != null) {
        Image(shown, contentDescription, modifier, alignment = alignment, contentScale = ContentScale.Fit)
        return
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        alignment = alignment,
        onState = { state ->
            when (state) {
                is AsyncImagePainter.State.Error -> onFailed()
                is AsyncImagePainter.State.Success -> if (light || lift) {
                    val source = state.result.image.toBitmap()
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            if (lift) LightArt.whiteOnClear(source) else LightArt.inkOnClear(source, ink)
                        }
                        if (result != null) {
                            if (lift) LightArt.cacheDark[url] = result else LightArt.cache[url] = result
                            inked = result
                        }
                    }
                }
                else -> Unit
            }
        },
        modifier = modifier,
    )
}

private object LightArt {
    /** Per logo URL, so a logo is only redrawn once per run (a few logos; each a few hundred KB at most). */
    val cache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()
    val cacheDark = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

    /** The mirror of [inkOnClear] for a dark screen: near-black, low-colour pixels become white. */
    fun whiteOnClear(source: Bitmap): ImageBitmap? {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return null
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        if (px.count { (it ushr 24) < 26 } < px.size / 4) return null
        var changed = 0
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            if (a < 8) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
            val chroma = (maxOf(r, g, b) - minOf(r, g, b)) / 255.0
            if (luma <= 0.3 && chroma < 0.25) {
                px[i] = (a shl 24) or 0xFFFFFF
                changed++
            }
        }
        if (changed == 0) return null
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /**
     * Null when the art has its own background (under a quarter of it transparent) or nothing to change;
     * otherwise a copy with every light pixel (luma ≥ 0.6, low colour) in [ink], its alpha kept, so
     * anti-aliased edges stay smooth.
     */
    fun inkOnClear(source: Bitmap, ink: Int): ImageBitmap? {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return null
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val clear = px.count { (it ushr 24) < 26 }
        if (clear < px.size / 4) return null
        val inkRgb = ink and 0xFFFFFF
        var changed = 0
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            if (a < 8) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
            val chroma = (maxOf(r, g, b) - minOf(r, g, b)) / 255.0
            if (luma >= 0.6 && chroma < 0.25) {
                px[i] = (a shl 24) or inkRgb
                changed++
            }
        }
        if (changed == 0) return null
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
