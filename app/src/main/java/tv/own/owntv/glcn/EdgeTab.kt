package tv.own.owntv.glcn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent

/** glcn: the room a list keeps at its edge for an [EdgeTab]. */
val EDGE_TAB_ROOM = 16.mpx

/**
 * glcn: a slim tab on a panel's edge with a ‹ ([toRight] false: on the left edge, ◀ opens what is behind it) or a ›
 * (on the right edge, ▶ goes on). Each time the panel takes the cursor ([visible]) it nudges twice towards its arrow,
 * so the eye goes there; then it stays still. Used by every channel list and its categories.
 */
@Composable
fun EdgeTab(visible: Boolean, modifier: Modifier, toRight: Boolean = false) {
    val nudge = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        delay(TAB_NUDGE_DELAY_MS)
        repeat(2) {
            nudge.animateTo(if (toRight) 1f else -1f, tween(180, easing = FastOutSlowInEasing))
            nudge.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 500f))
        }
    }
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(160), label = "edgeTab")
    Box(
        modifier
            .graphicsLayer { alpha = a; translationX = nudge.value * 6.mpx.toPx() }
            .width(EDGE_TAB_ROOM - 2.mpx)
            .height(110.mpx)
            .clip(if (toRight) RoundedCornerShape(topStart = 10.mpx, bottomStart = 10.mpx) else RoundedCornerShape(topEnd = 10.mpx, bottomEnd = 10.mpx))
            .background(stageAccent.accent.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        OwnTVIcon(if (toRight) OwnTVIcon.CHEVRON else OwnTVIcon.CHEVRON_LEFT, StageColors.Text, Modifier.size(14.mpx))
    }
}

private const val TAB_NUDGE_DELAY_MS = 600L
