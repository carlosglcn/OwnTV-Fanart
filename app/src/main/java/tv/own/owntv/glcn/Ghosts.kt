package tv.own.owntv.glcn

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.TextUnit
import coil3.compose.AsyncImage
import coil3.request.allowHardware
import coil3.toBitmap

/**
 * glcn: "ghosts" — faint, large marks behind a panel's words that hint at what it is about without competing:
 * a channel's logo in grey, a season number or a year in outline, a team's crest. They fade in when their subject
 * changes, so the eye takes them as part of the page rather than a picture.
 */
object Ghosts {
    const val ALPHA = 0.08f
    const val ALPHA_STRONG = 0.13f
    val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
}

/** A logo in grey at [alpha], fitted to [modifier]'s size; nothing while [url] is blank. */
@Composable
fun GhostLogo(url: String?, modifier: Modifier, alpha: Float = Ghosts.ALPHA) {
    if (url.isNullOrBlank()) return
    val a by animateFloatAsState(alpha, tween(GHOST_FADE_MS), label = "ghostLogo")
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = rememberGhostFilter(url),
        alpha = a,
        modifier = modifier,
    )
}

/**
 * [text] drawn as an outline only ([strokeWidth] px at the text's size), in [color] at [alpha] — a season number
 * or a year, huge, behind a list. Drawn in [modifier]'s box from its top-start.
 */
@Composable
fun GhostOutline(text: String, size: TextUnit, color: Color, modifier: Modifier, alpha: Float = Ghosts.ALPHA_STRONG, strokeWidth: Float = 3f) {
    val measurer = rememberTextMeasurer()
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val m = measurer.measure(text, TextStyle(fontSize = size, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = TextUnit.Unspecified))
            drawText(m, color = color, alpha = alpha, drawStyle = Stroke(width = strokeWidth * density))
        }
    }
}

private const val GHOST_FADE_MS = 420

/**
 * glcn: how a ghost logo is drawn. In grey as a rule; but a one-colour logo of the page's own brightness (a white
 * logo on the light theme, a black one on the dark) would vanish, so that one is drawn as its shape in the theme's
 * text colour. The logo is read once, small, and remembered.
 */
@Composable
fun rememberGhostFilter(url: String?): ColorFilter {
    val light = tv.own.owntv.ui.theme.LocalStagePalette.current.isLight
    val text = tv.own.owntv.ui.theme.StageColors.Text
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ink by androidx.compose.runtime.produceState(url?.let { inkCache[it] }, url) {
        val u = url ?: return@produceState
        if (value != null) return@produceState
        value = readInk(ctx, u)?.also { inkCache[u] = it }
    }
    val i = ink ?: return Ghosts.GREY
    if (i == NO_INK) return Ghosts.GREY
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(i, it) }
    val mono = hsv[1] < MONO_MAX_SAT
    val vanishes = mono && if (light) hsv[2] > PALE_MIN else hsv[2] < DARK_MAX
    return if (vanishes) ColorFilter.tint(text, androidx.compose.ui.graphics.BlendMode.SrcIn) else Ghosts.GREY
}

private val inkCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
private const val NO_INK = 1 // an opaque picture (no shape to draw) or none: grey as usual
private const val MONO_MAX_SAT = 0.2f
private const val PALE_MIN = 0.8f
private const val DARK_MAX = 0.2f

/** The logo's ink: its pixels' colour weighted by their opacity; [NO_INK] for a picture without transparency. */
private suspend fun readInk(ctx: android.content.Context, url: String): Int? = runCatching {
    val req = coil3.request.ImageRequest.Builder(ctx).data(url).size(48, 48).allowHardware(false).build()
    val res = coil3.SingletonImageLoader.get(ctx).execute(req) as? coil3.request.SuccessResult ?: return null
    val bmp = res.image.toBitmap()
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        var opaque = 0; var aSum = 0.0; var r = 0.0; var g = 0.0; var b = 0.0
        for (c in px) {
            val a = (c ushr 24) and 0xFF
            if (a > 240) opaque++
            if (a > 0) { aSum += a; r += ((c shr 16) and 0xFF) * a.toDouble(); g += ((c shr 8) and 0xFF) * a.toDouble(); b += (c and 0xFF) * a.toDouble() }
        }
        if (aSum == 0.0 || opaque > px.size * 0.9) NO_INK
        else android.graphics.Color.rgb((r / aSum).toInt().coerceIn(0, 255), (g / aSum).toInt().coerceIn(0, 255), (b / aSum).toInt().coerceIn(0, 255))
    }
}.getOrNull()
