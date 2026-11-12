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
        colorFilter = Ghosts.GREY,
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
