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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the way to choose a layout — a card per option with a small drawing of how the screen will look,
 * the same as Home's. One card is one choice; OK picks it and closes.
 */
internal class GalleryOption(val key: String, val name: Int, val desc: Int, val sketch: DrawScope.(Color, Color, Color) -> Unit)

@Composable
internal fun GalleryPopup(title: Int, body: Int?, options: List<GalleryOption>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    StagePopup(onDismiss = onDismiss, title = stringResource(title), body = body?.let { stringResource(it) }, width = 1620.mpx) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
            options.forEach { o ->
                val picked = o.key == current
                StageSurface(
                    onClick = { onPick(o.key); onDismiss() },
                    radius = 22.mpx,
                    modifier = Modifier.weight(1f).then(if (picked || (options.none { it.key == current } && o === options.first())) Modifier.focusRequester(first) else Modifier),
                ) { focused ->
                    Column(Modifier.padding(14.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                        GallerySketch(o.sketch, focused, Modifier.fillMaxWidth().height(170.mpx))
                        Text(
                            stringResource(o.name), style = stageText(21, 800), maxLines = 1,
                            color = if (focused) stageAccent.onAccent else if (picked) stageAccent.accent else StageColors.Text,
                        )
                        if (o.desc != 0) Text(
                            stringResource(o.desc), style = stageText(15, 500), maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start,
                            color = if (focused) stageAccent.onAccent.copy(alpha = 0.9f) else StageColors.Dim,
                        )
                    }
                }
            }
        }
    }
}

/** A screen-shaped box the drawing goes in: ink for lines, the accent for pictures, a fill for panels. */
@Composable
internal fun GallerySketch(draw: DrawScope.(Color, Color, Color) -> Unit, focused: Boolean, modifier: Modifier) {
    val ink = if (focused) stageAccent.onAccent else StageColors.Ink
    val accent = stageAccent.accent
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(ink.copy(alpha = 0.10f), size = size, cornerRadius = CornerRadius(14f, 14f))
            draw(ink, accent, accent)
        }
    }
}

// --- drawing helpers ------------------------------------------------------------------------------------

private val Coral = Color(0xFFFF7A6B)
private val Amber = Color(0xFFFFC14D)
private val Violet = Color(0xFF8B6CFF)
private val Sky = Color(0xFF4DB8FF)

internal fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, c: Color, r: Float = 8f) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r))

internal fun DrawScope.art(x: Float, y: Float, w: Float, h: Float, a: Color, b: Color, alpha: Float = 0.7f, r: Float = 10f) =
    drawRoundRect(
        Brush.linearGradient(listOf(a.copy(alpha = alpha), b.copy(alpha = alpha)), Offset(x, y), Offset(x + w, y + h)),
        Offset(x, y), Size(w, h), CornerRadius(r, r),
    )

internal fun DrawScope.bars(x: Float, y: Float, widths: List<Float>, th: Float, c: Color) =
    widths.forEachIndexed { i, lw -> box(x, y + i * (th + 5f), lw, th, c, th / 2) }

internal fun DrawScope.tiles(x: Float, y: Float, n: Int, tw: Float, th: Float, gap: Float, tints: List<Color>, first: Int = 0) =
    repeat(n) { i -> box(x + i * (tw + gap), y, tw, th, tints[(first + i) % tints.size].copy(alpha = 0.62f), 5f) }

private fun DrawScope.fade(w: Float, h: Float) =
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f))), size = Size(w, h))

private fun tintsOf(accent: Color) = listOf(Coral, Amber, accent, Violet, Sky)

// --- Live TV --------------------------------------------------------------------------------------------

/** Stage: the preview and its information at the top, the channels as a row of cards, the rest in buttons. */
private val liveStage: DrawScope.(Color, Color, Color) -> Unit = { ink, accent, _ ->
    val w = size.width; val h = size.height
    box(w * 0.02f, h * 0.12f, w * 0.04f, h * 0.76f, ink.copy(alpha = 0.30f))
    art(w * 0.10f, h * 0.08f, w * 0.34f, h * 0.38f, Sky, accent)
    bars(w * 0.48f, h * 0.12f, listOf(w * 0.26f, w * 0.18f, w * 0.30f), 7f, ink.copy(alpha = 0.75f))
    repeat(3) { i -> box(w * 0.66f + i * w * 0.1f, h * 0.50f, w * 0.085f, 10f, ink.copy(alpha = 0.4f), 5f) }
    tiles(w * 0.10f, h * 0.60f, 4, w * 0.2f, h * 0.3f, w * 0.015f, tintsOf(accent))
}

