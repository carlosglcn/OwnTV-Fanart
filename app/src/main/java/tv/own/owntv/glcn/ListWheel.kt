package tv.own.owntv.glcn

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * glcn: a held ▲/▼ turns a list like a wheel. The key's own repeats (a step every 50–100 ms, each one a
 * jump) are set aside: the list runs on the screen's frames instead, at a speed that builds up gently
 * while the key is held, and the highlight stays where it was while the rows slide under it. Let go and
 * it rolls on to the next row and settles there, eased, never back. At either end of the list, where it
 * cannot turn any further, the highlight itself runs on to the first or last row.
 *
 * While it turns, [active] tells the list's bring-into-view rule to stay still: the wheel moves the list,
 * and focus only follows the row under the highlight.
 */
class ListWheel(
    private val state: LazyListState,
    private val scope: CoroutineScope,
    /** How many rows the list has. */
    private val count: () -> Int,
    /** Put the focus on row [index] (it is on screen). */
    private val focus: (index: Int) -> Unit,
) {
    private var job: Job? = null
    private var held = false
    private var dir = 0

    /** The row the wheel is on, for the scroll bar's bubble; null when it is still. */
    var row: Int? = null
        private set

    val turning: Boolean get() = job?.isActive == true

    /** A repeat of a held ▲ (−1) or ▼ (+1) on row [from]. */
    fun hold(direction: Int, from: Int) {
        held = true
        if (turning) { dir = direction; return }
        dir = direction
        row = from
        job = scope.launch { runCatching { turn(from) } ; active = false; row = null; held = false }
    }

    /** The key came up: roll on to the next row and stop. */
    fun release() { held = false }

    fun stop() { job?.cancel(); job = null; active = false; row = null; held = false }

    private suspend fun turn(from: Int) {
        val start = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from } ?: return
        // The highlight's line on screen: the middle of the row the key was held on.
        val lens = start.offset + start.size / 2
        active = true
        state.scroll {
            var current = from
            var last = withFrameNanos { it }
            val began = last
            var speed = START_ROWS_PER_S // rows per second, eased towards the target
            var spare = 0f               // rows owed to the highlight where the list cannot move
            while (held) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                val t = (now - began) / 1e9f
                val target = START_ROWS_PER_S + (TOP_ROWS_PER_S - START_ROWS_PER_S) * smooth(((t - RAMP_DELAY_S) / RAMP_S).coerceIn(0f, 1f))
                speed += (target - speed) * (1f - exp(-dt / EASE_S))
                val pitch = pitch()
                if (pitch <= 0f) break
                val want = dir * speed * pitch * dt
                val moved = scrollBy(want)
                if (abs(moved) < abs(want) * 0.5f) {
                    // The end of the list: the rows stand, the highlight runs on.
                    spare += speed * dt
                    while (spare >= 1f) {
                        spare -= 1f
                        val next = current + dir
                        if (next !in 0 until count()) { held = false; break }
                        current = next; row = next; focus(next)
                    }
                    continue
                }
                val under = rowAt(lens) ?: continue
                if (under != current) { current = under; row = under; focus(under) }
            }
            settle(current, lens, speed)
        }
    }

    /** Roll on until the row ahead sits on the highlight's line, slowing from the wheel's speed to rest. */
    private suspend fun ScrollScope.settle(current: Int, lens: Int, speed: Float) {
        val info = state.layoutInfo
        val here = info.visibleItemsInfo.firstOrNull { it.index == current } ?: return
        val centre = here.offset + here.size / 2
        var target = current
        var distance = (centre - lens).toFloat()
        // Already past the middle of this row in the direction of travel: the next one, never back.
        if (dir > 0 && distance < 0f || dir < 0 && distance > 0f) {
            val next = (current + dir).coerceIn(0, count() - 1)
            info.visibleItemsInfo.firstOrNull { it.index == next }?.let { r ->
                target = next
                distance = (r.offset + r.size / 2 - lens).toFloat()
            }
        }
        if (target != current) { row = target; focus(target) }
        if (abs(distance) < 0.5f) return
        val pitch = max(pitch(), 1f)
        // As long as the wheel's own speed would take to stop over that distance, within reason.
        val seconds = (2f * abs(distance) / max(speed * pitch, 1f)).coerceIn(SETTLE_MIN_S, SETTLE_MAX_S)
        var done = 0f
        var last = withFrameNanos { it }
        val began = last
        while (true) {
            val now = withFrameNanos { it }
            last = now
            val p = ((now - began) / 1e9f / seconds).coerceIn(0f, 1f)
            val eased = 1f - (1f - p) * (1f - p) * (1f - p) // ease-out cubic: keeps the wheel's speed at the start
            val step = distance * eased - done
            done += scrollBy(step)
            if (p >= 1f) break
        }
    }

    /** One row's height and the gap after it — the common row, not the taller focused one. */
    private fun pitch(): Float {
        val info = state.layoutInfo
        val sizes = info.visibleItemsInfo.map { it.size }.sorted()
        if (sizes.isEmpty()) return 0f
        return (sizes[sizes.size / 2] + info.mainAxisItemSpacing).toFloat()
    }

    private fun rowAt(y: Int): Int? = state.layoutInfo.visibleItemsInfo
        .firstOrNull { y >= it.offset && y < it.offset + it.size + state.layoutInfo.mainAxisItemSpacing }?.index

    companion object {
        /** True while a wheel turns a list: the bring-into-view rule leaves the list where it is. */
        @Volatile var active: Boolean = false
            private set

        private const val START_ROWS_PER_S = 9f   // about the key's own repeat, so the start does not jump
        private const val TOP_ROWS_PER_S = 26f
        private const val RAMP_DELAY_S = 0.35f    // a short hold stays slow and readable
        private const val RAMP_S = 1.4f
        private const val EASE_S = 0.12f
        private const val SETTLE_MIN_S = 0.14f
        private const val SETTLE_MAX_S = 0.38f

        private fun smooth(x: Float) = x * x * (3f - 2f * x)
    }
}
