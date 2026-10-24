package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.stageAccent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the channel list over the player as picture cards — what each channel shows now, with its
 * programme picture — along the foot of the screen, so the choice is made by what is on rather than by
 * name. ◀ ▶ move, OK switches, ▲ goes back to the list view, Back closes. Pictures are read a window
 * at a time around the cards on screen.
 */
@Composable
fun ZapPosterOverlay(
    channels: List<ChannelEntity>,
    currentId: Long?,
    title: String?,
    loadNow: suspend (List<ChannelEntity>) -> Map<Long, EpgProgrammeEntity>,
    loadSchedule: (suspend (ChannelEntity) -> List<EpgProgrammeEntity>)? = null,
    onSelect: (ChannelEntity) -> Unit,
    onListView: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = remember(channels, currentId) { channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0))
    val first = remember { FocusRequester() }
    val programmes = remember(channels) { mutableStateMapOf<Long, EpgProgrammeEntity?>() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler { onDismiss() }
    // A window of pictures around what is on screen, once per stop.
    LaunchedEffect(channels) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { at ->
            val window = channels.subList((at - 2).coerceAtLeast(0), (at + 10).coerceAtMost(channels.size))
                .filter { it.id !in programmes }
            if (window.isEmpty()) return@collect
            val got = runCatching { loadNow(window) }.getOrDefault(emptyMap())
            window.forEach { programmes[it.id] = got[it.id] }
        }
    }
    // Opened by holding OK, the same OK is still down — and its repeats and release would land on the
    // focused card and pick it at once, closing the strip. OK counts only from a fresh press.
    var focusedCh by remember { androidx.compose.runtime.mutableStateOf<ChannelEntity?>(null) }
    var okArmed by remember { androidx.compose.runtime.mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    val shade = tv.own.owntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    Box(
        modifier.fillMaxSize().onPreviewKeyEvent { e ->
            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (!ok) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) okArmed = true
            !okArmed
        },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to shade.copy(alpha = 0.78f), 1f to shade.copy(alpha = 0.92f)))
                .padding(top = 90.mpx, bottom = 40.mpx)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) false
                    // glcn: ▲ opens the list; ◀ from the first channel goes back to it too.
                    else if (e.key == Key.DirectionUp) { onListView(); true }
                    else if (e.key == Key.DirectionLeft && focusedCh?.id == channels.firstOrNull()?.id) { onListView(); true }
                    else false
                },
        ) {
            Row(
                Modifier.padding(start = 64.mpx, end = 64.mpx, bottom = 14.mpx),
                horizontalArrangement = Arrangement.spacedBy(14.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title ?: stringResource(R.string.content_channel_overlay_title),
                    style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "· " + pluralStringResource(R.plurals.content_live_channel_count, channels.size, channels.size),
                    style = stageText(18, 500), color = StageColors.Muted, maxLines = 1,
                )
                Text(
                    "▲ " + stringResource(R.string.glcn_zap_view_list),
                    style = stageText(16, 600), color = StageColors.Dim, maxLines = 1,
                    modifier = Modifier.padding(start = 20.mpx),
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(
                tv.own.owntv.features.home.LocalHomeFocusInfo provides { info -> info?.channel?.let { focusedCh = it } },
            ) { LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(20.mpx),
                contentPadding = PaddingValues(start = 64.mpx, end = 64.mpx, top = 10.mpx, bottom = 10.mpx),
                modifier = Modifier.focusGroup(),
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { index, ch ->
                    tv.own.owntv.features.home.ArtLiveCard(
                        channel = ch,
                        programme = programmes[ch.id],
                        now = now,
                        onClick = { onSelect(ch) },
                        width = 360.mpx,
                        modifier = if (index == start) Modifier.focusRequester(first) else Modifier,
                    )
                }
            } }
        }
        // glcn: the focused channel's programme in full, above the strip on the right — the picture
        // behind stays free on the left.
        if (loadSchedule != null) ZapPosterInfo(
            channel = focusedCh,
            programmes = programmes,
            loadSchedule = loadSchedule,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 64.mpx, bottom = 392.mpx),
        )
    }
}

