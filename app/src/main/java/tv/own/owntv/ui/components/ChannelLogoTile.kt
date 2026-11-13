package tv.own.owntv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * A channel logo on its tile.
 *
 * Provider logos are overwhelmingly transparent PNGs, so a tile with a solid fill shows that fill
 * through the artwork as a black square around it. The fill is therefore drawn only when there is no
 * logo to show — while [fallback] is on screen, or after the image fails to load.
 *
 * Transparency alone is not enough, though: most logos are drawn for a light background, so a
 * dark-ink one (A&E, ABC) then disappears into the dark UI. Each logo's own ink is measured once and
 * the few that would be unreadable get a plate behind them, in the opposite luminance to the surface
 * they sit on. The artwork itself is never recoloured, and the tile never changes size, so a plate
 * appearing cannot shift the layout around it.
 */
/**
 * glcn: the colour of the text around a logo when it sits on something other than the page — a focused row's fill.
 * Set by [tv.own.owntv.ui.stage.StageSurface] for its focused item (and by lists that draw their own focus), so a logo
 * knows what it is on without every caller passing it: light text means a dark fill (a white logo stays white), dark
 * text a light one (a white logo turns grey).
 */
val LocalLogoInk = androidx.compose.runtime.compositionLocalOf<Color?> { null }

@Composable
fun ChannelLogoTile(
    logoUrl: String?,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier.fillMaxSize(),
    fill: Color = tv.own.owntv.ui.theme.StageColors.Ink.copy(alpha = 0.04f),
    contentScale: ContentScale = ContentScale.Fit,
    surfaceLuminance: Float = tv.own.owntv.ui.theme.StageColors.GlassOff.luminance(),
    fallback: @Composable () -> Unit,
) {
    // null = still loading, true = loaded, false = failed. Keyed on the URL so a recycled row
    // re-evaluates rather than inheriting the previous channel's outcome.
    var loaded by remember(logoUrl) { mutableStateOf<Boolean?>(null) }
    var ink by remember(logoUrl) { mutableStateOf(logoUrl?.let(logoInk::get) ?: LOGO_INK_NONE) }
    var pending by remember(logoUrl) { mutableStateOf<Bitmap?>(null) }
    val showLogo = !logoUrl.isNullOrBlank() && loaded != false

    // Measuring means reading every sampled pixel, so it happens off the main thread, once per logo
    // per run — the cache is consulted above before a row ever asks for it again.
    LaunchedEffect(logoUrl, pending) {
        val bitmap = pending ?: return@LaunchedEffect
        val url = logoUrl ?: return@LaunchedEffect
        val measured = withContext(Dispatchers.Default) {
            if (bitmap.isRecycled) LOGO_INK_NONE else bitmap.measureInk()
        }
        // One int per logo, but a catalog can hold six figures of them. Start over rather than grow
        // without limit; re-measuring a logo the user scrolls back to costs a fraction of a frame.
        if (logoInk.size > MAX_MEASURED_LOGOS) logoInk.clear()
        logoInk[url] = measured
        ink = measured
        pending = null
    }

    // glcn: 0 classic (upstream), 1 none.
    val around = LocalLogoInk.current
    @Suppress("NAME_SHADOWING")
    val surfaceLuminance = if (around == null) surfaceLuminance else if (around.luminance() > 0.5f) ON_DARK_FILL else ON_LIGHT_FILL
    val needsPlate = LogoPlate.mode == 0 && ink != LOGO_INK_NONE &&
        contrastRatio(Color(ink).luminance(), surfaceLuminance) < MIN_CONTRAST
    // glcn: a white one-colour logo (Telecinco) on a light surface is drawn in the theme's text colour instead —
    // its own shape, no plate. Logos with colour, and every logo on a dark surface, are left as they are.
    val inkOnLight = ink != LOGO_INK_NONE && surfaceLuminance >= 0.5f && isPaleMono(ink)
    val logoFilter = if (inkOnLight) PALE_LOGO_ON_LIGHT else null
    val background = when {
        !showLogo -> fill
        inkOnLight -> Color.Transparent
        needsPlate -> if (surfaceLuminance < 0.5f) LightPlate else DarkPlate
        else -> Color.Transparent
    }

    // Hardware bitmaps cannot be read back pixel by pixel. Logo tiles are tiny, so giving up the
    // hardware config costs nothing and is what makes the ink measurement possible at all.
    val platformContext = LocalPlatformContext.current
    val request = remember(logoUrl, platformContext) {
        ImageRequest.Builder(platformContext).data(logoUrl).allowHardware(false).transformations(LogoTrim).build()
    }

    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        if (showLogo) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = contentScale,
                colorFilter = logoFilter,
                modifier = imageModifier,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            loaded = true
                            val url = logoUrl
                            if (url != null && !logoInk.containsKey(url)) {
                                pending = (state.result.image as? BitmapImage)?.bitmap
                            }
                        }
                        is AsyncImagePainter.State.Error -> loaded = false
                        else -> Unit
                    }
                },
            )
        } else {
            fallback()
        }
    }
}

