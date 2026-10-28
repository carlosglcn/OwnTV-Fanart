package tv.own.owntv.features.live

import tv.own.owntv.core.live.ProviderTags
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.features.home.timeLeftText
import tv.own.owntv.features.shell.components.VodCinematicBackdrop
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageLiveBadge
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.glcn.staggerAt
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageText

/*
 * glcn fork — Live TV "Cinematic" layout (Settings › Live TV layout › Cinematic).
 *
 * Top: the hero — the preview video on the left, the focused programme's picture (XMLTV
 * <programme><icon>) behind everything on the right, and its title, times, progress, details and
 * synopsis. Below: the channels as one row of picture cards (what is on now, LIVE, the channel, the
 * time and progress), each with the next two programmes under it.
 *
 * LiveScreen owns the state and the focus plumbing (the card modifiers carry its requesters and key
 * handling, the row shares its LazyListState), so restore / reveal / CH± keep working unchanged.
 */

private const val CARD_W = 400
private const val CARD_H = 225
private const val WINDOW_HOURS = 6L

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun LiveCinematicPane(
    channels: LazyPagingItems<ChannelEntity>,
    listState: LazyListState,
    previewChannel: ChannelEntity?,
    nowNext: EpgNowNext?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    singleSessionBlocked: Boolean,
    loadProgrammes: suspend (List<ChannelEntity>, Long, Long) -> Map<Long, List<EpgProgrammeEntity>>,
    loadSimilar: suspend (String?, String?, Int?, Long?, String?) -> List<tv.own.owntv.core.live.SimilarNow.Entry>,
    header: @Composable () -> Unit,
    cardModifier: (index: Int, channel: ChannelEntity) -> Modifier,
    onFocus: (ChannelEntity) -> Unit,
    onClick: (ChannelEntity) -> Unit,
    onLongClick: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The guide window for the channels around the visible cards, refreshed on the minute.
    var programmes by remember { mutableStateOf<Map<Long, List<EpgProgrammeEntity>>>(emptyMap()) }
    var loadedMinute by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            delay(60_000 - (t % 60_000))
            now = System.currentTimeMillis()
        }
    }
    val visible by remember(listState) {
        derivedStateOf {
            val items = listState.layoutInfo.visibleItemsInfo
            (items.firstOrNull()?.index ?: 0) to (items.lastOrNull()?.index ?: 0)
        }
    }
    LaunchedEffect(visible, channels.itemCount, now) {
        delay(120)
        val minute = now / 60_000
        val snapshot = channels.itemSnapshotList
        val (first, last) = visible
        val from = (first - 3).coerceAtLeast(0)
        val to = (last + 8).coerceAtMost(snapshot.size - 1)
        if (to < from) return@LaunchedEffect
        val want = (from..to).mapNotNull { snapshot.getOrNull(it) }.filter { loadedMinute[it.id] != minute }
        if (want.isEmpty()) return@LaunchedEffect
        val found = runCatching { loadProgrammes(want, now - 60_000, now + WINDOW_HOURS * 3_600_000) }.getOrNull()
            ?: return@LaunchedEffect
        programmes = programmes + want.associate { it.id to found[it.id].orEmpty() }
        loadedMinute = loadedMinute + want.associate { it.id to minute }
    }

    // glcn: "You may like" — what else is on soon in the focused programme's genre. Kept while the next
    // answer loads, so zapping along the row does not make the strip flicker.
    var similar by remember { mutableStateOf<List<tv.own.owntv.core.live.SimilarNow.Entry>>(emptyList()) }
    val focusTitle = nowNext?.now?.title
    val focusCategories = nowNext?.nowDetails?.categories
    val focusYear = nowNext?.nowDetails?.year
    LaunchedEffect(previewChannel?.id, focusTitle) {
        if (focusTitle == null) { similar = emptyList(); return@LaunchedEffect }
        delay(500)
        similar = runCatching { loadSimilar(focusTitle, focusCategories, focusYear, previewChannel?.id, previewChannel?.name) }.getOrDefault(emptyList())
    }
    val similarFirst = remember { androidx.compose.ui.focus.FocusRequester() }
    // ▲ from a suggestion goes back to the card of the channel the strip was built for.
    val returnTo = remember { androidx.compose.ui.focus.FocusRequester() }
    // The strip may have been scrolled, so its first card may not exist yet: scroll back, wait a frame, then focus it.
    val similarState = androidx.compose.foundation.lazy.rememberLazyListState()
    val enterScope = androidx.compose.runtime.rememberCoroutineScope()
    val enterSimilar: () -> Unit = {
        enterScope.launch {
            runCatching { similarState.scrollToItem(0) }
            androidx.compose.runtime.withFrameNanos { }
            runCatching { similarFirst.requestFocus() }
        }
        Unit
    }

    val focusedProgrammes = previewChannel?.let { programmes[it.id] }.orEmpty()
    val heroArt = nowNext?.nowDetails?.imageUrl
        ?: focusedProgrammes.firstOrNull { now in it.startMs until it.stopMs }?.imageUrl

    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        // glcn: with the menu on top the page is shorter (the bar takes its band): the hero moves up into the room
        // the chips no longer need, and the rows with it, so "You may like" is whole at the bottom.
        val short = (1080.mpx - maxHeight).coerceAtLeast(0.mpx)
        val heroTop = (100.mpx - short).coerceAtLeast(16.mpx)
        val rowsTop = 450.mpx - (100.mpx - heroTop)
        // The picture fills the top right and dissolves into the page, as Movies' Cinematic does.
        tv.own.owntv.ui.components.ArtTint(heroArt, Modifier.matchParentSize())
        VodCinematicBackdrop(heroArt, series = true, Modifier.align(Alignment.TopEnd))

        LiveCinematicHero(
            channel = previewChannel,
            nowNext = nowNext,
            art = heroArt,
            previewEngine = previewEngine,
            showVideo = showVideo,
            singleSessionBlocked = singleSessionBlocked,
            // glcn: low enough to clear the top bar's chips ("Last channel", the playlist picker).
            modifier = Modifier.padding(start = 64.mpx, top = heroTop, end = 64.mpx),
        )

        Column(Modifier.padding(top = rowsTop)) {
            Box(Modifier.padding(start = 64.mpx, end = 64.mpx)) { header() }
            Spacer(Modifier.height(18.mpx))
            if (channels.itemCount == 0) {
                Text(
                    stringResource(R.string.content_no_channels_here),
                    style = stageText(20, 500), color = StageColors.Muted,
                    modifier = Modifier.padding(start = 64.mpx, top = 40.mpx),
                )
            } else {
                val cardsGate = tv.own.owntv.glcn.rememberStaggerGate()
                CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides edgeScrollSpec) {
                    LazyRow(
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(26.mpx),
                        contentPadding = PaddingValues(start = 64.mpx, end = 64.mpx, top = 8.mpx, bottom = 6.mpx),
                        modifier = Modifier.fillMaxWidth().focusGroup(),
                    ) {
                        items(
                            count = channels.itemCount,
                            key = channels.itemKey { it.id },
                            contentType = channels.itemContentType { "card" },
                        ) { index ->
                            val channel = channels[index]
                            if (channel != null) {
                                val rows = programmes[channel.id].orEmpty()
                                val current = rows.firstOrNull { now in it.startMs until it.stopMs }
                                val next = rows.filter { it.startMs >= (current?.stopMs ?: now) }.take(1)
                                Column(Modifier.staggerAt(index, cardsGate, step = 70, cap = 7).width(CARD_W.mpx)) {
                                    LiveChannelCard(
                                        channel = channel,
                                        programme = current,
                                        now = now,
                                        onClick = { onClick(channel) },
                                        onLongClick = { onLongClick(channel) },
                                        modifier = Modifier
                                            .onDownToSimilar(similar.isNotEmpty(), enterSimilar)
                                            .then(if (channel.id == previewChannel?.id) Modifier.focusRequester(returnTo) else Modifier)
                                            .then(cardModifier(index, channel))
                                            .onFocusChanged { if (it.hasFocus) onFocus(channel) },
                                    )
                                    Spacer(Modifier.height(10.mpx))
                                    next.forEach { p ->
                                        UpNextBox(p)
                                        Spacer(Modifier.height(4.mpx))
                                    }
                                }
                            }
                        }
                    }
                }
                if (similar.isNotEmpty()) {
                    Text(
                        stringResource(R.string.glcn_similar_title), style = stageText(20, 700), color = StageColors.Text,
                        modifier = Modifier.padding(start = 64.mpx, top = 2.mpx),
                    )
                    LazyRow(
                        state = similarState,
                        horizontalArrangement = Arrangement.spacedBy(18.mpx),
                        contentPadding = PaddingValues(start = 64.mpx, end = 64.mpx, top = 6.mpx, bottom = 24.mpx),
                        modifier = Modifier.fillMaxWidth().focusGroup(),
                    ) {
                        items(count = similar.size, key = { similar[it].programme.id }) { i ->
                            val e = similar[i]
                            SimilarCard(
                                entry = e,
                                now = now,
                                onClick = { onClick(e.channel) },
                                modifier = (if (i == 0) Modifier.focusRequester(similarFirst) else Modifier)
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                                            ev.key == androidx.compose.ui.input.key.Key.DirectionUp
                                        ) { runCatching { returnTo.requestFocus() }.isSuccess } else false
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** glcn: Down from a channel card goes to the "You may like" strip when there is one. */
private fun Modifier.onDownToSimilar(has: Boolean, enter: () -> Unit): Modifier =
    androidx.compose.ui.Modifier.onPreviewKeyEvent { e ->
        if (has && e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
            e.key == androidx.compose.ui.input.key.Key.DirectionDown
        ) { enter(); true } else false
    }.then(this)

private const val SIM_W = 250
private const val SIM_H = 122

/** glcn: one suggestion — the programme's picture with its title, then the channel and when it is on, over the picture. */
@Composable
private fun SimilarCard(
    entry: tv.own.owntv.core.live.SimilarNow.Entry,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatTime = rememberSystemTimeFormatter()
    val p = entry.programme
    val shape = RoundedCornerShape(12.mpx)
    StageSurface(
        onClick = onClick,
        radius = 12.mpx,
        focusStyle = StageFocus.POSTER,
        modifier = modifier.size(SIM_W.mpx, SIM_H.mpx),
        contentAlignment = Alignment.TopStart,
    ) { _ ->
        Box(Modifier.fillMaxSize().clip(shape).background(Color(0xFF15191C))) {
            if (!p.imageUrl.isNullOrBlank()) {
                tv.own.owntv.ui.components.ProgrammeArt(url = p.imageUrl!!, modifier = Modifier.fillMaxSize())
            } else {
                ChannelLogoTile(
                    logoUrl = entry.channel.displayLogoUrl,
                    modifier = Modifier.align(Alignment.Center).size(110.mpx, 70.mpx),
                    fill = Color.Transparent,
                ) { OwnTVIcon(OwnTVIcon.LIVE_TV, tint = StageColors.Dim, modifier = Modifier.size(34.mpx)) }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.3f to Color.Transparent, 0.7f to Color.Black.copy(alpha = 0.72f), 1f to Color.Black.copy(alpha = 0.92f)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 12.mpx, end = 12.mpx, bottom = 8.mpx)) {
                Text(
                    tv.own.owntv.core.live.SportsNow.displayTitle(p.title), style = stageText(16, 700), color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ProviderTags.parse(entry.channel.name).name + stringResource(R.string.content_epg_bits_separator) +
                        if (p.startMs <= now) stringResource(R.string.glcn_similar_now) else formatTime(p.startMs),
                    style = stageText(13, 600), color = Color(0xFFD5DDDA), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The hero: the preview video, then the programme's title, channel, time, progress, details and synopsis. */
@Composable
private fun LiveCinematicHero(
    channel: ChannelEntity?,
    nowNext: EpgNowNext?,
    art: String?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    singleSessionBlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(44.mpx)) {
        // glcn: the box is for the video. Without one the backdrop already shows the picture, so a
        // second copy of it here only repeated it — the words take the room instead.
        if (showVideo) {
            LiveCinematicVideo(
                channel = channel,
                art = null,
                previewEngine = previewEngine,
                showVideo = showVideo,
                singleSessionBlocked = singleSessionBlocked,
                modifier = Modifier.size(560.mpx, 315.mpx),
            )
        }
        val now = nowNext?.now
        // glcn: a soft wash of the page colour behind the words, strongest where they start and gone past their end,
        // so title and synopsis read over a bright picture (the picture itself fades in only further right).
        val wash = StageColors.Wash
        Column(
            Modifier.width(if (showVideo) 1000.mpx else 1100.mpx).padding(top = 6.mpx)
                .drawBehind {
                    val pad = 28.mpx.toPx()
                    drawRect(
                        Brush.horizontalGradient(
                            0f to wash.copy(alpha = 0.82f), 0.62f to wash.copy(alpha = 0.66f), 1f to wash.copy(alpha = 0f),
                            startX = -pad, endX = size.width + pad,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(-pad, -pad),
                        size = androidx.compose.ui.geometry.Size(size.width + 2 * pad, size.height + 2 * pad),
                    )
                },
        ) {
            if (channel != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    LivePlate(channel.displayLogoUrl, 72.mpx, 52.mpx)
                    Column {
                        Text(
                            ProviderTags.parse(channel.name).name, style = stageText(22, 700), color = StageColors.Text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        channel.number?.let {
                            Text(it.toString(), style = stageText(16, 600), color = StageColors.Muted, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(16.mpx))
            }
            if (now != null) {
                Text(
                    tv.own.owntv.core.live.SportsNow.displayTitle(now.title), style = stageText(44, 800, (-0.5).mpxSp), color = StageColors.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        detailsLine(now, nowNext.nowDetails).takeIf { it.isNotBlank() },
                        tv.own.owntv.ui.components.rememberTmdbRating(now.title, nowNext.nowDetails?.categories),
                    ).joinToString("  ·  "),
                    style = stageText(19, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.mpx, bottom = 14.mpx),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    StageProgress(progressOf(now), Modifier.width(420.mpx))
                    Text(timeLeftText(minutesLeft(now)), style = stageText(17, 500), color = StageColors.Muted, maxLines = 1)
                }
                now.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it, style = stageText(19, 400).copy(lineHeight = (19 * 1.5f).mpxSp), color = StageColors.Text,
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 16.mpx).widthIn(max = 820.mpx),
                    )
                }
            }
        }
    }
}

/** The 16:9 preview: the programme's picture (or the logo) until the video plays over it. */
@Composable
private fun LiveCinematicVideo(
    channel: ChannelEntity?,
    art: String?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    singleSessionBlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by previewEngine.state.collectAsStateWithLifecycle()
    val playing = showVideo && state != tv.own.owntv.player.LivePreviewEngine.State.ERROR &&
        state != tv.own.owntv.player.LivePreviewEngine.State.IDLE
    val loading = showVideo && state == tv.own.owntv.player.LivePreviewEngine.State.LOADING
    Box(
        modifier.clip(RoundedCornerShape(24.mpx)).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) tv.own.owntv.player.ExoPreviewSurface(engine = previewEngine, modifier = Modifier.fillMaxSize())
        // glcn: the picture shows until the video plays and while a new channel loads, not a black square.
        if (!playing || loading) {
            if (!art.isNullOrBlank()) {
                tv.own.owntv.ui.components.ProgrammeArt(url = art, modifier = Modifier.fillMaxSize())
            } else if (channel != null) {
                if (loading) Box(Modifier.fillMaxSize().background(Color.Black))
                ChannelLogoTile(logoUrl = channel.displayLogoUrl, modifier = Modifier.size(220.mpx), fill = Color.Transparent) {
                    OwnTVIcon(OwnTVIcon.LIVE_TV, tint = StageColors.Dim, modifier = Modifier.size(56.mpx))
                }
            }
        }
        if (loading) OwnTVSpinner(sizeDp = 28)
        if (channel != null) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 18.mpx, top = 16.mpx)) { StageLiveBadge(stringResource(R.string.player_live)) }
        }
        if (singleSessionBlocked && !playing) {
            Text(
                stringResource(R.string.content_preview_single_stream),
                style = stageText(15, 700), color = Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.mpx)).padding(horizontal = 12.mpx, vertical = 6.mpx),
            )
        }
    }
}

/** One channel: the picture of what is on, LIVE, the logo, the title, the channel, the time and progress. */
@Composable
internal fun LiveChannelCard(
    channel: ChannelEntity,
    programme: EpgProgrammeEntity?,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatTime = rememberSystemTimeFormatter()
    val shape = RoundedCornerShape(18.mpx)
    StageSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        radius = 18.mpx,
        focusStyle = StageFocus.POSTER,
        modifier = modifier.size(CARD_W.mpx, CARD_H.mpx),
        contentAlignment = Alignment.TopStart,
    ) { focused ->
        Box(Modifier.fillMaxSize().clip(shape).background(Color(0xFF15191C))) {
            val art = programme?.imageUrl
            if (!art.isNullOrBlank()) {
                tv.own.owntv.ui.components.ProgrammeArt(url = art, modifier = Modifier.fillMaxSize())
            } else {
                ChannelLogoTile(
                    logoUrl = channel.displayLogoUrl,
                    modifier = Modifier.align(Alignment.Center).size(150.mpx, 100.mpx),
                    fill = Color.Transparent,
                ) { OwnTVIcon(OwnTVIcon.LIVE_TV, tint = StageColors.Dim, modifier = Modifier.size(44.mpx)) }
            }
            // Bottom shade so the text reads on any picture.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.25f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.7f), 1f to Color.Black.copy(alpha = 0.95f)),
                ),
            )
            // glcn: the focused card's picture is the backdrop already — its logo steps forward instead.
            Box(Modifier.align(Alignment.TopStart).padding(12.mpx)) {
                if (focused) LivePlate(channel.displayLogoUrl, 96.mpx, 66.mpx) else LivePlate(channel.displayLogoUrl, 64.mpx, 44.mpx)
            }
            // glcn: every channel here is on air; the badge is kept for real live events (a match, a gala).
            if (programme != null && tv.own.owntv.core.live.SportsNow.isLive(programme)) {
                Box(Modifier.align(Alignment.TopEnd).padding(12.mpx)) { StageLiveBadge(stringResource(R.string.player_live)) }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.mpx, end = 16.mpx, bottom = 12.mpx)) {
                Text(
                    programme?.title?.let(tv.own.owntv.core.live.SportsNow::displayTitle) ?: ProviderTags.parse(channel.name).name,
                    style = stageText(21, 800), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (programme != null) ProviderTags.parse(channel.name).name else stringResource(R.string.content_no_epg),
                    style = stageText(15, 600), color = Color(0xFFD5DDDA),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (programme != null) {
                    Text(
                        stringResource(R.string.content_live_time_range_plain, formatTime(programme.startMs), formatTime(programme.stopMs)),
                        style = stageText(15, 600), color = Color(0xFFD5DDDA), maxLines = 1,
                    )
                    Spacer(Modifier.height(6.mpx))
                    val span = (programme.stopMs - programme.startMs).coerceAtLeast(1)
                    StageProgress(((now - programme.startMs).toFloat() / span).coerceIn(0f, 1f), Modifier.fillMaxWidth(), flat = true)
                }
            }
        }
    }
}

