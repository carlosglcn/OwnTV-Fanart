package tv.own.owntv.glcn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.launch
import tv.own.owntv.ui.stage.GlcnMotion
import tv.own.owntv.ui.theme.mpx

/**
 * glcn: a list's give at its ends. The lists glide and stop without bouncing; only a ▲ on the first row or a ▼ on the
 * last — a press that leads nowhere — makes the list yield a little that way and come back: "there is no more".
 *
 * It sees the ▲/▼ its rows left alone, makes the move itself and, when there was nowhere to go, gives. Nothing with
 * animations Off, the Subtle style or light mode, and nothing while a key is held (one give per press).
 */
fun Modifier.edgeGive(): Modifier = composed {
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val shift = remember { Animatable(0f) }
    val reach = with(LocalDensity.current) { EDGE_GIVE.mpx.toPx() }
    this
        .onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            val dir = when (e.key) {
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionDown -> FocusDirection.Down
                else -> return@onKeyEvent false
            }
            if (focus.moveFocus(dir)) return@onKeyEvent true
            if (e.nativeKeyEvent.repeatCount == 0 && !LowRam.on && !GlcnMotion.calm) scope.launch {
                shift.snapTo(0f)
                shift.animateTo(if (dir == FocusDirection.Down) -reach else reach, spring(dampingRatio = 1f, stiffness = EDGE_OUT_STIFFNESS))
                shift.animateTo(0f, spring(dampingRatio = EDGE_BACK_DAMPING, stiffness = EDGE_BACK_STIFFNESS))
            }
            false
        }
        .graphicsLayer { translationY = shift.value }
}

private const val EDGE_GIVE = 14
private const val EDGE_OUT_STIFFNESS = 1400f
private const val EDGE_BACK_DAMPING = 0.7f
private const val EDGE_BACK_STIFFNESS = 380f
