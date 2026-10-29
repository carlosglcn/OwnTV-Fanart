package tv.own.owntv.features.shell.components

import androidx.compose.runtime.setValue

import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.ui.input.key.type

import androidx.compose.ui.input.key.key

import androidx.compose.ui.input.key.onPreviewKeyEvent

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.dao.ChannelWithWatchedAt
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: ▼ on the "Last channel" pill — the last few channels watched, each with what is on now and how far through it
 * is, from any screen. OK plays one; Back or ▲ from the first closes it.
 */
@Composable
fun RecentChannelsDrop(
    load: suspend () -> List<ChannelWithWatchedAt>,
    loadNow: suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>,
    onPlay: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries by produceState<List<ChannelWithWatchedAt>?>(null) { value = runCatching { load() }.getOrDefault(emptyList()).take(RECENT_DROP_MAX) }
    val first = remember { FocusRequester() }
    var focusedIdx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val enter = remember { androidx.compose.animation.core.Animatable(if (tv.own.owntv.glcn.LowRam.on) 1f else 0f) }
    val enterSpec = tv.own.owntv.ui.theme.ownTvTween<Float>(300, androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
    LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }
    LaunchedEffect(entries?.isNotEmpty()) { if (entries?.isNotEmpty() == true) runCatching { first.requestFocus() } }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 100.mpx, end = 64.mpx).width(620.mpx)
                    .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * -24.mpx.toPx() }
                    .stageGlass(StageRadii.Sheet, overContent = true)
                    .padding(14.mpx)
                    // glcn: it closes the way you leave it — ◀ ▶, ▼ past the last channel, ▲ above the first — not only with Back.
                    .onPreviewKeyEvent { e ->
                        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val last = (entries?.size ?: 0) - 1
                        when (e.key) {
                            androidx.compose.ui.input.key.Key.DirectionLeft, androidx.compose.ui.input.key.Key.DirectionRight -> { onDismiss(); true }
                            androidx.compose.ui.input.key.Key.DirectionDown -> if (focusedIdx >= last) { onDismiss(); true } else false
                            androidx.compose.ui.input.key.Key.DirectionUp -> if (focusedIdx <= 0) { onDismiss(); true } else false
                            else -> false
                        }
                    }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.mpx),
            ) {
                Text(
                    stringResource(R.string.content_category_recently_watched), style = stageText(18, 800), color = StageColors.Muted,
                    modifier = Modifier.padding(start = 10.mpx, top = 4.mpx, bottom = 6.mpx),
                )
                entries?.forEachIndexed { i, e ->
                    RecentRow(
                        e.channel, loadNow,
                        Modifier.then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                            .onFocusChanged { if (it.hasFocus) focusedIdx = i },
                    ) { onPlay(e.channel) }
                }
                if (entries?.isEmpty() == true) Text(stringResource(R.string.glcn_recent_none), style = stageText(17, 500), color = StageColors.Muted, modifier = Modifier.padding(10.mpx))
            }
        }
    }
}

@Composable
private fun RecentRow(ch: ChannelEntity, loadNow: suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>, modifier: Modifier, onClick: () -> Unit) {
    val now by produceState<EpgProgrammeEntity?>(null, ch.id) { value = runCatching { loadNow(ch).first }.getOrNull() }
    val a = stageAccent
    StageSurface(onClick = onClick, radius = StageRadii.Row, focusStyle = StageFocus.FILLED, modifier = modifier.fillMaxWidth().height(78.mpx)) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
            tv.own.owntv.features.home.ChannelPlate(ch, Modifier.size(84.mpx, 54.mpx))
            Column(Modifier.weight(1f)) {
                val on = if (focused) a.onAccent else null
                Text(ch.name, style = stageText(18, 700), color = on ?: StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                now?.let { p ->
                    Text(tv.own.owntv.core.live.SportsNow.displayTitle(p.title), style = stageText(15, 500), color = on ?: StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val f = ((System.currentTimeMillis() - p.startMs).toFloat() / (p.stopMs - p.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                    Box(Modifier.padding(top = 5.mpx).fillMaxWidth().height(4.mpx).clip(RoundedCornerShape(2.mpx)).background((on ?: StageColors.Text).copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(f).height(4.mpx).background(on ?: a.accent))
                    }
                }
            }
        }
    }
}

private const val RECENT_DROP_MAX = 6