/** What follows, under a card: "13:16 – 14:46 · 90 min", the title, then the details the feed carries. */
@Composable
private fun UpNextBox(p: EpgProgrammeEntity, width: Dp = CARD_W.mpx) {
    val formatTime = rememberSystemTimeFormatter()
    val minutes = ((p.stopMs - p.startMs) / 60_000).toInt()
    val details = p.details
    val bits = listOfNotNull(
        details.year?.toString(),
        details.rating,
    ) + details.categoryList.take(2)
    Column(
        Modifier
            .width(width)
            .background(StageColors.Ink.copy(alpha = 0.06f), RoundedCornerShape(12.mpx))
            .padding(horizontal = 14.mpx, vertical = 9.mpx),
    ) {
        Text(
            stringResource(R.string.content_live_time_range_plain, formatTime(p.startMs), formatTime(p.stopMs)) +
                stringResource(R.string.content_epg_bits_separator) + stringResource(R.string.player_duration_minutes, minutes),
            style = stageText(14, 600), color = StageColors.Muted, maxLines = 1,
        )
        Text(p.title, style = stageText(17, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (bits.isNotEmpty()) {
            Text(bits.joinToString(stringResource(R.string.content_epg_bits_separator)), style = stageText(14, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


/**
 * glcn: "Sports now" — every channel showing sport at this moment, as picture cards (live events
 * first). OK plays the channel full screen.
 */
@Composable
internal fun SportsNowDialog(
    load: suspend () -> List<tv.own.owntv.core.live.SportsNow.Entry>,
    onPick: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.activity.compose.BackHandler { onDismiss() }
    var entries by remember { mutableStateOf<List<tv.own.owntv.core.live.SportsNow.Entry>?>(null) }
    LaunchedEffect(Unit) { entries = runCatching { load() }.getOrDefault(emptyList()) }
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(entries) {
        if (!entries.isNullOrEmpty()) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
    val now = System.currentTimeMillis()
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.86f))
                .padding(start = 64.mpx, end = 64.mpx, top = 48.mpx),
        ) {
            Column {
                Text(stringResource(R.string.glcn_sports_now), style = stageText(40, 800), color = Color.White)
                Spacer(Modifier.height(24.mpx))
                val list = entries
                when {
                    list == null -> Box(Modifier.fillMaxWidth().height(300.mpx), contentAlignment = Alignment.Center) {
                        OwnTVSpinner(sizeDp = 32)
                    }
                    list.isEmpty() -> Text(
                        stringResource(R.string.glcn_sports_none), style = stageText(22, 500), color = StageColors.Muted,
                    )
                    else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(26.mpx),
                        verticalArrangement = Arrangement.spacedBy(26.mpx),
                        contentPadding = PaddingValues(top = 12.mpx, bottom = 48.mpx),
                        modifier = Modifier.fillMaxSize().focusGroup(),
                    ) {
                        items(list.size, key = { list[it].channel.id }) { i ->
                            val e = list[i]
                            LiveChannelCard(
                                channel = e.channel,
                                programme = e.programme,
                                now = now,
                                onClick = { onPick(e.channel, list.map { it.channel }); onDismiss() },
                                onLongClick = {},
                                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}
