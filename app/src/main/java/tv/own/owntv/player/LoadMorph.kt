package tv.own.owntv.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.hudAccent
import tv.own.owntv.ui.theme.hudOnAccent

/**
 * glcn: ONE sign in the middle of the screen while a channel or film opens — a ring that turns, in the place and at
 * the size of the play button. When the picture arrives the ring closes into a full circle, fills, and the play sign
 * grows out of it; a moment later it fades. Stalls again later → the ring again, in the same place. It replaces the
 * spinner that sat on its own, apart from the play button. [hidden]: the HUD's own play button is up (it carries
 * the ring itself, see [SpinRing]). Light mode: no closing, the ring just goes.
 */
@Composable
internal fun LoadMorph(loading: Boolean, hidden: Boolean, modifier: Modifier = Modifier, sizeDp: Int = MORPH_SIZE_DP) {
    var shown by remember { mutableStateOf(loading) }
    val close = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val alpha = remember { Animatable(if (loading) 1f else 0f) }
    val plain = tv.own.owntv.glcn.LowRam.on || !tv.own.owntv.ui.theme.animationsOn
    LaunchedEffect(loading) {
        if (loading) {
            close.snapTo(0f)
            reveal.snapTo(0f)
            shown = true
            alpha.animateTo(1f, tween(FADE_IN_MS))
        } else if (shown) {
            if (!plain) {
                close.animateTo(1f, tween(CLOSE_MS, easing = FastOutSlowInEasing))
                reveal.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f))
                delay(HOLD_MS)
                alpha.animateTo(0f, tween(FADE_OUT_MS))
            } else {
                alpha.snapTo(0f)
            }
            shown = false
        }
    }
    if (hidden || (!shown && alpha.value <= 0f)) return
    val accent = hudAccent
    val onAccent = hudOnAccent
    val spin by rememberInfiniteTransition(label = "loadMorph").animateFloat(
        0f, 360f, infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)), label = "loadMorphSpin",
    )
    Box(modifier.size(sizeDp.dp).graphicsLayer { this.alpha = alpha.value }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 4.dp.toPx()
            val r = size.minDimension / 2f
            drawCircle(Color.White.copy(alpha = 0.18f), radius = r - w / 2f, style = Stroke(w))
            val sweep = ARC_START + (360f - ARC_START) * close.value
            drawArc(
                accent, startAngle = spin - 90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(w / 2f, w / 2f), size = Size(size.width - w, size.height - w),
                style = Stroke(w, cap = StrokeCap.Round),
            )
            val fill = reveal.value.coerceIn(0f, 1f)
            if (fill > 0f) drawCircle(accent, radius = (r - w) * fill)
        }
        val grow = reveal.value
        if (grow > 0f) {
            tv.own.owntv.ui.components.OwnTVIcon(
                OwnTVIcon.PLAY, onAccent, filled = true,
                modifier = Modifier.size((sizeDp * 0.42f).dp).graphicsLayer { scaleX = grow; scaleY = grow },
            )
        }
    }
}

/** glcn: the turning ring round the HUD's play button while the stream loads — the same ring as [LoadMorph]. */
@Composable
internal fun SpinRing(sizeDp: Int, modifier: Modifier = Modifier) {
    val accent = hudAccent
    val spin by rememberInfiniteTransition(label = "spinRing").animateFloat(
        0f, 360f, infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)), label = "spinRingTurn",
    )
    Canvas(modifier.size(sizeDp.dp)) {
        val w = 4.dp.toPx()
        drawArc(
            accent, startAngle = spin - 90f, sweepAngle = ARC_START, useCenter = false,
            topLeft = Offset(w / 2f, w / 2f), size = Size(size.width - w, size.height - w),
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }
}

private const val MORPH_SIZE_DP = 64
private const val ARC_START = 90f
private const val SPIN_MS = 900
private const val FADE_IN_MS = 150
private const val CLOSE_MS = 260
private const val HOLD_MS = 280L
private const val FADE_OUT_MS = 320
