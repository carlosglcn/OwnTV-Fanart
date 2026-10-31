package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.stage.StagePill
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn (TV profile): ▼ in the full-screen player — the picture goes on playing above, and a strip rises over its lower
 * half: the groups (your channels, your favourites' groups, recent, all) and the channels on air as picture cards, the
 * one playing first. OK tunes and closes; ▲ from the cards reaches the groups; Back closes.
 */
@Composable
fun TvStrip(
    load: suspend () -> List<LiveViewModel.StripGroup>,
    loadNow: suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>,
    currentId: Long?,
    onTune: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onDismiss: () -> Unit,
) {
    val groups by produceState<List<LiveViewModel.StripGroup>?>(null) { value = runCatching { load() }.getOrDefault(emptyList()) }
    var selected by remember { mutableIntStateOf(0) }
    val cardFocus = remember { FocusRequester() }
    val enter = remember { androidx.compose.animation.core.Animatable(if (tv.own.owntv.glcn.LowRam.on) 1f else 0f) }
    val enterSpec = tv.own.owntv.ui.theme.ownTvTween<Float>(STRIP_ENTER_MS, androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
    LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }
    // The playing channel's card takes the focus once the groups are in.
    LaunchedEffect(groups != null) { if (groups != null) { androidx.compose.runtime.withFrameNanos { }; runCatching { cardFocus.requestFocus() } } }
    androidx.activity.compose.BackHandler { onDismiss() }
    val shade = Color(6, 9, 12)
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(
            Modifier.fillMaxSize().okAfterFreshPress()
                .graphicsLayer { alpha = 0.3f + 0.7f * enter.value }
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Transparent, 0.62f to shade.copy(alpha = 0.78f), 1f to shade.copy(alpha = 0.94f))),
        ) {
            val list = groups.orEmpty()
            val group = list.getOrNull(selected.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 46.mpx)
                    .graphicsLayer { translationY = (1f - enter.value) * STRIP_RISE.mpx.toPx() },
                verticalArrangement = Arrangement.spacedBy(18.mpx),
            ) {
                // The groups, as pills; ◀ ▶ move between them and the cards follow.
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 64.mpx),
                    horizontalArrangement = Arrangement.spacedBy(12.mpx),
                    modifier = Modifier.focusRestorer().focusGroup(),
                ) {
                    itemsIndexed(list) { i, g ->
                        StagePill(
                            text = stripTitle(g),
                            onClick = { selected = i; runCatching { cardFocus.requestFocus() } },
                            small = g.channels.size.toString(),
                            highlighted = i == selected,
                            modifier = Modifier.onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { selected = i; runCatching { cardFocus.requestFocus() }.isSuccess } else false
                            }.onFocusChanged { if (it.hasFocus) selected = i },
                        )
                    }
                }
                group?.let { g ->
                    val start = remember(g) { g.channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0) }
                    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = start)
                    androidx.compose.runtime.key(g) {
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(horizontal = 64.mpx),
                            horizontalArrangement = Arrangement.spacedBy(20.mpx),
                            modifier = Modifier.fillMaxWidth().focusRestorer().focusGroup(),
                        ) {
                            itemsIndexed(g.channels, key = { _, c -> c.id }) { i, ch ->
                                val now by produceState<EpgProgrammeEntity?>(null, ch.id) { value = runCatching { loadNow(ch).first }.getOrNull() }
                                tv.own.owntv.features.home.ArtLiveCard(
                                    channel = ch,
                                    programme = now,
                                    now = System.currentTimeMillis(),
                                    onClick = { onTune(ch, g.channels) },
                                    width = STRIP_CARD_WIDTH.mpx,
                                    modifier = if (i == start) Modifier.focusRequester(cardFocus) else Modifier,
                                )
                            }
                        }
                    }
                }
                if (groups?.isEmpty() == true) Text(
                    stringResource(R.string.glcn_recent_none), style = stageText(18, 600), color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 64.mpx),
                )
            }
        }
    }
}

@Composable
private fun stripTitle(g: LiveViewModel.StripGroup): String = when (g.kind) {
    LiveViewModel.StripKind.FAVORITES -> stringResource(R.string.glcn_strip_my_channels)
    LiveViewModel.StripKind.GROUP -> g.title.orEmpty()
    LiveViewModel.StripKind.RECENT -> stringResource(R.string.content_category_recently_watched)
    LiveViewModel.StripKind.ALL -> stringResource(R.string.content_category_all_channels)
    LiveViewModel.StripKind.STARTING -> stringResource(R.string.glcn_tvhome_starting)
    LiveViewModel.StripKind.FILMS -> stringResource(R.string.glcn_tvhome_films)
    LiveViewModel.StripKind.SPORTS -> stringResource(R.string.glcn_tvhome_sports)
    LiveViewModel.StripKind.TONIGHT -> stringResource(R.string.glcn_tvhome_tonight)
}

private const val STRIP_ENTER_MS = 420
private const val STRIP_RISE = 160
private const val STRIP_CARD_WIDTH = 360
