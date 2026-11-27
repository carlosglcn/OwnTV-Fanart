package tv.own.owntv.glcn

import androidx.compose.ui.graphics.graphicsLayer
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
import tv.own.owntv.ui.theme.mpx
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
    var stiffness = 950f
    /** glcn: below 1 the box overshoots a touch and settles back — an elastic glide (Home's rows). */
    var damping = 1f
    /** glcn: the box stretches as it goes — its leading edge gets there first, the trailing one follows (menu bars). */
    var stretch = false
    /**
     * glcn: the box keeps its place and the cards pass under it (Home's rows, as on Prime Video) instead of gliding
     * from card to card. While [anchor] is set the box is drawn there; once the row has come to rest, whatever gap is
     * left to the focused card (the row's last cards, where it cannot scroll further) is glided as usual.
     */
    var pinned = false
    var anchor by mutableStateOf<Rect?>(null)
    /** glcn: the box came over from another host: it shows at once (no fade in) while it glides in. */
    var arrived = false
    /**
     * glcn: the focused card's "pop" — it grows a little past its rest size with a small bounce, the box with it
     * (Home's landscape cards, as on a launcher's app tiles). 1 = no pop. Read by the card ([slidePop]).
     */
    val pop = Animatable(1f)
}

val LocalSlideFocus = compositionLocalOf<SlideFocusState?> { null }

/** glcn: rows under a pinned box bring the focused card to their first place (Home); elsewhere they scroll as before. */
val LocalPinnedRows = compositionLocalOf { false }

/**
 * glcn: where the last sliding box on screen was drawn, and when — across all hosts. When focus jumps from one list
 * or menu to another (the top bar to a row, a menu to its page) the new host's box sets off from there, so one box
 * seems to travel between them instead of one going out and another coming on.
 */
object SlideFocusTrail {
    var rect: Rect = Rect.Zero
    var at: Long = 0L
    /** A jump older than this starts in place, as before (a screen just opened, a dialog closed…). */
    const val FRESH_MS = 350L
    fun fresh(): Rect? = rect.takeIf { it != Rect.Zero && android.os.SystemClock.uptimeMillis() - at < FRESH_MS }
}

