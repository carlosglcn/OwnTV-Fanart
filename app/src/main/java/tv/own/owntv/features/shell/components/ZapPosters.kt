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
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
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
import tv.own.owntv.glcn.staggerAt
import tv.own.owntv.glcn.staggerIn

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
    /** glcn: hold OK on a card → the channel's options. */
    onLongPress: ((ChannelEntity) -> Unit)? = null,
    /** glcn: a programme's synopsis (the strip's rows are read without it, to stay light). */
    loadDescription: (suspend (Long) -> String?)? = null,
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
    // glcn: schedules read once, and the channels around the cursor read ahead, so the card beside the
    // strip is complete the moment a channel is focused instead of filling in piece by piece.
    val scheduleCache = remember(channels) { androidx.compose.runtime.mutableStateMapOf<Long, List<EpgProgrammeEntity>>() }
    var focusedCh by remember { androidx.compose.runtime.mutableStateOf<ChannelEntity?>(null) }
    if (loadSchedule != null) {
        LaunchedEffect(channels, focusedCh?.id) {
            val at = channels.indexOfFirst { it.id == focusedCh?.id }.takeIf { it >= 0 } ?: start
            for (d in buildList { add(0); for (k in 1..6) { add(k); add(-k) } }) {
                val c = channels.getOrNull(at + d) ?: continue
                if (c.id in scheduleCache) continue
                runCatching { loadSchedule(c) }.getOrNull()?.let { scheduleCache[c.id] = it }
            }
        }
    }
    // Opened by holding OK, the same OK is still down — and its repeats and release would land on the
    // focused card and pick it at once, closing the strip. OK counts only from a fresh press.
    var okArmed by remember { androidx.compose.runtime.mutableStateOf(false) }
    val cardFocus = remember(channels) { HashMap<Int, FocusRequester>() }
    val wrapScope = androidx.compose.runtime.rememberCoroutineScope()
    fun wrapTo(index: Int) {
        wrapScope.launch {
            listState.scrollToItem(index)
            androidx.compose.runtime.withFrameNanos { }
            runCatching { cardFocus.getOrPut(index) { FocusRequester() }.requestFocus() }
        }
    }
    val now = remember { System.currentTimeMillis() }
    // glcn: ▼ opens what each channel gives next under its card (a few programmes, so the picture behind stays
    // visible); ▲ closes it, and from the closed strip goes to the list as before. Read from the cache the
    // look-ahead above fills, so it opens at once.
    var showNext by remember { androidx.compose.runtime.mutableStateOf(false) }
    val timeFmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val shade = tv.own.owntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    val zapGate = tv.own.owntv.glcn.rememberStaggerGate()
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
                    else if (e.key == Key.DirectionUp) { if (showNext) showNext = false else onListView(); true }
                    else if (e.key == Key.DirectionDown) { if (loadSchedule != null) showNext = true; true }
                    // glcn: ◀ from the first card wraps to the last, ▶ from the last to the first.
                    else if (e.key == Key.DirectionLeft && channels.size > 1 && focusedCh?.id == channels.first().id) { wrapTo(channels.size - 1); true }
                    else if (e.key == Key.DirectionRight && channels.size > 1 && focusedCh?.id == channels.last().id) { wrapTo(0); true }
                    else false
                },
        ) {
            Row(
                Modifier.staggerIn(0).padding(start = 64.mpx, end = 64.mpx, bottom = 14.mpx),
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
                if (loadSchedule != null && !showNext) Text(
                    "▼ " + stringResource(R.string.content_live_next),
                    style = stageText(16, 600), color = StageColors.Dim, maxLines = 1,
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
                    Column(Modifier.staggerAt((index - start + 1).coerceAtLeast(0), zapGate, step = 70, cap = 6).width(360.mpx)) {
                    tv.own.owntv.features.home.ArtLiveCard(
                        channel = ch,
                        programme = programmes[ch.id],
                        now = now,
                        onClick = { onSelect(ch) },
                        onLongClick = onLongPress?.let { lp -> { lp(ch) } },
                        width = 360.mpx,
                        modifier = Modifier.focusRequester(cardFocus.getOrPut(index) { FocusRequester() }).then(if (index == start) Modifier.focusRequester(first) else Modifier),
                    )
                    if (showNext) {
                        val after = programmes[ch.id]?.stopMs ?: now
                        val rows = scheduleCache[ch.id]?.filter { it.startMs >= after }?.take(3).orEmpty()
                        if (rows.isNotEmpty()) ZapNextList(rows, timeFmt)
                    }
                    }
                }
            } }
        }
        // glcn: the focused channel's programme in full, above the strip on the right — the picture
        // behind stays free on the left.
        if (loadSchedule != null) ZapPosterInfo(
            channel = focusedCh,
            programmes = programmes,
            loadSchedule = loadSchedule,
            cache = scheduleCache,
            loadDescription = loadDescription,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 64.mpx, bottom = if (showNext) 530.mpx else 392.mpx).staggerIn(260, fromRight = true),
        )
    }
}

