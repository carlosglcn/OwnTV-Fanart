package tv.own.owntv.glcn

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import tv.own.owntv.ui.theme.mpx

/**
 * glcn: a row of buttons that scrolls when it does not fit, with room round it for the focused button's
 * ring and glow — a scroll clips at its bounds, which cut the ring and left the glow as a hard-edged
 * box. The room bleeds out past the row's own place, so the layout around it does not move.
 */
@Composable
fun Modifier.glowRoomScroll(h: Dp = 30.mpx, v: Dp = 34.mpx): Modifier =
    this.layout { measurable, constraints ->
        val hp = h.roundToPx()
        val vp = v.roundToPx()
        val inner = constraints.copy(
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * hp else constraints.maxWidth,
            minWidth = if (constraints.minWidth > 0) constraints.minWidth + 2 * hp else 0,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + 2 * vp else constraints.maxHeight,
            minHeight = if (constraints.minHeight > 0) constraints.minHeight + 2 * vp else 0,
        )
        val p = measurable.measure(inner)
        layout((p.width - 2 * hp).coerceAtLeast(0), (p.height - 2 * vp).coerceAtLeast(0)) { p.place(-hp, -vp) }
    }.horizontalScroll(rememberScrollState()).padding(horizontal = h, vertical = v)
