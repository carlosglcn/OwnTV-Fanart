package tv.own.owntv.glcn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import tv.own.owntv.ui.theme.mpx

/**
 * glcn: a screen that builds up piece by piece. A piece fades in while rising a few pixels (or sliding in from the
 * right) a little after the one before it. A [StaggerGate] keeps it to the first moments of a screen: lazy lists
 * compose their items as they scroll into view, and those later ones must simply be there, not animate again.
 */
class StaggerGate {
    private val born = android.os.SystemClock.uptimeMillis()
    fun open(): Boolean = android.os.SystemClock.uptimeMillis() - born < 900L
}

/** One gate per screen (or per list, when [key] changes — a new category — the build-up plays again). */
@Composable
fun rememberStaggerGate(key: Any? = Unit): StaggerGate = remember(key) { StaggerGate() }

private val StaggerEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** [index]th piece: [step] ms after the one before, never more than [cap] steps in. */
@Composable
fun Modifier.staggerAt(index: Int, gate: StaggerGate? = null, step: Int = 60, cap: Int = 10, fromRight: Boolean = false): Modifier =
    staggerIn(delayMs = index.coerceIn(0, cap) * step, fromRight = fromRight, gate = gate)

@Composable
fun Modifier.staggerIn(delayMs: Int, fromRight: Boolean = false, gate: StaggerGate? = null): Modifier {
    if (LowRam.on) return this
    // Decided once, when the piece first appears: a late one (scrolled into view) is simply shown.
    val animate = remember { gate?.open() ?: true }
    if (!animate) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(520, easing = StaggerEase))
    }
    return this.graphicsLayer {
        alpha = p.value
        if (fromRight) translationX = (1f - p.value) * 48.mpx.toPx() else translationY = (1f - p.value) * 24.mpx.toPx()
    }
}

/** glcn: [block] drawn into its own layer at [alpha], so a glow and a fill fade as one. */
inline fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAtAlpha(alpha: Float, bounds: androidx.compose.ui.geometry.Rect, block: () -> Unit) {
    if (alpha >= 0.999f) { block(); return }
    val paint = androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha }
    drawContext.canvas.saveLayer(bounds, paint)
    block()
    drawContext.canvas.restore()
}
