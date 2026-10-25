package tv.own.owntv.glcn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.theme.GlcnStyle
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the way to choose a colour style — every style drawn as a small Home in its own colours, two rows
 * of five, and "My own colours" under them. OK picks one.
 */
@Composable
fun StylePickerPopup(current: GlcnStyle?, onPick: (GlcnStyle?) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    StagePopup(onDismiss = onDismiss, title = stringResource(R.string.glcn_style_title), body = stringResource(R.string.glcn_style_desc), width = 1700.mpx) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.mpx)) {
            GlcnStyle.entries.chunked(5).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    row.forEachIndexed { c, st ->
                        val picked = st == current
                        StageSurface(
                            onClick = { onPick(st); onDismiss() },
                            radius = 18.mpx,
                            modifier = Modifier.weight(1f).then(if ((current == null && r == 0 && c == 0) || picked) Modifier.focusRequester(first) else Modifier),
                        ) { focused ->
                            Column(Modifier.padding(10.mpx), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                                StyleSketch(st, focused, Modifier.fillMaxWidth().height(112.mpx))
                                Text(
                                    st.label(), style = stageText(18, 800), maxLines = 1,
                                    color = if (focused) stageAccent.onAccent else if (picked) stageAccent.accent else StageColors.Text,
                                )
                                Text(
                                    st.kind(), style = stageText(13, 500), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (focused) stageAccent.onAccent.copy(alpha = 0.9f) else StageColors.Dim,
                                )
                            }
                        }
                    }
                }
            }
            StageSurface(onClick = { onPick(null); onDismiss() }, radius = 14.mpx, modifier = Modifier.fillMaxWidth()) { focused ->
                Column(Modifier.padding(horizontal = 18.mpx, vertical = 12.mpx)) {
                    Text(
                        stringResource(R.string.glcn_style_none), style = stageText(19, 800),
                        color = if (focused) stageAccent.onAccent else if (current == null) stageAccent.accent else StageColors.Text,
                    )
                    Text(
                        stringResource(R.string.glcn_style_none_desc), style = stageText(14, 500), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (focused) stageAccent.onAccent.copy(alpha = 0.9f) else StageColors.Dim,
                    )
                }
            }
        }
    }
}

/** A Home in the style's own colours: its page, its panels (glass ones see-through), its accent and text. */
@Composable
private fun StyleSketch(style: GlcnStyle, focused: Boolean, modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) { drawStyle(style) }
    }
}

private fun DrawScope.drawStyle(style: GlcnStyle) {
    val w = size.width
    val h = size.height
    val page = Color(style.page)
    val accent = Color(android.graphics.Color.parseColor(style.accentHex))
    val text = Color(style.text)
    val muted = Color(style.muted)
    val panel = Color(style.glassTint)
    val corner = CornerRadius(14f, 14f)
    drawRoundRect(page, size = size, cornerRadius = corner)
    // Glass styles show a soft glow of the accent through their panels; flat ones stay flat.
    if (style.glass) {
        drawRoundRect(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), Offset(w * 0.75f, h * 0.2f), w * 0.6f),
            size = size, cornerRadius = corner,
        )
    }
    val panelAlpha = if (style.glass) 0.55f else 1f
    fun block(x: Float, y: Float, bw: Float, bh: Float, c: Color, r: Float = 8f) =
        drawRoundRect(c, Offset(x, y), Size(bw, bh), CornerRadius(r, r))
    // The rail.
    block(w * 0.03f, h * 0.12f, w * 0.06f, h * 0.76f, panel.copy(alpha = panelAlpha))
    if (style.glass) drawRoundRect(text.copy(alpha = 0.14f), Offset(w * 0.03f, h * 0.12f), Size(w * 0.06f, h * 0.76f), CornerRadius(8f, 8f), style = Stroke(1.5f))
    // The billboard: the accent over the page, with a title and a line in the style's text colours.
    drawRoundRect(
        Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.25f)), Offset(w * 0.40f, 0f), Offset(w * 0.96f, h * 0.5f)),
        Offset(w * 0.40f, h * 0.08f), Size(w * 0.56f, h * 0.42f), CornerRadius(10f, 10f),
    )
    block(w * 0.14f, h * 0.14f, w * 0.20f, 8f, text, 4f)
    block(w * 0.14f, h * 0.14f + 15f, w * 0.14f, 6f, muted, 3f)
    block(w * 0.14f, h * 0.14f + 28f, w * 0.17f, 6f, muted, 3f)
    // The cards: the style's panel colour, one of them with the accent's focus ring.
    repeat(4) { i ->
        val x = w * (0.14f + i * 0.2f)
        block(x, h * 0.60f, w * 0.17f, h * 0.30f, panel.copy(alpha = panelAlpha))
        if (style.glass) drawRoundRect(text.copy(alpha = 0.12f), Offset(x, h * 0.60f), Size(w * 0.17f, h * 0.30f), CornerRadius(8f, 8f), style = Stroke(1.2f))
        block(x + w * 0.015f, h * 0.84f, w * 0.1f, 5f, if (i == 0) accent else muted.copy(alpha = 0.7f), 3f)
    }
    drawRoundRect(accent, Offset(w * 0.14f, h * 0.60f), Size(w * 0.17f, h * 0.30f), CornerRadius(8f, 8f), style = Stroke(3f))
}