/** glcn: the next few programmes of one channel, under its card — time and title, one line each. */
@Composable
private fun ZapNextList(rows: List<EpgProgrammeEntity>, fmt: java.text.DateFormat) {
    Column(Modifier.fillMaxWidth().padding(start = 6.mpx, end = 6.mpx, top = 12.mpx), verticalArrangement = Arrangement.spacedBy(3.mpx)) {
        Text(stringResource(R.string.content_live_next), style = stageText(15, 700), color = stageAccent.accent, maxLines = 1)
        rows.forEachIndexed { ni, n ->
            Row(Modifier.staggerIn(ni * 50), horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                Text(fmt.format(java.util.Date(n.startMs)), style = stageText(16, 700), color = StageColors.Muted, maxLines = 1, modifier = Modifier.width(66.mpx))
                Text(n.title, style = stageText(16, 500), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ZapPosterInfo(
    channel: ChannelEntity?,
    programmes: Map<Long, EpgProgrammeEntity?>,
    loadSchedule: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    cache: MutableMap<Long, List<EpgProgrammeEntity>>,
    loadDescription: (suspend (Long) -> String?)? = null,
    modifier: Modifier = Modifier,
) {
    // From the cache at once; a channel not read yet is read now, with nothing waited for.
    var loaded by remember { mutableStateOf<Pair<Long, List<EpgProgrammeEntity>>?>(null) }
    LaunchedEffect(channel?.id) {
        if (channel == null) return@LaunchedEffect
        if (channel.id in cache) return@LaunchedEffect
        val got = runCatching { loadSchedule(channel) }.getOrDefault(emptyList())
        cache[channel.id] = got
        loaded = channel.id to got
    }
    val rows = channel?.let { c -> cache[c.id] ?: loaded?.takeIf { it.first == c.id }?.second } ?: emptyList()
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
        kotlinx.coroutines.delay(120)
        value = runCatching { resolver.detailsFor(cur.title, cur.categories) }.getOrNull()
    }
    // glcn: the guide's own synopsis, read for the programme in the card (the rows above carry none).
    val synopsis by androidx.compose.runtime.produceState<String?>(null, cur.id) {
        value = cur.description?.takeIf { it.isNotBlank() } ?: card?.description?.takeIf { it.isNotBlank() }
        if (value == null && loadDescription != null && cur.id > 0) value = runCatching { loadDescription(cur.id) }.getOrNull()?.takeIf { it.isNotBlank() }
    }
    val fmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val remaining = ((cur.stopMs - nowMs) / 60_000L).toInt()
    val logo = ch.displayLogoUrl?.takeIf { it.isNotBlank() }
    val accent = stageAccent.accent
    Column(
        modifier.width(520.mpx).stageGlass(StageRadii.Sheet, overContent = true, share = tv.own.owntv.features.live.PLAYER_SHEET_GLASS).padding(18.mpx),
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
        (synopsis ?: lent?.overview)?.let { DriftingSynopsis(it) }
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
    /** glcn: a long text fades out at its foot instead of being cut through a line. */
    fade: Boolean = false,
) {
    val lineH = with(androidx.compose.ui.platform.LocalDensity.current) { style.fontSize.toDp() } * 1.4f
    val scroll = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(text, scroll.maxValue) {
        scroll.scrollTo(0)
        if (scroll.maxValue <= 0 || tv.own.owntv.glcn.LowRam.on) return@LaunchedEffect // light mode: no animation
        while (true) {
            kotlinx.coroutines.delay(tv.own.owntv.glcn.DriftPace.START_MS.toLong())
            scroll.animateScrollTo(scroll.maxValue, androidx.compose.animation.core.tween(scroll.maxValue * tv.own.owntv.glcn.DriftPace.MS_PER_PX, easing = androidx.compose.animation.core.LinearEasing))
            kotlinx.coroutines.delay(3_000)
            // glcn: back to the start gently, not in one jump.
            scroll.animateScrollTo(0, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    // A little room under the last line, so its descenders are not cut where the drift ends.
    val soft = fade && scroll.maxValue > 0
    Box(
        modifier.fillMaxWidth().heightIn(max = lineH * lines + 6.dp).clip(androidx.compose.ui.graphics.RectangleShape)
            .then(
                if (soft) Modifier
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // glcn: once the words have drifted up, the line leaving at the top fades out too, never cut in half.
                        val top = if (scroll.value > 0) (lineH.toPx() / size.height).coerceIn(0f, 0.3f) else 0f
                        drawRect(
                            if (top > 0f) Brush.verticalGradient(0f to Color.Transparent, top to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent)
                            else Brush.verticalGradient(0.86f to Color.Black, 1f to Color.Transparent),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    }
                else Modifier,
            ),
    ) {
        Text(
            text, style = style.copy(lineHeight = style.fontSize * 1.4f), color = color,
            modifier = Modifier.verticalScroll(scroll, enabled = false).padding(bottom = if (soft) 18.dp else 6.dp),
        )
    }
}