/** Separate: categories, the channel list and the preview, side by side. */
private val liveSeparate: DrawScope.(Color, Color, Color) -> Unit = { ink, accent, _ ->
    val w = size.width; val h = size.height
    bars(w * 0.04f, h * 0.12f, List(6) { w * (0.14f - (it % 3) * 0.02f) }, 7f, ink.copy(alpha = 0.45f))
    box(w * 0.24f, h * 0.08f, w * 0.34f, h * 0.84f, ink.copy(alpha = 0.14f))
    repeat(5) { i ->
        box(w * 0.26f, h * (0.13f + i * 0.16f), w * 0.05f, h * 0.10f, tintsOf(accent)[i].copy(alpha = 0.7f), 4f)
        box(w * 0.33f, h * (0.14f + i * 0.16f), w * (0.14f + (i % 2) * 0.05f), 7f, ink.copy(alpha = 0.6f), 4f)
    }
    art(w * 0.62f, h * 0.08f, w * 0.34f, h * 0.36f, Sky, accent)
    bars(w * 0.62f, h * 0.52f, listOf(w * 0.26f, w * 0.18f, w * 0.22f, w * 0.14f), 7f, ink.copy(alpha = 0.6f))
}

/** Cinematic: the programme's picture behind everything, the channels as picture cards. */
private val liveCinematic: DrawScope.(Color, Color, Color) -> Unit = { ink, accent, _ ->
    val w = size.width; val h = size.height
    art(0f, 0f, w, h, Violet, accent, alpha = 0.62f, r = 14f)
    fade(w, h)
    bars(w * 0.07f, h * 0.14f, listOf(w * 0.34f, w * 0.22f, w * 0.28f), 8f, Color.White.copy(alpha = 0.9f))
    tiles(w * 0.07f, h * 0.58f, 4, w * 0.21f, h * 0.34f, w * 0.015f, tintsOf(accent), first = 1)
}

// --- Movies & Series ------------------------------------------------------------------------------------

/** Separate panels: categories, the title list and a preview. */
private val vodSeparate: DrawScope.(Color, Color, Color) -> Unit = { ink, accent, _ ->
    val w = size.width; val h = size.height
    bars(w * 0.04f, h * 0.12f, List(6) { w * (0.13f - (it % 3) * 0.02f) }, 7f, ink.copy(alpha = 0.45f))
    repeat(3) { r -> repeat(3) { c ->
        box(w * (0.24f + c * 0.11f), h * (0.10f + r * 0.29f), w * 0.095f, h * 0.26f, tintsOf(accent)[(r * 3 + c) % 5].copy(alpha = 0.62f), 5f)
    } }
    art(w * 0.60f, h * 0.10f, w * 0.36f, h * 0.38f, Coral, accent)
    bars(w * 0.60f, h * 0.56f, listOf(w * 0.30f, w * 0.22f, w * 0.26f), 7f, ink.copy(alpha = 0.6f))
}

/** Cinematic: the title's artwork fills the screen, its details above a poster grid. */
private val vodCinematic: DrawScope.(Color, Color, Color) -> Unit = { ink, accent, _ ->
    val w = size.width; val h = size.height
    art(0f, 0f, w, h, Coral, accent, alpha = 0.62f, r = 14f)
    fade(w, h)
    bars(w * 0.07f, h * 0.12f, listOf(w * 0.36f, w * 0.22f, w * 0.30f), 8f, Color.White.copy(alpha = 0.92f))
    tiles(w * 0.07f, h * 0.52f, 6, w * 0.125f, h * 0.20f, w * 0.015f, tintsOf(accent))
    tiles(w * 0.07f, h * 0.76f, 6, w * 0.125f, h * 0.20f, w * 0.015f, tintsOf(accent), first = 3)
}

