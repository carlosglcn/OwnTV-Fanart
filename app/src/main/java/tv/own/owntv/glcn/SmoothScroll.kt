package tv.own.owntv.glcn

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.launch

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
            // Room kept round the focused item, so its ring is never flush with (or cut by) the screen's edge.
            val room = 64f.coerceAtMost((containerSize - size).coerceAtLeast(0f) / 2f)
            val lead = offset - room
            val trailing = offset + size + room - containerSize
            return when {
                lead >= 0f && trailing <= 0f -> 0f
                lead < 0f && trailing > 0f -> 0f
                kotlin.math.abs(lead) < kotlin.math.abs(trailing) -> lead
                else -> trailing
            }
        }
    }

    /** For the shell's CompositionLocalProvider. */
    fun provided(): androidx.compose.runtime.ProvidedValue<*> =
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec
}

/**
 * glcn: the Dock's way of moving, for a grid. One number — the distance still to travel — is eased towards zero a
 * little every frame, and a new key press only adds to it. The speed therefore carries over from one press to the next
 * (a held ▲/▼ flows instead of restarting its animation each step), and the highlight, which follows the focused
 * item, glides with the picture. Compose's own bring-into-view is switched off where this is used ([off]).
 */
class GlideScroller(private val state: androidx.compose.foundation.lazy.grid.LazyGridState, private val scope: kotlinx.coroutines.CoroutineScope) {
    private var pending = 0f
    private var job: kotlinx.coroutines.Job? = null

    /** The focused item [index] is on screen: add whatever scroll it still needs, one row of room kept ahead. */
    fun focused(index: Int) {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val h = item.size.height.toFloat()
        val room = h.coerceAtMost(((info.viewportSize.height - h) / 2f).coerceAtLeast(0f))
        val top = item.offset.y - pending
        val lead = top - room
        val trailing = top + h + room - info.viewportSize.height
        val need = when {
            lead >= 0f && trailing <= 0f -> 0f
            lead < 0f -> lead
            else -> trailing
        }
        if (need != 0f) add(need)
    }

    private fun add(delta: Float) {
        if (LowRam.on) { scope.launch { state.scrollBy(delta) }; return }
        pending += delta
        if (job?.isActive == true) return
        job = scope.launch {
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (kotlin.math.abs(pending) > 0.5f) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                val step = pending * (1f - kotlin.math.exp(-dt / 0.085f))
                val used = state.scrollBy(step)
                pending -= step
                // The list met its end (or a jump moved it): nothing more to travel.
                if (kotlin.math.abs(step) > 1f && kotlin.math.abs(used) < kotlin.math.abs(step) * 0.25f) pending = 0f
            }
            if (pending != 0f) { state.scrollBy(pending); pending = 0f }
        }
    }
}

/** Zero scroll distance: the bring-into-view that [GlideScroller] replaces. */
@OptIn(ExperimentalFoundationApi::class)
val NoBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
