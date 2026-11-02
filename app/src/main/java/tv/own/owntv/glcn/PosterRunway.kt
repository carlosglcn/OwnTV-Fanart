package tv.own.owntv.glcn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.floor

/**
 * glcn: "Pasarela" — one row of posters where the focused one opens wide and the rest, the same height, glide
 * sideways, as the Home Dock does. ONE number ([pos]) drives the row: which poster is open and how far the row has
 * glided. A spring re-aimed on every key press keeps its speed, so a held ◀/▶ flows. Only the posters around the
 * focus are composed; [onWindow] says which, so the screen can ask for their artwork.
 *
 * [item] draws poster [index] at the width the row gives it; [wideness] is 0 (closed) to 1 (open), read inside draw
 * code so the row never recomposes while it glides.
 *
 * CH+/CH− (and Page Up/Down) jump a screenful; the posters a held ◀ is heading for are composed ahead of it, so
 * focus never runs out of neighbours and escapes to the menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterRunway(
    count: Int,
    focusedAt: Int,
    narrow: Dp,
    wide: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    onWindow: (IntRange) -> Unit = {},
    item: @Composable (index: Int, wideness: () -> Float) -> Unit,
) {
    val pos = remember { Animatable(focusedAt.toFloat()) }
    LaunchedEffect(focusedAt) {
        val to = focusedAt.toFloat()
        if (LowRam.on || abs(pos.value - to) > 6f) pos.snapTo(to)
        else pos.animateTo(to, spring(dampingRatio = 1f, stiffness = 140f))
    }
    if (count <= 0) return
    // Only the whole poster under the glide matters to composition: a derived value, so the row does not
    // recompose on every frame of the spring.
    val k by remember { derivedStateOf { floor(pos.value).toInt() } }
    var jumpTo by remember { mutableIntStateOf(-1) }
    val requesters = remember { HashMap<Int, FocusRequester>() }
    val lo = if (jumpTo >= 0) minOf(k, focusedAt, jumpTo) else minOf(k, focusedAt)
    val hi = if (jumpTo >= 0) maxOf(k, focusedAt, jumpTo) else maxOf(k, focusedAt)
    val from = (lo - BEHIND).coerceIn(0, count - 1)
    val to = (hi + AHEAD).coerceAtMost(count - 1)
    SideEffect { onWindow(from..to) }
    LaunchedEffect(jumpTo) {
        if (jumpTo < 0) return@LaunchedEffect
        withFrameNanos { }
        runCatching { requesters[jumpTo]?.requestFocus() }
        jumpTo = -1
    }
    fun wideness(i: Int, t: Float) = (1f - abs(i - t)).coerceIn(0f, 1f)
    // The row positions everything itself; Compose's own scroll-into-view must not fight it.
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoViewSpec) {
        Layout(
            modifier = modifier
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val step = when (e.key) {
                        Key.ChannelUp, Key.PageUp -> -PAGE
                        Key.ChannelDown, Key.PageDown -> PAGE
                        else -> return@onPreviewKeyEvent false
                    }
                    val target = (focusedAt + step).coerceIn(0, count - 1)
                    if (target != focusedAt) jumpTo = target
                    true
                }
                .focusGroup(),
            content = {
                for (i in from..to) key(i) {
                    val r = remember { FocusRequester() }
                    DisposableEffect(r) {
                        requesters[i] = r
                        onDispose { if (requesters[i] === r) requesters.remove(i) }
                    }
                    Box(Modifier.focusRequester(r), propagateMinConstraints = true) { item(i) { wideness(i, pos.value) } }
                }
            },
        ) { measurables, constraints ->
            val t = pos.value
            val kk = floor(t).toInt()
            val frac = t - kk
            val gapPx = gap.roundToPx()
            val n = narrow.roundToPx()
            val w = wide.roundToPx()
            val widths = IntArray(measurables.size) { j -> n + ((w - n) * wideness(from + j, t)).toInt() }
            val left = IntArray(measurables.size)
            for (j in 1 until measurables.size) left[j] = left[j - 1] + widths[j - 1] + gapPx
            fun at(i: Int) = left[(i - from).coerceIn(0, measurables.size - 1)]
            val scroll = at(kk) + (at(kk + 1) - at(kk)) * frac
            val placeables = measurables.mapIndexed { j, m ->
                m.measure(Constraints(minWidth = widths[j], maxWidth = widths[j], minHeight = 0, maxHeight = Constraints.Infinity))
            }
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else w * 4
            layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
                placeables.forEachIndexed { j, p ->
                    val x = (left[j] - scroll).toInt()
                    // A poster that has glided past the left edge dissolves away instead of being cut.
                    val a = if (x >= 0) 1f else (1f + x / (0.7f * widths[j])).coerceIn(0f, 1f)
                    p.placeWithLayer(x, 0, zIndex = if (from + j == focusedAt) 1f else 0f) { alpha = a }
                }
            }
        }
    }
}

/** Posters kept composed behind and ahead of the focus, and how far CH± jumps. */
private const val BEHIND = 4
private const val AHEAD = 10
private const val PAGE = 6