@Composable
fun LiveLayoutGallery(current: SettingsRepository.LiveLayout, onPick: (SettingsRepository.LiveLayout) -> Unit, onDismiss: () -> Unit) =
    GalleryPopup(
        title = R.string.settings_live_layout, body = R.string.settings_live_layout_description,
        options = listOf(
            GalleryOption(SettingsRepository.LiveLayout.STAGE.name, R.string.settings_live_layout_stage, R.string.settings_live_layout_stage_description, liveStage),
            GalleryOption(SettingsRepository.LiveLayout.SEPARATE.name, R.string.settings_vod_layout_separate, R.string.settings_live_layout_separate_description, liveSeparate),
            GalleryOption(SettingsRepository.LiveLayout.CINEMATIC.name, R.string.settings_vod_layout_cinematic, R.string.settings_live_layout_cinematic_description, liveCinematic),
        ),
        current = current.name, onPick = { onPick(SettingsRepository.LiveLayout.valueOf(it)) }, onDismiss = onDismiss,
    )

@Composable
fun VodLayoutGallery(current: SettingsRepository.VodLayout, onPick: (SettingsRepository.VodLayout) -> Unit, onDismiss: () -> Unit) =
    GalleryPopup(
        title = R.string.settings_vod_layout, body = R.string.settings_vod_layout_screen_description,
        options = listOf(
            GalleryOption(SettingsRepository.VodLayout.SEPARATE.name, R.string.settings_vod_layout_separate, R.string.settings_vod_layout_separate_description, vodSeparate),
            GalleryOption(SettingsRepository.VodLayout.CINEMATIC.name, R.string.settings_vod_layout_cinematic, R.string.settings_vod_layout_cinematic_description, vodCinematic),
        ),
        current = current.name, onPick = { onPick(SettingsRepository.VodLayout.valueOf(it)) }, onDismiss = onDismiss,
    )

// --- Live TV opens in -----------------------------------------------------------------------------------

/** The list: the categories, the channels in a column and the preview. */
private val openInList: DrawScope.(Color, Color, Color) -> Unit = { ink, accent, _ -> liveSeparate(this, ink, accent, accent) }

/** The guide: channels down the side, the day's programmes as blocks along a time line. */
private val openInGuide: DrawScope.(Color, Color, Color) -> Unit = { ink, accent, _ ->
    val w = size.width; val h = size.height
    val tints = tintsOf(accent)
    // The time line, with "now" marked.
    repeat(4) { i -> box(w * (0.22f + i * 0.2f), h * 0.07f, w * 0.08f, 6f, ink.copy(alpha = 0.45f), 3f) }
    drawRect(accent, Offset(w * 0.52f, h * 0.05f), Size(3f, h * 0.9f))
    repeat(5) { r ->
        val y = h * (0.16f + r * 0.162f)
        box(w * 0.04f, y, w * 0.12f, h * 0.12f, tints[r % 5].copy(alpha = 0.7f), 5f)
        var x = w * 0.19f
        listOf(0.2f, 0.27f, 0.18f).forEachIndexed { k, seg ->
            box(x, y, w * seg - 4f, h * 0.12f, ink.copy(alpha = if ((r + k) % 2 == 0) 0.22f else 0.14f), 5f)
            x += w * seg
        }
    }
}

@Composable
fun LiveOpensInGallery(current: SettingsRepository.LiveView, onPick: (SettingsRepository.LiveView) -> Unit, onDismiss: () -> Unit) =
    GalleryPopup(
        title = R.string.settings_live_opens_in, body = R.string.settings_live_opens_in_description,
        options = listOf(
            GalleryOption(SettingsRepository.LiveView.LIST.name, R.string.content_live_list_view, 0, openInList),
            GalleryOption(SettingsRepository.LiveView.GUIDE.name, R.string.content_live_guide_view, 0, openInGuide),
        ),
        current = current.name, onPick = { onPick(SettingsRepository.LiveView.valueOf(it)) }, onDismiss = onDismiss,
    )
