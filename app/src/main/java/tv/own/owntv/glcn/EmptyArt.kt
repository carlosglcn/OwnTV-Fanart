package tv.own.owntv.glcn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent

/**
 * glcn: the picture over an empty page — the app icon's sunset, simply drawn: a sun half set in the accent's
 * tones over three horizon lines, with the page's own [icon] in the sun. Original drawing, no assets.
 */
@Composable
fun EmptyArt(icon: OwnTVIcon, modifier: Modifier = Modifier) {
    val accent = stageAccent.accent
    val ink = StageColors.Text
    Box(modifier.size(ART_W.mpx, ART_H.mpx), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val horizon = size.height * 0.68f
            val r = size.height * 0.5f
            // The sun, cut by the horizon.
            clipRectHorizon(horizon) {
                drawCircle(
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = 0.55f), 1f to accent.copy(alpha = 0.12f),
                        startY = horizon - r, endY = horizon,
                    ),
                    radius = r, center = Offset(w / 2f, horizon),
                )
            }
            // Three horizon lines, shorter as they go down.
            val stroke = 3.mpx.toPx()
            listOf(1f, 0.72f, 0.46f).forEachIndexed { i, f ->
                val y = horizon + i * 12.mpx.toPx()
                val half = w * 0.42f * f
                drawLine(ink.copy(alpha = 0.22f - i * 0.05f), Offset(w / 2f - half, y), Offset(w / 2f + half, y), stroke, StrokeCap.Round)
            }
        }
        OwnTVIcon(icon, ink.copy(alpha = 0.85f), Modifier.offset(y = (ART_H * 0.68f - ICON - 16).mpx).size(ICON.mpx))
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.clipRectHorizon(
    horizon: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) = drawContext.canvas.let {
    it.save()
    it.clipRect(0f, 0f, size.width, horizon)
    block()
    it.restore()
}

private const val ART_W = 240
private const val ART_H = 150
private const val ICON = 40
