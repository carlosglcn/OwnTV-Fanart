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

/** glcn: the way the last arrow went (-1, 0, 1 across and down), set from the window's keys — the focused card leans from it. */
object FocusMotion {
    @Volatile var dx = 0f
    @Volatile var dy = 0f

    fun onKey(event: android.view.KeyEvent) {
        if (event.action != android.view.KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { dx = -1f; dy = 0f }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { dx = 1f; dy = 0f }
            android.view.KeyEvent.KEYCODE_DPAD_UP -> { dx = 0f; dy = -1f }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { dx = 0f; dy = 1f }
        }
    }
}

/** glcn: the focused card's lean, shared with its picture (only one card is focused at a time) for the depth effect. */
internal class TiltAnims(val amp: Animatable<Float, *>, val phase: Animatable<Float, *>, val kickX: Animatable<Float, *>, val kickY: Animatable<Float, *>) {
    val rotY: Float get() = kotlin.math.sin(phase.value) * SWAY_Y * amp.value + kickY.value
    val rotX: Float get() = kotlin.math.cos(phase.value) * SWAY_X * amp.value + kickX.value
}

internal object FocusTilt {
    var active by androidx.compose.runtime.mutableStateOf<TiltAnims?>(null)
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
            .graphicsLayer {
                scaleX = zoom; scaleY = zoom
                // glcn: depth — the picture slides a little against the card's lean, as if behind glass.
                val t = if (focused) FocusTilt.active else null
                if (t != null) {
                    val cover = maxOf(zoom, 1f + PARALLAX * 2.5f * t.amp.value.coerceAtLeast(kotlin.math.abs(t.kickY.value) / KICK_DEG))
                    scaleX = cover; scaleY = cover
                    translationX = -(t.rotY / SWAY_Y).coerceIn(-1f, 1f) * size.width * PARALLAX
                    translationY = (t.rotX / SWAY_X).coerceIn(-1f, 1f) * size.height * PARALLAX
                }
            }
            .drawWithContent {
                drawContent()
                // glcn: a soft gloss that follows the lean, on the side tipped towards the light.
                val t = if (focused && sweep) FocusTilt.active else null
                if (t != null && t.amp.value > 0.01f) {
                    val cx = size.width * (0.5f + (t.rotY / SWAY_Y).coerceIn(-1f, 1f) * 0.45f)
                    val cy = size.height * (0.2f - (t.rotX / SWAY_X).coerceIn(-1f, 1f) * 0.25f)
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = GLOSS * t.amp.value), Color.Transparent),
                            center = Offset(cx, cy), radius = size.maxDimension * 0.6f,
                        ),
                    )
                }
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
    val kickX = remember { Animatable(0f) }
    val kickY = remember { Animatable(0f) }
    val anims = remember { TiltAnims(amp, phase, kickX, kickY) }
    LaunchedEffect(focused) {
        if (focused) {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            FocusTilt.active = anims
            // glcn: momentum — on arrival the card tips the way the focus travelled and springs back, then sways.
            kickY.snapTo(FocusMotion.dx * KICK_DEG)
            kickX.snapTo(-FocusMotion.dy * KICK_DEG * 0.8f)
            kotlinx.coroutines.coroutineScope {
                launch { kickY.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = KICK_DAMPING, stiffness = KICK_STIFFNESS)) }
                launch { kickX.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = KICK_DAMPING, stiffness = KICK_STIFFNESS)) }
                launch { amp.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
                launch {
                    while (true) {
                        phase.animateTo(phase.value + TWO_PI, tween(7_000, easing = androidx.compose.animation.core.LinearEasing))
                        phase.snapTo(phase.value % TWO_PI)
                    }
                }
            }
        } else {
            if (FocusTilt.active === anims) FocusTilt.active = null
            kickX.snapTo(0f); kickY.snapTo(0f)
            amp.animateTo(0f, tween(300))
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (FocusTilt.active === anims) FocusTilt.active = null } }
    return this.graphicsLayer {
        val ry = anims.rotY
        val rx = anims.rotX
        if (ry != 0f || rx != 0f) {
            cameraDistance = 14f * density
            rotationY = ry
            rotationX = rx
        }
    }
}

private const val TWO_PI = (2 * Math.PI).toFloat()
private const val SWAY_Y = 3.2f
private const val SWAY_X = 2.2f
/** How far the picture slides against the lean, as a share of its size (the zoom keeps the edges covered). */
private const val PARALLAX = 0.012f
private const val GLOSS = 0.14f
private const val KICK_DEG = 7f
private const val KICK_DAMPING = 0.42f
private const val KICK_STIFFNESS = 240f

/** glcn: OK pressed on a card or a button: it sinks a touch and springs back, so the press is felt. */
@Composable
internal fun Modifier.pressSink(pressed: Boolean): Modifier {
    if (!tv.own.owntv.ui.theme.animationsOn || tv.own.owntv.glcn.LowRam.on) return this
    val s by animateFloatAsState(
        if (pressed) PRESS_SCALE else 1f,
        if (pressed) tween(PRESS_IN_MS) else androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "pressSink",
    )
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

private const val PRESS_SCALE = 0.95f
private const val PRESS_IN_MS = 90
