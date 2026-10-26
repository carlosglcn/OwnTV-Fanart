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
                ) { focused ->
                    Column(Modifier.padding(14.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                        Sketch(mode, focused, Modifier.fillMaxWidth().height(150.mpx))
                        Text(
                            stringResource(name),
                            style = stageText(21, 800),
                            // On the accent fill of the focused card the text takes the colour made for it.
                            color = if (focused) stageAccent.onAccent else if (picked) stageAccent.accent else StageColors.Text, maxLines = 1,
                        )
                        Text(
                            stringResource(lines[mode]),
                            style = stageText(15, 500), color = if (focused) stageAccent.onAccent.copy(alpha = 0.9f) else StageColors.Dim, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.mpx))
    }
}

/** A tiny drawing of one layout: a screen, the picture, the rail, the rows. Shapes and colour only — no words. */
@Composable
private fun Sketch(mode: Int, focused: Boolean, modifier: Modifier) {
    // On the focused card's accent fill the lines turn to the colour made for it, so they stay readable.
    val ink = if (focused) stageAccent.onAccent else StageColors.Ink
    val accent = stageAccent.accent
    // Each layout has a hue of its own, so the four read apart at a glance; posters take turns in the tints.
    val hue = when (mode) { 0 -> Color(0xFFFFA24C); 1 -> Color(0xFF8B6CFF); 2 -> Color(0xFF3FA9F5); else -> Color(0xFFFF6FA3) }
    val tints = listOf(Color(0xFFFF7A6B), Color(0xFFFFC14D), accent, Color(0xFF8B6CFF), Color(0xFF4DB8FF))
    androidx.compose.foundation.layout.Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val r = CornerRadius(10f, 10f)
            // The screen.
            drawRoundRect(ink.copy(alpha = 0.10f), size = size, cornerRadius = CornerRadius(14f, 14f))
            val railW = w * 0.05f
            fun art(x: Float, y: Float, bw: Float, bh: Float, rad: CornerRadius = r, a: Float = 0.75f) =
                drawRoundRect(
                    Brush.linearGradient(listOf(hue.copy(alpha = a), accent.copy(alpha = a)), Offset(x, y), Offset(x + bw, y + bh)),
                    topLeft = Offset(x, y), size = Size(bw, bh), cornerRadius = rad,
                )
            fun block(x: Float, y: Float, bw: Float, bh: Float, c: Color, rad: CornerRadius = r) =
                drawRoundRect(c, topLeft = Offset(x, y), size = Size(bw, bh), cornerRadius = rad)
            fun posters(x: Float, y: Float, count: Int, pw: Float, ph: Float, gap: Float, first: Int = 0) {
                repeat(count) { i -> block(x + i * (pw + gap), y, pw, ph, tints[(first + i) % tints.size].copy(alpha = 0.62f), CornerRadius(5f, 5f)) }
            }
            fun lines(x: Float, y: Float, widths: List<Float>, th: Float, c: Color) {
                widths.forEachIndexed { i, lw -> block(x, y + i * (th + 5f), lw, th, c, CornerRadius(th / 2, th / 2)) }
            }
            fun play(cx: Float, cy: Float, rad: Float) {
                drawCircle(Color.White.copy(alpha = 0.85f), rad, Offset(cx, cy))
                drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx - rad * 0.3f, cy - rad * 0.5f); lineTo(cx + rad * 0.55f, cy); lineTo(cx - rad * 0.3f, cy + rad * 0.5f); close()
                    },
                    hue,
                )
            }
            // The rail, the same in every layout.
            block(w * 0.02f, h * 0.12f, railW, h * 0.76f, ink.copy(alpha = 0.30f), CornerRadius(8f, 8f))
            val left = w * 0.02f + railW + w * 0.03f
            when (mode) {
                0 -> { // Classic: the billboard to the right of the title, rows under it.
                    art(w * 0.36f, h * 0.06f, w * 0.60f, h * 0.46f)
                    play(w * 0.66f, h * 0.29f, h * 0.085f)
                    lines(left, h * 0.14f, listOf(w * 0.24f, w * 0.17f, w * 0.20f), 8f, ink.copy(alpha = 0.75f))
                    posters(left, h * 0.58f, 5, w * 0.12f, h * 0.17f, w * 0.015f)
                    posters(left, h * 0.80f, 5, w * 0.12f, h * 0.17f, w * 0.015f, first = 2)
                }
                1 -> { // Cinema: the picture fills the screen, the rows below.
                    art(0f, 0f, w, h, CornerRadius(14f, 14f), a = 0.62f)
                    shade(w, h)
                    lines(left, h * 0.18f, listOf(w * 0.30f, w * 0.20f, w * 0.26f), 8f, Color.White.copy(alpha = 0.9f))
                    posters(left, h * 0.62f, 6, w * 0.11f, h * 0.30f, w * 0.015f)
                }
                2 -> { // Panel: the rows at the left, a fixed panel at the right.
                    art(0f, 0f, w, h, CornerRadius(14f, 14f), a = 0.55f)
                    shade(w, h)
                    posters(left, h * 0.16f, 3, w * 0.13f, h * 0.30f, w * 0.015f)
                    posters(left, h * 0.54f, 3, w * 0.13f, h * 0.30f, w * 0.015f, first = 3)
                    block(w * 0.66f, h * 0.10f, w * 0.31f, h * 0.80f, Color.Black.copy(alpha = 0.32f), CornerRadius(12f, 12f))
                    lines(w * 0.69f, h * 0.18f, listOf(w * 0.20f, w * 0.14f, w * 0.22f, w * 0.18f), 6f, Color.White.copy(alpha = 0.85f))
                }
                else -> { // Dock: a large title over its picture, a dock that fades up from the bottom, one wide card and narrow ones.
                    art(0f, 0f, w, h, CornerRadius(14f, 14f), a = 0.70f)
                    shade(w, h)
                    lines(left, h * 0.14f, listOf(w * 0.34f, w * 0.22f), 10f, Color.White.copy(alpha = 0.92f))
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), startY = h * 0.36f, endY = h),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.36f), size = Size(w, h * 0.64f),
                    )
                    repeat(4) { i -> block(left + i * w * 0.15f, h * 0.50f, w * 0.12f, 6f, Color.White.copy(alpha = if (i == 0) 0.95f else 0.45f), CornerRadius(3f, 3f)) }
                    block(left, h * 0.60f, w * 0.30f, h * 0.30f, Color.White.copy(alpha = 0.55f), CornerRadius(10f, 10f))
                    posters(left + w * 0.32f, h * 0.62f, 4, w * 0.085f, h * 0.27f, w * 0.015f, first = 1)
                }
            }
        }
    }
}

/** A soft fade from the left and bottom, so the sketch's lines read over its picture. */
private fun DrawScope.shade(w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f))), size = Size(w, h))
}
