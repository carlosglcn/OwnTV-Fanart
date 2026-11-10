package tv.own.owntv.glcn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

    fun take(): Boolean = armed.also { armed = false }
}

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