@Composable
private fun ZapPosterInfo(
    channel: ChannelEntity?,
    programmes: Map<Long, EpgProgrammeEntity?>,
    loadSchedule: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    modifier: Modifier = Modifier,
) {
    var rows by remember { mutableStateOf<List<EpgProgrammeEntity>>(emptyList()) }
    LaunchedEffect(channel?.id) {
        rows = emptyList()
        if (channel == null) return@LaunchedEffect
        kotlinx.coroutines.delay(200) // not a fetch per card while the D-pad is moving
        rows = runCatching { loadSchedule(channel) }.getOrDefault(emptyList())
    }
    val ch = channel ?: return
    val nowMs = System.currentTimeMillis()
    val card = programmes[ch.id]
    val cur = rows.firstOrNull { nowMs in it.startMs until it.stopMs } ?: card ?: return // no guide: nothing
    val next = rows.filter { it.startMs >= cur.stopMs }.take(2)
    // glcn: a film or series the guide says little about: TMDB lends its year, rating and synopsis, once
    // the D-pad has rested (and from memory when it was asked before).
    val resolver: tv.own.owntv.core.metadata.ProgrammeArtResolver = org.koin.compose.koinInject()
    val lent by androidx.compose.runtime.produceState<tv.own.owntv.core.metadata.ProgrammeArtResolver.Info?>(null, cur.title, cur.categories) {
        value = null
        if (!cur.description.isNullOrBlank() && cur.year != null) return@produceState
        kotlinx.coroutines.delay(250)
        value = runCatching { resolver.detailsFor(cur.title, cur.categories) }.getOrNull()
    }
    val fmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val remaining = ((cur.stopMs - nowMs) / 60_000L).toInt()
    val logo = ch.displayLogoUrl?.takeIf { it.isNotBlank() }
    val accent = stageAccent.accent
    Column(
        modifier.width(520.mpx).stageGlass(StageRadii.Sheet, overContent = true).padding(18.mpx),
        verticalArrangement = Arrangement.spacedBy(8.mpx),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
            // No logo: the words alone.
            if (logo != null) tv.own.owntv.ui.components.ChannelLogoTile(
                logoUrl = logo,
                modifier = Modifier.size(72.mpx, 52.mpx),
                fill = Color.Transparent,
            ) { }
            Column(Modifier.weight(1f)) {
                Text(cur.title, style = stageText(24, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    tv.own.owntv.core.live.ProviderTags.parse(ch.name).name + " · " +
                        if (remaining in 1..600) stringResource(R.string.content_live_time_remaining, fmt.format(java.util.Date(cur.stopMs)), remaining)
                        else stringResource(R.string.content_live_time_range, fmt.format(java.util.Date(cur.startMs)), fmt.format(java.util.Date(cur.stopMs))),
                    style = stageText(16, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val span = (cur.stopMs - cur.startMs).coerceAtLeast(1L)
        val f = ((nowMs - cur.startMs).toFloat() / span).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(4.mpx).clip(RoundedCornerShape(2.mpx)).background(StageColors.Ink.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(f).height(4.mpx).background(accent))
        }
        // genre · year · age / rating: one quiet line
        val facts = (
            cur.details.categoryList.take(2) +
                listOfNotNull(
                    (cur.year ?: lent?.year)?.toString(),
                    cur.rating ?: lent?.rating?.let { "★ " + tv.own.owntv.core.metadata.ProgrammeArtResolver.format(it) },
                )
            ).filter { it.isNotBlank() }.joinToString(" · ")
        if (facts.isNotEmpty()) Text(facts, style = stageText(15, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        (cur.description ?: card?.description)?.takeIf { it.isNotBlank() }
            ?.let { it }
            .let { it ?: lent?.overview }
            ?.let { DriftingSynopsis(it) }
        if (next.isNotEmpty()) {
            Text(
                stringResource(R.string.content_live_next), style = stageText(15, 700), color = accent,
                modifier = Modifier.padding(top = 4.mpx),
            )
            next.forEach { n ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    Text(fmt.format(java.util.Date(n.startMs)), style = stageText(16, 700), color = StageColors.Muted, maxLines = 1, modifier = Modifier.width(76.mpx))
                    Text(n.title, style = stageText(16, 500), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}


/**
 * glcn: a synopsis that fills three lines and, when it is longer, drifts down to its end and back, so the
 * whole text can be read without a scrollbar or a cut-off "…".
 */
@Composable
internal fun DriftingSynopsis(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = stageText(16, 400),
    color: Color = StageColors.Dim,
    lines: Int = 3,
) {
    val lineH = with(androidx.compose.ui.platform.LocalDensity.current) { style.fontSize.toDp() } * 1.4f
    val scroll = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(text, scroll.maxValue) {
        scroll.scrollTo(0)
        if (scroll.maxValue <= 0) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(2_500)
            scroll.animateScrollTo(scroll.maxValue, androidx.compose.animation.core.tween(scroll.maxValue * 45, easing = androidx.compose.animation.core.LinearEasing))
            kotlinx.coroutines.delay(2_000)
            scroll.scrollTo(0)
        }
    }
    Box(modifier.fillMaxWidth().heightIn(max = lineH * lines).clip(androidx.compose.ui.graphics.RectangleShape)) {
        Text(
            text, style = style.copy(lineHeight = style.fontSize * 1.4f), color = color,
            modifier = Modifier.verticalScroll(scroll, enabled = false),
        )
    }
}
