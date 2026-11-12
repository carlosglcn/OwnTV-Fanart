package tv.own.owntv.glcn

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: how every channel list marks the channel being watched — the same everywhere: a soft accent wash over its
 * row with small moving bars at its left edge, inside the row ([playingMark]), and, when it is scrolled out of sight, a
 * "▲ Cuatro · watching now" pill at the edge it went past ([WatchingPill]).
 */
object PlayingMark {
    const val WASH = 0.16f
    const val EQ_CYCLE_MS = 1_100
}

/** Three bars rising and falling, out of step — "this one is playing". */
@Composable
fun EqualizerBars(color: Color, modifier: Modifier) {
    val phase by eqPhase()
    Canvas(modifier) { drawBars(color, phase, Offset.Zero, size) }
}

@Composable
private fun eqPhase() = rememberInfiniteTransition(label = "eq").animateFloat(
    0f, 1f, infiniteRepeatable(tween(PlayingMark.EQ_CYCLE_MS, easing = LinearEasing)), label = "eqPhase",
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBars(color: Color, phase: Float, at: Offset, box: Size) {
    val gap = box.width * 0.14f
    val w = (box.width - gap * 2f) / 3f
    for (b in 0 until 3) {
        val x = Math.sin((phase + b * 0.31f) * 2.0 * Math.PI).toFloat()
        val bh = box.height * (0.35f + 0.65f * (0.5f + 0.5f * x))
        drawRoundRect(color, Offset(at.x + b * (w + gap), at.y + box.height - bh), Size(w, bh), CornerRadius(w / 2f))
    }
}

/**
 * The watched channel's row, for lists whose rows are shared components: the wash over the row and the bars in the
 * row's left margin, where the thin accent line used to be. Only the bars move, in the draw phase.
 */
@Composable
fun Modifier.playingMark(on: Boolean): Modifier {
    if (!on) return this
    val accent = stageAccent.accent
    val phase by eqPhase()
    return this.drawBehind {
        drawRoundRect(accent.copy(alpha = PlayingMark.WASH), cornerRadius = CornerRadius(14.mpx.toPx()))
        val bw = 9.mpx.toPx()
        val bh = 16.mpx.toPx()
        drawBars(accent, phase, Offset(3.mpx.toPx(), (size.height - bh) / 2f), Size(bw, bh))
    }
}

/** Where the watched channel went, with its name: a pill at the list's top ([above]) or foot. */
@Composable
fun WatchingPill(name: String, above: Boolean) {
    val a = stageAccent
    Row(
        Modifier
            .shadow(8.mpx, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(a.accent)
            .padding(horizontal = 16.mpx, vertical = 7.mpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.mpx),
    ) {
        Text(if (above) "▲" else "▼", style = stageText(14, 800), color = a.onAccent)
        EqualizerBars(a.onAccent, Modifier.size(16.mpx))
        Text(
            stringResource(R.string.glcn_deck_watching, name), style = stageText(16, 700), color = a.onAccent,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
