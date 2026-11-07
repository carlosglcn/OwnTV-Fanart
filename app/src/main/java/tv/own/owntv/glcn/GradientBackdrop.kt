package tv.own.owntv.glcn

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import tv.own.owntv.core.theme.GlcnStyle

/** glcn: the picture in focus on the screen (a billboard, a title, the live programme), for "Fanart vivo". */
object FanartAmbient {
    var url by mutableStateOf<String?>(null)
}

/**
 * glcn: the backdrop of the gradient styles, behind everything. Still styles draw their gradient once; "drift" ones
 * move three soft lights very slowly (a full round in about 40 s, still in the light mode / with animations off);
 * "art" takes the focused picture's colour into the lights, changing gently from one picture to the next.
 */
@Composable
fun GradientBackdrop(style: GlcnStyle, modifier: Modifier = Modifier) {
    val stops = style.gradient?.map { Color(it) } ?: return
    val moving = style.motion != GlcnStyle.MOTION_STILL && !LowRam.on && tv.own.owntv.ui.theme.animationsOn
    val t = if (moving) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            0f, 1f, infiniteRepeatable(tween(DRIFT_MS, easing = LinearEasing), RepeatMode.Restart), label = "auroraT",
        ).value
    } else 0.18f
    val artTone = if (style.motion == GlcnStyle.MOTION_ART) tv.own.owntv.ui.components.rememberArtColor(FanartAmbient.url) else null
    val lead by animateColorAsState(artTone?.let { lift(it) } ?: stops[0], tween(900), label = "artLead")
    val second by animateColorAsState(artTone?.let { shift(it) } ?: stops[1], tween(1100), label = "artSecond")
    Canvas(modifier) {
        if (style.motion == GlcnStyle.MOTION_STILL) drawStill(stops)
        else drawLights(listOf(lead, second, stops[2]), stops.last(), t)
    }
}

/** A still gradient, corner to corner, with a soft darkening at the foot so text there keeps its contrast. */
fun DrawScope.drawStill(stops: List<Color>) {
    drawRect(Brush.linearGradient(stops, Offset(0f, 0f), Offset(size.width, size.height)))
    drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f)))
}

/** Three large soft lights over a dark page, each on its own slow round (t in 0..1). */
fun DrawScope.drawLights(lights: List<Color>, page: Color, t: Float) {
    drawRect(page)
    val w = size.width
    val h = size.height
    val two = (2 * Math.PI).toFloat()
    val spots = listOf(
        Triple(0.18f, 0.18f, 0.0f),
        Triple(0.58f, 0.32f, 0.33f),
        Triple(0.86f, 0.06f, 0.66f),
    )
    lights.forEachIndexed { i, c ->
        val (bx, by, ph) = spots[i]
        val a = (t + ph) * two
        val cx = w * (bx + 0.06f * kotlin.math.cos(a))
        val cy = h * (by + 0.08f * kotlin.math.sin(a))
        val r = w * 0.42f
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.78f), c.copy(alpha = 0.28f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
    }
    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.4f)))
}

private fun lift(c: Color): Color = androidx.compose.ui.graphics.lerp(c, Color.White, 0.12f)
private fun shift(c: Color): Color = Color(c.blue, c.red * 0.8f, c.green, 1f)

private const val DRIFT_MS = 40_000

/**
 * glcn: true while something with its own colour fills the screen behind the app — a photo, an animated wallpaper or
 * a gradient style. Screens that would lay a picture of their own behind their text use a quiet glass panel instead.
 */
object BusyBackdrop {
    var on by androidx.compose.runtime.mutableStateOf(false)
}
