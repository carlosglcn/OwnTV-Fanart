package tv.own.owntv.glcn

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * glcn: how every row, grid and list glides when the focus moves to something off-screen — one longer, eased
 * slide instead of the quick snap the TV default gave, which read as a bump on every step. Light mode keeps it
 * instant. Used app-wide from the shell, and by the screens that set their own distance rule.
 */
@OptIn(ExperimentalFoundationApi::class)
object SmoothScroll {
    val animation: AnimationSpec<Float>
        get() = if (LowRam.on) snap<Float>() else tween<Float>(420, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))

    /** The app-wide default: scroll only as far as the focused item needs, eased. */
    val spec = object : BringIntoViewSpec {
        override val scrollAnimationSpec: AnimationSpec<Float> get() = animation
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
            val trailing = offset + size - containerSize
            return when {
                offset >= 0f && trailing <= 0f -> 0f
                offset < 0f && trailing > 0f -> 0f
                kotlin.math.abs(offset) < kotlin.math.abs(trailing) -> offset
                else -> trailing
            }
        }
    }

    /** For the shell's CompositionLocalProvider. */
    fun provided(): androidx.compose.runtime.ProvidedValue<*> =
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec
}
