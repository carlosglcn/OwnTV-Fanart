package tv.own.owntv.glcn

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx

/**
 * glcn: what a card looks like while its content is still on its way — a grey shape with a soft light passing over
 * it, instead of an empty gap that suddenly fills. Still (no passing light) with animations off or in light mode.
 */
@Composable
fun Modifier.skeleton(): Modifier {
    val base = StageColors.Ink.copy(alpha = 0.07f)
    val shine = StageColors.Ink.copy(alpha = 0.14f)
    if (!tv.own.owntv.ui.theme.animationsOn || LowRam.on) return this.drawBehind { drawRect(base) }
    val t by rememberInfiniteTransition(label = "skeleton").animateFloat(
        0f, 1f, infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing)), label = "skeletonSweep",
    )
    return this.drawBehind {
        drawRect(base)
        val w = size.width
        val x = -w + t * (w * 3f)
        drawRect(Brush.linearGradient(listOf(base.copy(alpha = 0f), shine, base.copy(alpha = 0f)), start = Offset(x, 0f), end = Offset(x + w, size.height)))
    }
}

/** A poster still loading: the poster's own shape (2:3, rounded), shimmering. */
@Composable
fun SkeletonPoster(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.mpx)).skeleton())
}

private const val SHIMMER_MS = 1_400
