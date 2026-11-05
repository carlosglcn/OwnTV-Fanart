package tv.own.owntv.glcn

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: a slim scroll bar at a long list's right edge — there only while the list moves, fading out once it rests —
 * with a bubble beside the thumb naming where you are ([label], the focused channel's number) on a long run.
 */
@Composable
fun Modifier.fastScrollbar(state: LazyListState, count: Int, endInset: Dp, bottomInset: Dp, resting: () -> Float = { 0f }, held: () -> Boolean = { false }, label: () -> String?): Modifier {
    val alpha = remember { Animatable(0f) }
    val bubble = remember { Animatable(0f) }
    val accent = stageAccent.accent
    val palette = LocalStagePalette.current
    val ink = if (palette.isLight) Color.Black else Color.White
    val measurer = rememberTextMeasurer()
    val style = stageText(15, 800)
    // One watcher: each move shows the bar (and, on a run of moves, the bubble); only a pause of [REST_MS] hides it —
    // collectLatest drops the pending hide whenever the list moves again, so it no longer blinks on a long run.
    LaunchedEffect(state) {
        var moves = 0
        var lastAt = 0L
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collectLatest {
            val now = android.os.SystemClock.uptimeMillis()
            moves = if (now - lastAt < RUN_GAP_MS) moves + 1 else 0
            lastAt = now
            if (alpha.value < 1f) alpha.animateTo(1f, androidx.compose.animation.core.tween(160))
            if (moves >= RUN_STEPS && bubble.value < 1f) bubble.animateTo(1f, androidx.compose.animation.core.tween(140))
            delay(REST_MS)
            bubble.animateTo(0f, androidx.compose.animation.core.tween(300))
            alpha.animateTo(0f, androidx.compose.animation.core.tween(500))
        }
    }
    return drawWithContent {
        drawContent()
        // glcn: a quiet bar stays while the list holds focus (it was easy to miss); moving brings it up full.
        val hold = held()
        val a = if (hold) 1f else maxOf(alpha.value, resting())
        val visible = state.layoutInfo.visibleItemsInfo.size
        if (a <= 0.01f || count <= visible * 2 || visible == 0) return@drawWithContent
        val px = 1.mpx.toPx()
        // Held: wider, ringed, the bubble always up — it is the thing being moved.
        val w = (if (hold) 10f else 6f) * px
        val x = size.width - endInset.toPx() + (if (hold) 4f else 6f) * px
        val top = 4f * px
        val track = size.height - bottomInset.toPx() - top
        val thumbH = (track * visible / count).coerceAtLeast(44f * px)
        val maxFirst = (count - visible).coerceAtLeast(1)
        val y = top + (track - thumbH) * (state.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
        drawRoundRect(ink.copy(alpha = 0.16f * a), Offset(x, top), Size(w, track), CornerRadius(w / 2f))
        drawRoundRect(accent.copy(alpha = a), Offset(x, y), Size(w, thumbH), CornerRadius(w / 2f))
        if (hold) drawRoundRect(ink.copy(alpha = 0.55f), Offset(x - 2f * px, y - 2f * px), Size(w + 4f * px, thumbH + 4f * px), CornerRadius(w / 2f + 2f * px), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * px))
        val b = if (hold) 1f else bubble.value * a
        val text = label()
        if (b > 0.01f && text != null) {
            val m = measurer.measure(text, style.copy(color = Color.White))
            val bw = m.size.width + 22f * px
            val bh = m.size.height + 10f * px
            val bx = x - 12f * px - bw
            val by = (y + thumbH / 2f - bh / 2f).coerceIn(0f, size.height - bh)
            drawRoundRect(accent.copy(alpha = b), Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2f))
            drawText(m, topLeft = Offset(bx + 11f * px, by + 5f * px), alpha = b)
        }
    }
}

private const val RUN_GAP_MS = 450L
private const val RUN_STEPS = 3
private const val REST_MS = 1_100L
