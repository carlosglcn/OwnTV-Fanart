package tv.own.owntv.glcn

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
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
    /**
     * glcn: the page's glide, everywhere — a spring that carries its speed ([FollowSpring]). Compose evaluates the
     * scroll's curve again on every frame from where the page is, handing it the speed it had: a tween ignores that
     * speed, so each new step restarted the glide (a bump) and a run ended in a sudden stop. The spring keeps it: it
     * sets off gently, a new step bends its path instead of restarting it, and it lands softly. Instant in light mode.
     */
    val animation: AnimationSpec<Float>
        get() = if (LowRam.on) snap<Float>() else FollowSpring(GLIDE_OMEGA)

    /** glcn: Home's page, row to row — a softer spring, so the page never sets off with a jolt. */
    val homeAnimation: AnimationSpec<Float>
        get() = if (LowRam.on) snap<Float>() else FollowSpring(HOME_OMEGA)

    /**
     * glcn: a menu's list (Settings) — brisker, so a quick run of ▼ never leaves the highlight off the screen; the
     * highlight slides at the same pace ([SETTINGS_FOCUS_STIFFNESS]).
     */
    val listAnimation: AnimationSpec<Float>
        get() = if (LowRam.on) snap<Float>() else FollowSpring(LIST_OMEGA)

    /**
     * glcn: the channel lists (TV en directo, the Guide) — a spring a touch short of critical damping, so a step
     * lands with a small give (about 3 %) instead of stopping dead; springs carry their speed, so a held ▲/▼ still
     * flows. Instant in light mode.
     */
    val elasticAnimation: AnimationSpec<Float>
        get() = if (LowRam.on) snap<Float>() else androidx.compose.animation.core.spring(dampingRatio = ELASTIC_DAMPING, stiffness = ELASTIC_STIFFNESS)

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
            // glcn: the same spring as the app's lists ([FollowSpring]): the speed carries over from step to step,
            // so the grid sets off gently, flows under a held key and lands softly — no jump at a press, no hard stop.
            var speed = 0f
            while (kotlin.math.abs(pending) > 0.5f || kotlin.math.abs(speed) > 12f) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                // glcn: at the app's pace (Pace), like every Compose animation.
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f) / Pace.factor.coerceIn(0.1f, 1f)
                last = now
                val (left, v) = FollowSpring.step(pending, speed, dt, GLIDE_OMEGA)
                val step = pending - left
                val used = state.scrollBy(step)
                pending = left
                speed = v
                // The list met its end (or a jump moved it): nothing more to travel.
                if (kotlin.math.abs(step) > 1f && kotlin.math.abs(used) < kotlin.math.abs(step) * 0.25f) { pending = 0f; speed = 0f }
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

/** glcn: the springs' pace (their angular frequency, 1/s): a single row's step lands in about 4.7 / ω seconds. */
private const val GLIDE_OMEGA = 13f
private const val HOME_OMEGA = 10f
private const val LIST_OMEGA = 18f
private const val ELASTIC_DAMPING = 0.75f
private const val ELASTIC_STIFFNESS = 220f
/** glcn: the settings highlight's slide, in step with [SmoothScroll.listAnimation]. */
const val SETTINGS_FOCUS_STIFFNESS = 900f

/**
 * glcn: a critically damped spring that follows its target without wobbling, carrying the speed it is handed. Its pace
 * quickens with the distance still to go, so a held key's run of steps never leaves the page far behind its focus
 * (a plain spring fell behind until the focus left the screen), and slows back to [omega] for a soft landing.
 *
 * Compose's bring-into-view asks it for one frame at a time from where the page is, with the speed it had; each frame
 * is solved exactly (the closed form), so the motion stays smooth at any frame rate.
 */
class FollowSpring(private val omega: Float) : androidx.compose.animation.core.FiniteAnimationSpec<Float> {
    override fun <V : androidx.compose.animation.core.AnimationVector> vectorize(
        converter: androidx.compose.animation.core.TwoWayConverter<Float, V>,
    ): androidx.compose.animation.core.VectorizedFiniteAnimationSpec<V> = Vectorized(omega)

    override fun equals(other: Any?) = other is FollowSpring && other.omega == omega
    override fun hashCode() = omega.hashCode()

    private class Vectorized<V : androidx.compose.animation.core.AnimationVector>(private val omega: Float) :
        androidx.compose.animation.core.VectorizedFiniteAnimationSpec<V> {
        private fun V.x() = (this as androidx.compose.animation.core.AnimationVector1D).value

        private fun solve(playTimeNanos: Long, initial: V, target: V, velocity: V): Pair<Float, Float> {
            val (left, v) = step(initial.x() - target.x(), -velocity.x(), playTimeNanos / 1e9f, omega)
            return (target.x() + left) to -v
        }

        // Floats only: the converter's vector is always one-dimensional.
        @Suppress("UNCHECKED_CAST")
        private fun vec(x: Float): V = androidx.compose.animation.core.AnimationVector1D(x) as V

        override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
            vec(solve(playTimeNanos, initialValue, targetValue, initialVelocity).first)

        override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
            vec(solve(playTimeNanos, initialValue, targetValue, initialVelocity).second)

        override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long {
            var left = initialValue.x() - targetValue.x()
            var v = -initialVelocity.x()
            var t = 0f
            while (t < 4f && (kotlin.math.abs(left) > 0.5f || kotlin.math.abs(v) > 12f)) {
                val r = step(left, v, FRAME_S, omega); left = r.first; v = r.second; t += FRAME_S
            }
            return (t * 1e9f).toLong()
        }
    }

    companion object {
        private const val FRAME_S = 1f / 60f
        /** Distance (px) over which the pace doubles. */
        private const val BOOST_PX = 220f

        /**
         * One stretch of [dt] seconds: [left] is the distance still to travel (it falls towards 0), [speed] how fast it
         * falls (px/s). Returns the new distance and speed. Solved in sub-steps of at most a frame, each exactly.
         */
        fun step(left: Float, speed: Float, dt: Float, omega: Float): Pair<Float, Float> {
            var x = left
            var v = -speed // the rate of change of x
            var rest = dt
            while (rest > 0f) {
                val h = kotlin.math.min(rest, FRAME_S)
                val w = omega * (1f + kotlin.math.abs(x) / BOOST_PX)
                val b = v + w * x
                val e = kotlin.math.exp(-w * h)
                val nx = (x + b * h) * e
                val nv = (v - w * b * h) * e
                x = nx; v = nv; rest -= h
            }
            return x to -v
        }
    }
}
