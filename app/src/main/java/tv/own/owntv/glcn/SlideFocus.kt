package tv.own.owntv.glcn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.drawStageFocus
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.StageAccent
import tv.own.owntv.ui.theme.ownTvTween
import tv.own.owntv.ui.theme.stageAccent

/**
 * glcn: one focus box for a whole list or menu. The items draw nothing when they take focus; they only say where
 * they are, and the box — drawn once behind them — glides from the item that lost focus to the one that gained it.
 * It follows the focused item exactly while the list scrolls under it: the glide is only the gap left at the
 * moment focus moved, shrinking to nothing.
 */
class SlideFocusState {
    /** The item holding focus now; null while focus is elsewhere. */
    var current by mutableStateOf<Any?>(null)
    var style: StageFocus = StageFocus.FILLED
    var radius: Dp = 12.dp
    var accent: StageAccent? = null
    /** The focused item's bounds in the window, kept up to date by that item. */
    var live by mutableStateOf(Rect.Zero)
    /** Where the box was drawn last (window coordinates) and the gap it starts from, shrinking with [glide] 1 → 0. */
    var drawn = Rect.Zero
    var gap = Rect.Zero
    val glide = Animatable(0f)
    var origin = Offset.Zero
    val alpha = Animatable(0f)
    /** How briskly the box glides (spring stiffness): menus snap, Home's rows take their time. */
    var stiffness = 700f
}

val LocalSlideFocus = compositionLocalOf<SlideFocusState?> { null }

/** Wraps a list or a menu: draws the sliding box behind [content] and tells its items to leave focus drawing to it. */
@Composable
fun SlideFocusHost(modifier: Modifier = Modifier, stiffness: Float = 700f, content: @Composable () -> Unit) {
    if (LowRam.on) { content(); return }
    val st = remember { SlideFocusState() }
    st.stiffness = stiffness
    val palette = LocalStagePalette.current
    val on = st.current != null
    val fade = ownTvTween<Float>(140)
    LaunchedEffect(on) { st.alpha.animateTo(if (on) 1f else 0f, fade) }
    Box(modifier) {
        Canvas(Modifier.matchParentSize().onGloballyPositioned { st.origin = it.positionInWindow() }) {
            val a = st.alpha.value
            val accent = st.accent
            val live = st.live
            if (a <= 0.01f || accent == null || live == Rect.Zero) return@Canvas
            val g = st.glide.value
            val l = live.left + st.gap.left * g - st.origin.x
            val t = live.top + st.gap.top * g - st.origin.y
            val w = live.width + st.gap.width * g
            val h = live.height + st.gap.height * g
            st.drawn = Rect(l + st.origin.x, t + st.origin.y, l + st.origin.x + w, t + st.origin.y + h)
            inset(l, t, size.width - l - w, size.height - t - h) {
                drawAtAlpha(a, Rect(-80f, -80f, size.width + 80f, size.height + 80f)) {
                    drawStageFocus(st.style, st.radius, accent, palette)
                }
            }
        }
        CompositionLocalProvider(LocalSlideFocus provides st) { content() }
    }
}

/** Where the box goes when it is not the whole focusable — a poster's art, not its title — set by [slideRegion]. */
class SlideRegion {
    var rect: Rect = Rect.Zero
    internal var onChange: ((Rect) -> Unit)? = null
}

/** Marks the part of an item the sliding box wraps. */
fun Modifier.slideRegion(region: SlideRegion): Modifier = onGloballyPositioned {
    val r = it.boundsInWindow()
    region.rect = r
    region.onChange?.invoke(r)
}

/** An item's side of it: reports focus and position to the host. */
@Composable
fun Modifier.slideFocusReport(st: SlideFocusState, style: StageFocus, radius: Dp, accent: StageAccent, region: SlideRegion? = null): Modifier {
    val id = remember { Any() }
    var rect by remember { mutableStateOf(Rect.Zero) }
    val scope = rememberCoroutineScope()
    if (region != null) {
        androidx.compose.runtime.SideEffect {
            if (rect == Rect.Zero) rect = region.rect
            region.onChange = { r -> rect = r; if (st.current === id) st.live = r }
        }
    }
    return this
        .onFocusChanged {
            if (it.isFocused) {
                // The gap from where the box was drawn to this item, so it starts where it is and glides in.
                val visible = st.current != null || st.alpha.value > 0.05f
                val from = st.drawn
                st.gap = if (visible && from != Rect.Zero && rect != Rect.Zero)
                    Rect(from.left - rect.left, from.top - rect.top, from.left - rect.left + (from.width - rect.width), from.top - rect.top + (from.height - rect.height))
                else Rect.Zero
                st.style = style; st.radius = radius; st.accent = accent
                st.current = id
                if (rect != Rect.Zero) st.live = rect
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    st.glide.snapTo(1f)
                    st.glide.animateTo(0f, spring(dampingRatio = 1f, stiffness = st.stiffness))
                }
            } else if (st.current === id) st.current = null
        }
        .then(
            if (region != null) Modifier else Modifier.onGloballyPositioned {
                rect = it.boundsInWindow()
                if (st.current === id) st.live = rect
            },
        )
}
