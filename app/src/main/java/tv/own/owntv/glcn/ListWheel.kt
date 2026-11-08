package tv.own.owntv.glcn

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * glcn: true while a wheel turns the list below: the row that still holds focus draws as any other, because
 * the wheel's own highlight ([ListWheel.lensTop]) stands in for it.
 */
val LocalQuietFocus = compositionLocalOf { false }

/**
 * glcn: a held ▲/▼ turns a list like a wheel.
 *
 * The key's own repeats (a step every 50–100 ms, each one a jump of the list and a hop of the focus) are set
 * aside. The list runs on the screen's frames instead, at a speed that builds up gently while the key is held.
 * Nothing else moves: the focus stays where it was, so no row, preview or panel is rebuilt while the list
 * turns — only the rows coming into view are. A highlight drawn behind the list ([lensTop]) stays put while
 * the rows slide through it. Let go and the list rolls on to the row ahead, slows and stops with that row in
 * the highlight, and only then does the focus land there, on a row already in place. At either end, where the
 * list cannot turn any further, the highlight itself glides on to the first or last row.
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

    /** True while the wheel turns; the highlight is drawn and the focused row stays quiet. */
    var turning by mutableStateOf(false)
        private set

    /** The row under the highlight, for the scroll bar's bubble; null when still. */
    var row by mutableStateOf<Int?>(null)
        private set

    /** The highlight's top edge, in the list's own pixels, and its height. */
    var lensTop by mutableFloatStateOf(0f)
        private set
    var lensHeight by mutableFloatStateOf(0f)
        private set

    /** A repeat of a held ▲ (−1) or ▼ (+1) on row [from]. */
    fun hold(direction: Int, from: Int) {
        held = true
        dir = direction
        if (job?.isActive == true) return
        val start = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from } ?: return
        lensTop = (start.offset - state.layoutInfo.viewportStartOffset).toFloat()
        lensHeight = start.size.toFloat()
        row = from
        turning = true
        active = true
        job = scope.launch {
            try { turn(from, start.offset + start.size / 2f) } finally { active = false; turning = false; row = null; held = false }
        }
    }

    /** The key came up: roll on to the next row and stop. */
    fun release() { held = false }

    fun stop() { job?.cancel(); job = null }

    private suspend fun turn(from: Int, startCentre: Float) {
        var lens = startCentre // the highlight's middle, in the list's offsets
        var current = from
        var landed = from
        state.scroll {
            var last = withFrameNanos { it }
            val began = last
            var speed = START_ROWS_PER_S
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
                val rest = want - moved
                if (abs(rest) > 0.5f) {
                    // The end of the list: the rows stand, the highlight glides on — no further than the last row.
                    val edge = state.layoutInfo.visibleItemsInfo.let { v -> if (dir > 0) v.lastOrNull { it.index == count() - 1 } else v.firstOrNull { it.index == 0 } }
                    val stop = edge?.let { it.offset + it.size / 2f }
                    lens += rest
                    if (stop != null && (dir > 0 && lens >= stop || dir < 0 && lens <= stop)) { lens = stop; held = false }
                    lensTop = lens - lensHeight / 2f - state.layoutInfo.viewportStartOffset
                }
                rowAt(lens)?.let { if (it != current) { current = it; row = it } }
            }
            landed = settle(current, lens, speed)
        }
        // The list is still: the focus lands on the row in the highlight, then the highlight steps aside.
        focus(landed)
        withFrameNanos { }
    }

    /** Roll on until the row ahead sits in the highlight, slowing from the wheel's speed to rest. Returns that row. */
    private suspend fun ScrollScope.settle(current: Int, lens: Float, speed: Float): Int {
        val info = state.layoutInfo
        val here = info.visibleItemsInfo.firstOrNull { it.index == current } ?: return current
        var target = current
        var distance = here.offset + here.size / 2f - lens
        // Already past the middle of this row in the direction of travel: the next one, never back.
        if (dir > 0 && distance < 0f || dir < 0 && distance > 0f) {
            val next = current + dir
            info.visibleItemsInfo.firstOrNull { it.index == next }?.let { r ->
                target = next
                distance = r.offset + r.size / 2f - lens
            }
        }
        row = target
        if (abs(distance) < 0.5f) return target
        val pitch = max(pitch(), 1f)
        // About as long as the wheel's own speed would take to come to rest over that distance.
        val seconds = (2f * abs(distance) / max(speed * pitch, 1f)).coerceIn(SETTLE_MIN_S, SETTLE_MAX_S)
        var done = 0f
        var stuck = 0f
        val began = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val p = ((now - began) / 1e9f / seconds).coerceIn(0f, 1f)
            val eased = 1f - (1f - p) * (1f - p) * (1f - p) // ease-out: leaves at the wheel's speed, lands at rest
            val step = distance * eased - done
            val moved = scrollBy(step)
            done += moved
            // At an end the list cannot follow: the highlight goes the rest of the way instead.
            stuck += step - moved
            if (abs(stuck) > 0.5f) lensTop += step - moved
            if (p >= 1f) break
        }
        return target
    }

    /** One row's height and the gap after it. */
    private fun pitch(): Float {
        val info = state.layoutInfo
        val sizes = info.visibleItemsInfo.map { it.size }.sorted()
        if (sizes.isEmpty()) return 0f
        return (sizes[sizes.size / 2] + info.mainAxisItemSpacing).toFloat()
    }

    private fun rowAt(y: Float): Int? {
        val info = state.layoutInfo
        return info.visibleItemsInfo.firstOrNull { y >= it.offset - info.mainAxisItemSpacing / 2f && y < it.offset + it.size + info.mainAxisItemSpacing / 2f }?.index
    }

    companion object {
        /** True while a wheel turns a list: the bring-into-view rule leaves the list where it is. */
        @Volatile var active: Boolean = false
            private set

        private const val START_ROWS_PER_S = 9f   // about the key's own repeat, so the start does not lurch
        private const val TOP_ROWS_PER_S = 24f
        private const val RAMP_DELAY_S = 0.4f     // a short hold stays slow and readable
        private const val RAMP_S = 1.6f
        private const val EASE_S = 0.15f
        private const val SETTLE_MIN_S = 0.16f
        private const val SETTLE_MAX_S = 0.42f

        private fun smooth(x: Float) = x * x * (3f - 2f * x)
    }
}