/** Wraps a list or a menu: draws the sliding box behind [content] and tells its items to leave focus drawing to it. */
@Composable
fun SlideFocusHost(
    modifier: Modifier = Modifier, stiffness: Float = 700f, stretch: Boolean = false, damping: Float = 1f,
    /** glcn: the box stays put and the cards come to it (see [SlideFocusState.pinned]). */
    pinned: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (LowRam.on) { content(); return }
    val st = remember { SlideFocusState() }
    st.pinned = pinned
    st.stiffness = stiffness
    st.stretch = stretch
    st.damping = damping
    val palette = LocalStagePalette.current
    val on = st.current != null
    val fade = ownTvTween<Float>(140)
    LaunchedEffect(on) {
        if (on && st.arrived) { st.arrived = false; st.alpha.snapTo(1f) } else st.alpha.animateTo(if (on) 1f else 0f, fade)
    }
    Box(modifier) {
        Canvas(Modifier.matchParentSize().onGloballyPositioned { st.origin = it.positionInWindow() }) {
            val a = st.alpha.value
            val accent = st.accent
            val live = st.live
            if (a <= 0.01f || accent == null || live == Rect.Zero) return@Canvas
            val g = st.glide.value
            val held = st.anchor
            var l = (held?.left ?: (live.left + st.gap.left * g)) - st.origin.x
            var t = (held?.top ?: (live.top + st.gap.top * g)) - st.origin.y
            var w = held?.width ?: (live.width + st.gap.width * g)
            var h = held?.height ?: (live.height + st.gap.height * g)
            if (held == null && st.stretch && g > 0f) {
                // The edge it travels towards arrives first (g²), the other trails (g^0.7): a drop that stretches.
                val lead = g * g
                val trail = Math.pow(g.toDouble(), 0.7).toFloat()
                val right = st.gap.left < 0f
                val down = st.gap.top < 0f
                val left = live.left + st.gap.left * (if (right) trail else lead)
                val rightEdge = live.right + st.gap.right * (if (right) lead else trail)
                val top = live.top + st.gap.top * (if (down) trail else lead)
                val bottom = live.bottom + st.gap.bottom * (if (down) lead else trail)
                l = left - st.origin.x; t = top - st.origin.y; w = rightEdge - left; h = bottom - top
            }
            val p = st.pop.value
            if (p != 1f) { l -= w * (p - 1f) / 2f; t -= h * (p - 1f) / 2f; w *= p; h *= p }
            st.drawn = Rect(l + st.origin.x, t + st.origin.y, l + st.origin.x + w, t + st.origin.y + h)
            if (a > 0.5f && st.current != null) { SlideFocusTrail.rect = st.drawn; SlideFocusTrail.at = android.os.SystemClock.uptimeMillis() }
            inset(l, t, size.width - l - w, size.height - t - h) {
                drawAtAlpha(a, Rect(-SLIDE_GLOW_ROOM, -SLIDE_GLOW_ROOM, size.width + SLIDE_GLOW_ROOM, size.height + SLIDE_GLOW_ROOM)) {
                    drawStageFocus(st.style, st.radius, accent, palette)
                }
            }
        }
        CompositionLocalProvider(LocalSlideFocus provides st, LocalPinnedRows provides pinned) { content() }
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
fun Modifier.slideFocusReport(st: SlideFocusState, style: StageFocus, radius: Dp, accent: StageAccent, region: SlideRegion? = null, pop: Float = 1f): Modifier {
    val id = remember { Any() }
    var rect by remember { mutableStateOf(Rect.Zero) }
    val anim = tv.own.owntv.ui.theme.animationsOn
    val scope = rememberCoroutineScope()
    val crossMax = with(androidx.compose.ui.platform.LocalDensity.current) { CROSS_MAX_MPX.mpx.toPx() }
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
                // glcn: coming from another host's box (just drawn somewhere else on screen): set off from there.
                // Only from nearby: across half the screen (Home's "Watch now" up to the top bar) the box was seen
                // sweeping over the picture — there it simply comes on where focus lands.
                val crossing = (if (!visible && !LowRam.on && anim) SlideFocusTrail.fresh() else null)
                    ?.takeIf { rect == Rect.Zero || (it.center - rect.center).getDistance() <= crossMax }
                if (crossing != null) st.arrived = true
                val from = crossing ?: st.drawn
                val visible2 = visible || crossing != null
                st.gap = if (visible2 && from != Rect.Zero && rect != Rect.Zero)
                    Rect(from.left - rect.left, from.top - rect.top, from.left - rect.left + (from.width - rect.width), from.top - rect.top + (from.height - rect.height))
                else Rect.Zero
                st.style = style; st.radius = radius; st.accent = accent
                st.current = id
                if (rect != Rect.Zero) st.live = rect
                // glcn: a pinned box, between cards of one size: it waits where it is while the row brings the card
                // under it, and only then glides whatever is left.
                val hold = st.pinned && anim && crossing == null && visible && from != Rect.Zero && rect != Rect.Zero &&
                    kotlin.math.abs(from.width - rect.width) <= rect.width * PIN_SAME_SIZE &&
                    kotlin.math.abs(from.height - rect.height) <= rect.height * PIN_SAME_SIZE &&
                    // …along one row: between rows the box glides down or up at once, as it always did.
                    kotlin.math.abs(from.top - rect.top) <= rect.height * PIN_SAME_ROW
                st.anchor = if (hold) from else null
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    st.glide.snapTo(1f)
                    if (hold) {
                        var last = st.live
                        var still = 0
                        val began = android.os.SystemClock.uptimeMillis()
                        while (st.current === id && st.anchor != null) {
                            androidx.compose.runtime.withFrameNanos { }
                            val now = st.live
                            still = if (now == last) still + 1 else 0
                            last = now
                            val waited = android.os.SystemClock.uptimeMillis() - began
                            if ((still >= PIN_STILL_FRAMES && waited >= PIN_MIN_MS) || waited >= PIN_MAX_MS) break
                        }
                        if (st.current !== id) return@launch
                        val at = st.live
                        st.gap = Rect(from.left - at.left, from.top - at.top, from.left - at.left + (from.width - at.width), from.top - at.top + (from.height - at.height))
                        st.anchor = null
                    }
                    // glcn: Animations Off — the box is simply there; Subtle — it slides and stops, without give.
                    if (anim) st.glide.animateTo(0f, spring(dampingRatio = if (tv.own.owntv.ui.stage.GlcnMotion.subtle) 1f else st.damping, stiffness = st.stiffness))
                    else st.glide.snapTo(0f)
                }
                // glcn: the card's pop — from rest, a little past [pop] and back (none with animations off).
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    st.pop.snapTo(1f)
                    if (pop != 1f && anim) st.pop.animateTo(pop, spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS))
                }
            } else if (st.current === id) { st.current = null; st.anchor = null }
        }
        .then(
            if (region != null) Modifier else Modifier.onGloballyPositioned {
                rect = it.boundsInWindow()
                if (st.current === id) st.live = rect
            },
        )
}

/** glcn: the focused card grows with the box ([SlideFocusState.pop]); put on the part the box wraps. */
fun Modifier.slidePop(st: SlideFocusState?, focused: Boolean): Modifier =
    if (st == null) this else graphicsLayer {
        val p = if (focused) st.pop.value else 1f
        scaleX = p; scaleY = p
    }

/** glcn: the channel lists' highlight (TV en directo): brisk, clearly springy — it passes the row by about an eighth and settles back, like a launcher's tiles. */
const val CHANNEL_FOCUS_STIFFNESS = 760f
const val CHANNEL_FOCUS_DAMPING = 0.76f

/** glcn: a launcher-like pop — a small overshoot (about 6 % past the step), settled in a quarter of a second. */
/** glcn: the farthest (in design px) a box travels from one list or menu to another; beyond it, no journey. */
private const val CROSS_MAX_MPX = 300
/** glcn: a pinned box holds only between cards of (nearly) one size, for at least / at most this long. */
private const val PIN_SAME_SIZE = 0.08f
private const val PIN_SAME_ROW = 0.25f
private const val PIN_STILL_FRAMES = 3
private const val PIN_MIN_MS = 90L
private const val PIN_MAX_MS = 700L
private const val POP_DAMPING = 0.55f
private const val POP_STIFFNESS = 420f

/** glcn: room round the sliding box for its glow — at 80 px a wide glow (an FX row's) was cut off in a hard line. */
private const val SLIDE_GLOW_ROOM = 180f
