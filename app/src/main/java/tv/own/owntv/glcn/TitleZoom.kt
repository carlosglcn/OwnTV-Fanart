package tv.own.owntv.glcn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import tv.own.owntv.ui.theme.animationsOn

/**
 * glcn: a title chosen on Home grows into its page — the page starts at [FROM] of its size and fades up, as if the
 * card had opened out — instead of arriving like any other section. [arm] on the click, [take] where the page
 * comes in (once: a later section change rises as usual).
 */
object TitleZoom {
    const val FROM = 0.9f
    const val MS = 300

    @Volatile private var armed = false

    fun arm() { armed = true }

    fun take(): Boolean = armed.also { armed = false; if (it) sectionAt = android.os.SystemClock.uptimeMillis() }

    /** When a section last came in zooming (a title opened from Home), so a page inside it does not zoom twice. */
    @Volatile var sectionAt = 0L
}

/**
 * glcn: a title's page opened from a grid (Films, Series, the Runway) grows into place the same way, inside its
 * section. Not when the section itself is already zooming in (opened from Home).
 */
@Composable
fun TitleZoomBox(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, content: @Composable () -> Unit) {
    val on = animationsOn && !LowRam.on &&
        android.os.SystemClock.uptimeMillis() - TitleZoom.sectionAt > SECTION_ZOOM_GRACE_MS
    val k = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(Unit) { if (on) k.animateTo(1f, tween(TitleZoom.MS, easing = FastOutSlowInEasing)) }
    androidx.compose.foundation.layout.Box(
        modifier.then(androidx.compose.ui.Modifier.graphicsLayer {
            val v = k.value
            alpha = v
            val z = TitleZoom.FROM + (1f - TitleZoom.FROM) * v
            scaleX = z; scaleY = z
        }),
    ) { content() }
}

private const val SECTION_ZOOM_GRACE_MS = 700L

/** The page's growth, 0 → 1 once, when it comes in (1 at once with animations off or in light mode). */
@Composable
fun rememberTitleZoom(): Animatable<Float, AnimationVector1D> {
    val on = animationsOn && !LowRam.on
    val k = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(Unit) {
        TitleZoom.take()
        if (on) k.animateTo(1f, tween(TitleZoom.MS, easing = FastOutSlowInEasing))
    }
    return k
}
