package tv.own.owntv.glcn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent

/**
 * glcn: the way to choose Home's layout — four cards, each with a small sketch of how Home would look,
 * instead of a chip that cycles through the modes with Right and leaves the person guessing.
 */
@Composable
fun HomeLayoutPicker(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    val names = listOf(
        R.string.glcn_home_backdrop_classic, R.string.glcn_home_backdrop_cinema,
        R.string.glcn_home_backdrop_panel, R.string.glcn_home_backdrop_dock,
    )
    val lines = listOf(
        R.string.glcn_setup_home_classic, R.string.glcn_setup_home_cinema,
        R.string.glcn_setup_home_panel, R.string.glcn_setup_home_dock,
    )
    StagePopup(onDismiss = onDismiss, title = stringResource(R.string.glcn_home_backdrop_title), width = 1620.mpx) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
            names.forEachIndexed { mode, name ->
                val picked = mode == current
                StageSurface(
                    onClick = { onPick(mode); onDismiss() },
                    radius = 22.mpx,
                    modifier = Modifier.weight(1f).then(if (mode == current.coerceIn(0, 3)) Modifier.focusRequester(first) else Modifier),
                ) { _ ->
                    Column(Modifier.padding(14.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                        Sketch(mode, Modifier.fillMaxWidth().height(150.mpx))
                        Text(
                            stringResource(name),
                            style = stageText(21, 800), color = if (picked) stageAccent.accent else StageColors.Text, maxLines = 1,
                        )
                        Text(
                            stringResource(lines[mode]),
                            style = stageText(15, 400), color = StageColors.Dim, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.mpx))
    }
}

/** A tiny drawing of one layout: a screen, the picture, the rail, the rows. Shapes only — no words. */
@Composable
private fun Sketch(mode: Int, modifier: Modifier) {
    val ink = StageColors.Ink
    val accent = stageAccent.accent
    androidx.compose.foundation.layout.Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val r = CornerRadius(10f, 10f)
            // The screen.
            drawRoundRect(ink.copy(alpha = 0.10f), size = size, cornerRadius = CornerRadius(14f, 14f))
            val railW = w * 0.05f
            fun block(x: Float, y: Float, bw: Float, bh: Float, c: Color, rad: CornerRadius = r) =
                drawRoundRect(c, topLeft = Offset(x, y), size = Size(bw, bh), cornerRadius = rad)
            fun posters(x: Float, y: Float, count: Int, pw: Float, ph: Float, gap: Float) {
                repeat(count) { i -> block(x + i * (pw + gap), y, pw, ph, ink.copy(alpha = 0.28f), CornerRadius(5f, 5f)) }
            }
            fun lines(x: Float, y: Float, widths: List<Float>, th: Float, c: Color) {
                widths.forEachIndexed { i, lw -> block(x, y + i * (th + 5f), lw, th, c, CornerRadius(th / 2, th / 2)) }
            }
            // The rail, the same in every layout.
            block(w * 0.02f, h * 0.12f, railW, h * 0.76f, ink.copy(alpha = 0.22f), CornerRadius(8f, 8f))
            val left = w * 0.02f + railW + w * 0.03f
            when (mode) {
                0 -> { // Classic: the billboard in the corner, rows under it.
                    block(w * 0.45f, h * 0.06f, w * 0.52f, h * 0.44f, accent.copy(alpha = 0.40f))
                    lines(left, h * 0.14f, listOf(w * 0.26f, w * 0.18f, w * 0.22f), 7f, ink.copy(alpha = 0.55f))
                    posters(left, h * 0.58f, 5, w * 0.12f, h * 0.17f, w * 0.015f)
                    posters(left, h * 0.80f, 5, w * 0.12f, h * 0.17f, w * 0.015f)
                }
                1 -> { // Cinema: the picture fills the screen, the rows below.
                    block(0f, 0f, w, h, accent.copy(alpha = 0.34f), CornerRadius(14f, 14f))
                    shade(w, h)
                    lines(left, h * 0.18f, listOf(w * 0.30f, w * 0.20f, w * 0.26f), 7f, ink.copy(alpha = 0.7f))
                    posters(left, h * 0.62f, 6, w * 0.11f, h * 0.30f, w * 0.015f)
                }
                2 -> { // Panel: the rows at the left, a fixed panel at the right.
                    block(0f, 0f, w, h, accent.copy(alpha = 0.30f), CornerRadius(14f, 14f))
                    shade(w, h)
                    posters(left, h * 0.16f, 3, w * 0.13f, h * 0.30f, w * 0.015f)
                    posters(left, h * 0.54f, 3, w * 0.13f, h * 0.30f, w * 0.015f)
                    block(w * 0.66f, h * 0.10f, w * 0.31f, h * 0.80f, ink.copy(alpha = 0.22f), CornerRadius(12f, 12f))
                    lines(w * 0.69f, h * 0.18f, listOf(w * 0.20f, w * 0.14f, w * 0.22f, w * 0.18f), 6f, ink.copy(alpha = 0.55f))
                }
                else -> { // Dock: a large title over its picture and a glass dock with tabs.
                    block(0f, 0f, w, h, accent.copy(alpha = 0.40f), CornerRadius(14f, 14f))
                    shade(w, h)
                    lines(left, h * 0.14f, listOf(w * 0.34f, w * 0.22f), 9f, ink.copy(alpha = 0.75f))
                    block(left - w * 0.01f, h * 0.52f, w * 0.90f - left + w * 0.06f, h * 0.42f, ink.copy(alpha = 0.26f), CornerRadius(14f, 14f))
                    repeat(4) { i -> block(left + i * w * 0.15f, h * 0.57f, w * 0.12f, 6f, ink.copy(alpha = if (i == 1) 0.8f else 0.4f), CornerRadius(3f, 3f)) }
                    posters(left + w * 0.01f, h * 0.68f, 5, w * 0.13f, h * 0.20f, w * 0.015f)
                }
            }
        }
    }
}

/** A soft fade from the left and bottom, so the sketch's lines read over its picture. */
private fun DrawScope.shade(w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f))), size = Size(w, h))
}