private const val ON_DARK_FILL = 0.12f
private const val ON_LIGHT_FILL = 0.9f

/** Mean ink colour per logo URL, measured once per app run. [LOGO_INK_NONE] means "leave it alone". */
private val logoInk = ConcurrentHashMap<String, Int>()

/** A logo that needs no treatment: fully opaque artwork, or one with nothing to measure. */
private const val LOGO_INK_NONE = 0

/** Below this contrast ratio between the logo's ink and the surface behind it, the logo gets a plate. */
private const val MIN_CONTRAST = 3f

/** glcn: plate mode, set from the shell (like [tv.own.owntv.ui.stage.GlcnMotion]). */
object LogoPlate {
    var mode by mutableStateOf(0)
}

/** Upper bound on the ink cache, so a very large catalog cannot grow it without limit. */
private const val MAX_MEASURED_LOGOS = 4096

// Slightly off-white and off-black rather than pure: a pure-white plate glares on a TV in a dark room.
private val LightPlate = Color(0xFFEDF1F5)
private val DarkPlate = Color(0xFF161C24)

/**
 * The logo's mean colour, weighted by alpha so the transparent surround does not drag every logo
 * towards black. Returns [LOGO_INK_NONE] for artwork that is essentially opaque — such a logo brings
 * its own background and is already readable on any surface.
 */
private fun Bitmap.measureInk(): Int {
    // Cap the work at a 48x48 grid however big the source is; a logo's ink does not need more.
    val stepX = max(1, width / 48)
    val stepY = max(1, height / 48)
    var samples = 0
    var opaque = 0
    var alphaSum = 0.0
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            samples++
            if (alpha > 240) opaque++
            if (alpha > 0) {
                alphaSum += alpha.toDouble()
                r += ((pixel shr 16) and 0xFF).toDouble() * alpha
                g += ((pixel shr 8) and 0xFF).toDouble() * alpha
                b += (pixel and 0xFF).toDouble() * alpha
            }
            x += stepX
        }
        y += stepY
    }
    if (samples == 0 || alphaSum == 0.0 || opaque > samples * 0.9) return LOGO_INK_NONE
    return android.graphics.Color.rgb(
        (r / alphaSum).toInt().coerceIn(0, 255),
        (g / alphaSum).toInt().coerceIn(0, 255),
        (b / alphaSum).toInt().coerceIn(0, 255),
    )
}

/**
 * glcn: a white one-colour logo on a light surface, turned to a soft grey that keeps its own detail: brightness is
 * inverted into a narrow grey band (white → a light grey, dark lines → lighter still), so it reads without turning black
 * and an outline or inner line inside the logo stays visible. Its dark glow or drop shadow (soft, half-transparent
 * dark pixels around the artwork, meant for dark backgrounds) is faded out: turned light, it would ring the logo with
 * a pale haze and make it look blurred.
 */
private val PALE_LOGO_ON_LIGHT = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            -PALE_SPAN * 0.2126f, -PALE_SPAN * 0.7152f, -PALE_SPAN * 0.0722f, 0f, PALE_TOP * 255f,
            -PALE_SPAN * 0.2126f, -PALE_SPAN * 0.7152f, -PALE_SPAN * 0.0722f, 0f, PALE_TOP * 255f,
            -PALE_SPAN * 0.2126f, -PALE_SPAN * 0.7152f, -PALE_SPAN * 0.0722f, 0f, PALE_TOP * 255f,
            HALO_FADE * 0.2126f, HALO_FADE * 0.7152f, HALO_FADE * 0.0722f, 1f, -HALO_FADE * 255f,
        ),
    ),
)

/** Grey of a black pixel (the lightest the logo gets); white comes out at [PALE_TOP] − [PALE_SPAN]. */
private const val PALE_TOP = 0.86f
private const val PALE_SPAN = 0.24f
/** How much opacity a dark pixel loses: a soft dark halo goes, a solid dark line inside the logo stays faintly. */
private const val HALO_FADE = 0.75f

/** glcn: a logo whose ink is near white and has (almost) no colour — a white one-colour logo. */
private fun isPaleMono(ink: Int): Boolean {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(ink, hsv)
    return hsv[1] < PALE_MONO_MAX_SAT && hsv[2] > PALE_MONO_MIN_VALUE
}

private const val PALE_MONO_MAX_SAT = 0.18f
private const val PALE_MONO_MIN_VALUE = 0.82f

/** WCAG contrast ratio between two relative luminances, 1.0 (identical) to 21.0 (black on white). */
private fun contrastRatio(a: Float, b: Float): Float {
    val lighter = max(a, b)
    val darker = if (lighter == a) b else a
    return (lighter + 0.05f) / (darker + 0.05f)
}
