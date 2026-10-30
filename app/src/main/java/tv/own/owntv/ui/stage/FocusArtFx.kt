package tv.own.owntv.ui.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * glcn: the focused card's picture comes alive — it drifts slowly closer (a Ken Burns push-in) and a
 * soft band of light crosses it once as focus lands. Only the focused card animates, so a rail of a
 * hundred posters costs what one does; with animations off in Settings it stays still.
 */
/**
 * glcn: Settings → Appearance → Animation style. Subtle is the Apple TV way: the focused card leans and
 * lifts, nothing else moves — no zoom drift, no light sweep, a still backdrop.
 */
object GlcnMotion {
    var subtle by androidx.compose.runtime.mutableStateOf(false)
}

@Composable
internal fun BoxScope.FocusArtFx(focused: Boolean, content: @Composable BoxScope.() -> Unit) {
    val animate = tv.own.owntv.ui.theme.animationsOn
    val zoom by animateFloatAsState(
        targetValue = if (focused && animate) (if (GlcnMotion.subtle) 1.03f else 1.08f) else 1f,
        animationSpec = tween(if (focused) 6_000 else 400, easing = LinearOutSlowInEasing),
        label = "artZoom",
    )
    val shine = remember { Animatable(-1f) }
    val sweep = animate && !GlcnMotion.subtle && !tv.own.owntv.glcn.LowRam.on
    LaunchedEffect(focused, sweep) {
        if (focused && sweep) {
            shine.snapTo(-0.6f)
            shine.animateTo(1.6f, tween(900, easing = FastOutSlowInEasing))
        } else shine.snapTo(-1f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = zoom; scaleY = zoom }
            .drawWithContent {
                drawContent()
                val s = shine.value
                if (s > -0.9f && s < 1.5f) {
                    val x = size.width * s
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.2f), Color.Transparent),
                            start = Offset(x - size.width * 0.35f, 0f),
                            end = Offset(x + size.width * 0.05f, size.height),
                        ),
                    )
                }
            },
        content = content,
    )
}

/**
 * glcn: the focused card floats in space like on an Apple TV — it leans a few degrees and sways
 * slowly while it is held. Only the focused card moves; read in the draw phase, so it never
 * recomposes the card. Still with animations off.
 */
@Composable
internal fun Modifier.stageTilt(focused: Boolean, delayMs: Long = 0L): Modifier {
    if (!tv.own.owntv.ui.theme.animationsOn || tv.own.owntv.glcn.LowRam.on) return this
    val amp = remember { Animatable(0f) }
    val phase = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        if (focused) {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            kotlinx.coroutines.coroutineScope {
                launch { amp.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
                launch {
                    while (true) {
                        phase.animateTo(phase.value + TWO_PI, tween(7_000, easing = androidx.compose.animation.core.LinearEasing))
                        phase.snapTo(phase.value % TWO_PI)
                    }
                }
            }
        } else {
            amp.animateTo(0f, tween(300))
        }
    }
    return this.graphicsLayer {
        val a = amp.value
        if (a > 0f) {
            cameraDistance = 14f * density
            rotationY = kotlin.math.sin(phase.value) * 3.2f * a
            rotationX = kotlin.math.cos(phase.value) * 2.2f * a
        }
    }
}

private const val TWO_PI = (2 * Math.PI).toFloat()
