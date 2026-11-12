package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import tv.own.owntv.glcn.playingMark
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import tv.own.owntv.core.epg.displayLogoUrl
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.features.home.relativeLastWatchedLabel
import tv.own.owntv.features.live.LiveStageRow
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.jumpLazyListTo
import tv.own.owntv.ui.stage.PlaylistMark
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.stage.stageSelectedBar
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * A channel list that slides in over the playing video, as a Stage glass sheet 20 from the screen edge.
 * Two instances exist in the player: Left opens the playing channel's own provider category (anchored
 * left), Right opens the profile's watch history (anchored right, [watchedAt] set). Rows are the Live TV
 * list's own [LiveStageRow]; the playing channel carries the accent bar. Browse with the D-pad, OK
 * switches channel, Back — or pushing outwards past the list edge — closes it, all without leaving
 * full-screen. The current channel is focused first; in the history list it is the newest row, so focus
 * starts on the row after it and OK there really changes channel.
 * The remote's paging keys (CH+/CH−, rewind/fast-forward by default) page through it as in the Live list.
 */
@Composable
fun ChannelListOverlay(
    channels: List<ChannelEntity>,
    currentId: Long?,
    onSelect: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    nowProgrammes: Map<Long, XtEpgEntry> = emptyMap(),
    nowPlaying: Map<Long, String> = emptyMap(),
    favoriteIds: Set<Long> = emptySet(),
    playlistMarks: Map<Long, PlaylistMark> = emptyMap(),
    title: String? = null,
    alignEnd: Boolean = false,
    showNumbers: Boolean = true,
    /** History only: channel id → when it was last watched, shown at the row's end. */
    watchedAt: Map<Long, Long>? = null,
    onOpenCategories: (() -> Unit)? = null,
    /** glcn: the focused channel's programme on air, for the picture panel beside the list. */
    loadNow: (suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>)? = null,
    /** glcn: the focused channel's coming schedule; when set it takes the picture panel's place, right beside the list. */
    loadSchedule: (suspend (ChannelEntity) -> List<EpgProgrammeEntity>)? = null,
    /** glcn: ▶ steps the view — picture, schedule, posters — remembered for next time. */
    onCyclePanel: (() -> Unit)? = null,
) {
    // glcn: what is on each channel, kept once read (the prefetch reads the rows around the cursor), so the panel
    // beside the list shows the picture at once instead of reading it again when the row is reached.
    val nowCache = remember { java.util.concurrent.ConcurrentHashMap<Long, Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>>() }
    val cachedNow: (suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>)? = remember(loadNow) {
        loadNow?.let { raw -> { ch: ChannelEntity -> nowCache[ch.id] ?: raw(ch).also { nowCache[ch.id] = it } } }
    }
    val layoutDirection = LocalLayoutDirection.current
    val dismissDirection = if (alignEnd) HorizontalDirection.END else HorizontalDirection.START
    val history = watchedAt != null
    val playingIndex = remember(channels, currentId) { channels.indexOfFirst { it.id == currentId } }
    // glcn: Recent opens on the channel playing (the top one), as every list does — it used to jump to the one before.
    val currentIndex = remember(channels, playingIndex) { playingIndex.coerceAtLeast(0) }
    // glcn: opens already on the current channel — scrolling there after the first frame showed the top for a blink.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    val focusTarget = remember { FocusRequester() }
    // The row that owns [focusTarget]: the current channel at first, then wherever paging jumped to.
    var targetIndex by remember(currentIndex) { mutableIntStateOf(currentIndex) }
    var focusedIndex by remember(currentIndex) { mutableIntStateOf(currentIndex) }
    // glcn: the coming programme picked beside the list (-1 = none), and how many there are; a new channel starts again.
    var schedPick by remember { mutableIntStateOf(-1) }
    var schedCount by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(focusedIndex) { schedPick = -1 }
    var listFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        runCatching { listState.scrollToItem(currentIndex) }
        runCatching { focusTarget.requestFocus() }
    }
    BackHandler { onDismiss() }

    val accent = stageAccent.accent
    val shade = tv.own.owntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    // glcn: the list glides in from its own side and the shade fades up with it (not in light mode).
    val enterAnim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (tv.own.owntv.glcn.LowRam.on) 1f else 0f) }
    val enterSpec = tv.own.owntv.ui.theme.ownTvTween<Float>(OVERLAY_ENTER_MS, androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
    androidx.compose.runtime.LaunchedEffect(Unit) { enterAnim.animateTo(1f, enterSpec) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .okAfterFreshPress()
            .graphicsLayer { alpha = 0.2f + 0.8f * enterAnim.value }
            // Darken only the side the sheet is on, so the picture stays visible beside it.
            .gradientWash(
                vertical = false,
                *(if (alignEnd) arrayOf(0.38f to Color.Transparent, 0.62f to shade.copy(alpha = 0.45f), 1f to shade.copy(alpha = 0.72f))
                else arrayOf(0f to shade.copy(alpha = 0.72f), 0.38f to shade.copy(alpha = 0.45f), 0.62f to Color.Transparent)),
            ),
    ) {
        // glcn: with the schedule beside it, list and schedule are one piece of glass, not two bubbles.
        val unified = loadSchedule != null && !alignEnd
        Row(
            Modifier
                .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
                .graphicsLayer { translationX = (1f - enterAnim.value) * OVERLAY_ENTER_PX.mpx.toPx() * (if (alignEnd) 1f else -1f) }
                .padding(horizontal = 14.mpx, vertical = 8.mpx)
                .fillMaxHeight()
                .then(if (unified) Modifier.stageGlass(StageRadii.Sheet, overContent = true, share = tv.own.owntv.glcn.PlayerGlass.share(tv.own.owntv.features.live.PLAYER_SHEET_GLASS)) else Modifier),
        ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (history) 760.mpx else 720.mpx)
                .then(if (unified) Modifier else Modifier.stageGlass(StageRadii.Sheet, overContent = true, share = tv.own.owntv.glcn.PlayerGlass.share(tv.own.owntv.features.live.PLAYER_SHEET_GLASS)))
                .onPreviewKeyEvent { e ->
                    // glcn: ▶ steps into the coming programmes beside the list; ▲▼ walk them, ◀ comes back,
                    // ▶ again changes the view as before.
                    if (unified && loadSchedule != null && e.type == KeyEventType.KeyDown) {
                        val dir = e.key.horizontalDirection(layoutDirection)
                        if (schedPick < 0 && dir == HorizontalDirection.END && schedCount > 0 && !alignEnd) { schedPick = 0; return@onPreviewKeyEvent true }
                        if (schedPick >= 0) {
                            when {
                                dir == HorizontalDirection.START -> { schedPick = -1; return@onPreviewKeyEvent true }
                                e.key == Key.DirectionUp -> { schedPick = (schedPick - 1).coerceAtLeast(0); return@onPreviewKeyEvent true }
                                e.key == Key.DirectionDown -> { schedPick = (schedPick + 1).coerceAtMost((schedCount - 1).coerceAtLeast(0)); return@onPreviewKeyEvent true }
                                dir == HorizontalDirection.END -> schedPick = -1
                            }
                        }
                    }
                    if (onCyclePanel != null && !alignEnd && e.type == KeyEventType.KeyDown &&
                        e.key.horizontalDirection(layoutDirection) == HorizontalDirection.END
                    ) {
                        onCyclePanel()
                        return@onPreviewKeyEvent true
                    }
                    if (e.type == KeyEventType.KeyDown && e.key.horizontalDirection(layoutDirection) == dismissDirection) {
                        // Pushing outward from the Start panel opens categories when they are wired;
                        // pushing outward from the End panel closes it. Back always closes.
                        if (!alignEnd && onOpenCategories != null) onOpenCategories() else onDismiss()
                        true
                    } else false
                }
                .padding(horizontal = 16.mpx, vertical = 20.mpx),
        ) {
            // "Catch-up · 978 channels        Live TV", as the Live TV header and category sheet have it.
            Row(
                Modifier.fillMaxWidth().padding(start = 14.mpx, end = 14.mpx),
                horizontalArrangement = Arrangement.spacedBy(16.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title ?: stringResource(R.string.content_channel_overlay_title),
                        style = stageText(26, 800), color = StageColors.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "· " + pluralStringResource(R.plurals.content_live_channel_count, channels.size, channels.size),
                        style = stageText(18, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(stringResource(R.string.common_nav_live_tv), style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val playing = channels.getOrNull(playingIndex)
            val subLine = when {
                history -> stringResource(R.string.content_history_overlay_hint)
                playing != null -> listOfNotNull(
                    stringResource(R.string.shell_now_playing),
                    playing.number?.toString()?.takeIf { showNumbers },
                    ProviderTags.parse(playing.name).name,
                ).joinToString(" · ")
                else -> null
            }
            subLine?.let {
                Text(
                    it, style = stageText(16, 400), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.mpx, end = 14.mpx, top = 4.mpx, bottom = 12.mpx),
                )
            }
            androidx.compose.runtime.DisposableEffect(Unit) { tv.own.owntv.glcn.GlassFocus.overVideo++; onDispose { tv.own.owntv.glcn.GlassFocus.overVideo-- } }
            Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { listFocused = it.hasFocus }
                    // glcn: wrap around, ▲ on the first channel goes to the last and ▼ on the last to the first.
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown || channels.size < 2) return@onPreviewKeyEvent false
                        val to = when {
                            e.key == Key.DirectionUp && focusedIndex == 0 -> channels.lastIndex
                            e.key == Key.DirectionDown && focusedIndex == channels.lastIndex -> 0
                            else -> return@onPreviewKeyEvent false
                        }
                        targetIndex = to
                        scope.jumpLazyListTo(listState, to) { focusTarget.requestFocus() }
                        true
                    }
                    .chNavPaging(
                        enabled = chNavEnabled,
                        upSkip = chNavUpSkip,
                        downSkip = chNavDownSkip,
                        isFocused = { listFocused },
                        lastIndex = { channels.lastIndex },
                        currentTargetIndex = { focusedIndex },
                        onJumpToIndex = { idx ->
                            targetIndex = idx
                            scope.jumpLazyListTo(listState, idx) { focusTarget.requestFocus() }
                        },
                    ),
                // Room for the focus ring, which the list would otherwise clip.
                contentPadding = PaddingValues(horizontal = 4.mpx, vertical = 4.mpx),
                verticalArrangement = Arrangement.spacedBy(2.mpx),
            ) {
                // glcn: every channel from one playlist → no playlist mark on each row (as on TV en directo).
                val oneSource = channels.asSequence().map { it.sourceId }.distinct().take(2).count() <= 1
                itemsIndexed(channels, key = { _, ch -> ch.id }) { index, ch ->
                    val isPlaying = index == playingIndex
                    val nowMs = remember { System.currentTimeMillis() }
                    LiveStageRow(
                        channel = ch,
                        name = ProviderTags.parse(ch.name),
                        now = nowProgrammes[ch.id],
                        nowTitle = nowPlaying[ch.id],
                        isFavorite = ch.id in favoriteIds,
                        showNumber = showNumbers,
                        mark = if (oneSource) null else playlistMarks[ch.sourceId],
                        onFocus = { focusedIndex = index },
                        onClick = { onSelect(ch) },
                        onLongClick = {},
                        logoTile = false,
                        trailing = watchedAt?.let { times ->
                            {
                                Text(
                                    if (isPlaying) stringResource(R.string.shell_now_playing)
                                    else times[ch.id]?.let { relativeLastWatchedLabel(it, nowMs) }.orEmpty(),
                                    style = stageText(15, if (isPlaying) 700 else 500),
                                    color = if (isPlaying) accent else StageColors.Dim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        modifier = Modifier
                            .playingMark(isPlaying)
                            .then(if (index == targetIndex) Modifier.focusRequester(focusTarget) else Modifier),
                    )
                }
            }
            // glcn: the watched channel scrolled out of sight: a pill at the edge it went past.
            val watchSide by remember(listState, playingIndex) {
                androidx.compose.runtime.derivedStateOf {
                    val vis = listState.layoutInfo.visibleItemsInfo
                    when {
                        playingIndex < 0 || vis.isEmpty() -> 0
                        playingIndex < vis.first().index -> 1
                        playingIndex > vis.last().index -> 2
                        else -> 0
                    }
                }
            }
            val watchName = channels.getOrNull(playingIndex)?.let { ProviderTags.parse(it.name).name }.orEmpty()
            androidx.compose.animation.AnimatedVisibility(watchSide == 1, Modifier.align(Alignment.TopCenter).padding(top = 6.mpx), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) { tv.own.owntv.glcn.WatchingPill(watchName, true) }
            androidx.compose.animation.AnimatedVisibility(watchSide == 2, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.mpx), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) { tv.own.owntv.glcn.WatchingPill(watchName, false) }
            }
            StageKeyHints(
                listOfNotNull(
                    stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
                    // Outward is ◀ on the left sheet, ▶ on the right one (Multiview's picker goes back to categories).
                    when {
                        onOpenCategories != null -> (if (alignEnd) "▶" else "◀") to stringResource(R.string.content_category_browser_title)
                        alignEnd -> "▶" to stringResource(R.string.content_close)
                        else -> null
                    },
                    "CH±" to stringResource(R.string.content_key_page),
                    when {
                        unified && loadSchedule != null && !alignEnd && schedPick < 0 && schedCount > 0 -> "▶" to stringResource(R.string.content_key_programmes)
                        onCyclePanel != null && !alignEnd -> "▶" to stringResource(R.string.glcn_zap_view_next)
                        else -> null
                    },
                    stringResource(R.string.common_back) to stringResource(R.string.content_close),
                ),
                textSize = 16,
                modifier = Modifier.padding(start = 14.mpx, top = 14.mpx),
            )
        }
        if (loadNow != null) NeighbourArtPrefetch(channels, focusedIndex, cachedNow!!)
        // glcn: the schedules of the channels around the cursor are read ahead, so the column beside the list is not late.
        if (loadSchedule != null) {
            LaunchedEffect(focusedIndex, channels.size) {
                kotlinx.coroutines.delay(250)
                for (d in buildList { for (k in 1..8) { add(k); add(-k) } }) {
                    channels.getOrNull(focusedIndex + d)?.let { runCatching { loadSchedule(it) } }
                }
            }
        }
        if (unified && loadSchedule != null) {
            ZapSchedulePanel(
                channel = channels.getOrNull(focusedIndex),
                loadSchedule = loadSchedule,
                loadNow = cachedNow, peek = { nowCache[it.id] },
                joined = true,
                selected = schedPick,
                onRowCount = { schedCount = it },
                modifier = Modifier,
            )
        }
        }
        if (loadSchedule != null && !unified) {
            ZapSchedulePanel(
                channel = channels.getOrNull(focusedIndex),
                loadSchedule = loadSchedule,
                modifier = Modifier
                    .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(vertical = 22.mpx)
                    .padding(
                        start = if (alignEnd) 0.mpx else (20 + (if (history) 760 else 720) + 4).mpx,
                        end = if (alignEnd) (20 + (if (history) 760 else 720) + 4).mpx else 0.mpx,
                    ),
            )
        } else if (loadSchedule == null && loadNow != null) {
            // The joined schedule view carries its own programme card; a second one would double it.
            ZapPreviewPanel(
                channel = channels.getOrNull(focusedIndex),
                loadNow = cachedNow!!, peek = { nowCache[it.id] },
                modifier = Modifier
                    .align(if (alignEnd) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 40.mpx),
            )
        }
    }
}

/**
 * glcn: the focused channel's programme on air — picture, title, time and synopsis — opposite the
 * zapping list, so browsing shows what is on before switching. Waits a moment while the D-pad is
 * still moving so a fast scroll does not fetch every row.
 */
@Composable
internal fun ZapPreviewPanel(
    channel: ChannelEntity?,
    loadNow: suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>,
    modifier: Modifier = Modifier,
    /** glcn: false inside the joined panel — no glass or width of its own, a wider, shorter picture. */
    framed: Boolean = true,
    /** glcn: the picture's shape when not framed (the zap deck keeps 16:9). */
    bareAspect: Float = 21f / 9f,
    /** glcn: synopsis lines when not framed. */
    bareLines: Int = 2,
    /** glcn: an already-read programme for the channel, shown in the same frame instead of after a read. */
    peek: ((ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>?)? = null,
    /** glcn: a coming programme (and the one after it) picked in the schedule, shown instead of what is on. */
    showing: Pair<EpgProgrammeEntity, EpgProgrammeEntity?>? = null,
    /** glcn: false where what follows is already listed beside it (the joined schedule). */
    showNext: Boolean = true,
    /** glcn: false where the channel's own picture already plays beside it (the zap deck's squeeze-back). */
    picture: Boolean = true,
) {
    // glcn: the previous channel's programme goes at once — a stale poster for seconds was worse than none.
    var programme by remember(channel?.id) { mutableStateOf(channel?.let { peek?.invoke(it) }) }
    LaunchedEffect(channel?.id) {
        if (channel == null || programme != null) return@LaunchedEffect
        programme = runCatching { loadNow(channel) }.getOrNull()
    }
    val p = showing?.first ?: programme?.first ?: return
    val next = if (showing != null) showing.second else programme?.second
    val fmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    Column(
        modifier
            .then(if (framed) Modifier.width(620.mpx).stageGlass(StageRadii.Sheet, overContent = true, share = tv.own.owntv.glcn.PlayerGlass.share(tv.own.owntv.features.live.PLAYER_SHEET_GLASS)).padding(18.mpx) else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.mpx),
    ) {
        val logo = channel?.displayLogoUrl?.takeIf { it.isNotBlank() }
        val art = tv.own.owntv.ui.components.programmeArtOrTmdb(p)
        // glcn: the programme's picture; a "picture" that is really a logo (squarish, small, or the channel's own logo),
        // and a channel with no picture at all, show the logo whole and sharp in the middle of the same frame —
        // not cropped into a blurred strip, nor floating faint in a short box.
        val shape = if (framed) 16f / 9f else bareAspect
        val pic = art ?: logo
        if (pic != null && picture) PreviewPicture(url = pic, logoLike = art == null || art == logo, aspect = shape)
        Text(p.title, style = stageText(24, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            fmt.format(java.util.Date(p.startMs)) + " – " + fmt.format(java.util.Date(p.stopMs)),
            style = stageText(16, 500), color = StageColors.Muted, maxLines = 1,
        )
        // glcn: how far through it is.
        val span = (p.stopMs - p.startMs).coerceAtLeast(1L)
        val f = ((System.currentTimeMillis() - p.startMs).toFloat() / span).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(4.mpx).clip(RoundedCornerShape(2.mpx)).background(StageColors.Ink.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(f).height(4.mpx).background(stageAccent.accent))
        }
        p.description?.takeIf { it.isNotBlank() }?.let {
            DriftingSynopsis(it, style = stageText(16, 400), color = StageColors.Body, lines = if (framed) 4 else bareLines)
        }
        // glcn: and what follows it.
        next?.takeIf { showNext }?.let { n ->
            Text(
                stringResource(R.string.content_live_next) + " · " + fmt.format(java.util.Date(n.startMs)) + "  " + n.title,
                style = stageText(16, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.mpx),
            )
        }
    }
}

/**
 * glcn: the focused channel's coming schedule in a column right beside the list — now, then the next
 * hours — so browsing reads like a small guide. Waits a moment while the D-pad is still moving.
 */
@Composable
internal fun ZapSchedulePanel(
    channel: ChannelEntity?,
    loadSchedule: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    modifier: Modifier = Modifier,
    /** glcn: the programme on air with its picture, shown under the schedule in the joined panel. */
    loadNow: (suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>)? = null,
    /** glcn: part of the list's own glass, not a panel of its own: a thin line on its left, no box. */
    joined: Boolean = false,
    /** glcn: an already-read programme for the card under the schedule (see [ZapPreviewPanel]). */
    peek: ((ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>?)? = null,
    /** glcn: the coming programme picked with the D-pad (-1 = none): lit, and shown in the card below. */
    selected: Int = -1,
    /** glcn: told how many programmes the schedule holds, so the picker stays inside it. */
    onRowCount: (Int) -> Unit = {},
    /** glcn: little height (under the zap deck's picture): the programme card without its picture, a shorter synopsis. */
    compact: Boolean = false,
) {
    var rows by remember { mutableStateOf<List<EpgProgrammeEntity>>(emptyList()) }
    LaunchedEffect(channel?.id) {
        if (channel == null) { rows = emptyList(); onRowCount(0); return@LaunchedEffect }
        rows = runCatching { loadSchedule(channel) }.getOrDefault(emptyList())
        onRowCount(rows.size)
    }
    // glcn: the schedule's rows are the guide's light ones (no synopsis); the coming programmes are read whole in the
    // background — synopsis and picture — and their pictures fetched, so picking one with ▶ ▲ ▼ shows it at once.
    val guideReader: tv.own.owntv.core.live.GuideReader = org.koin.compose.koinInject()
    val full = remember(channel?.id) { androidx.compose.runtime.mutableStateMapOf<Long, EpgProgrammeEntity>() }
    val prefetchCtx = androidx.compose.ui.platform.LocalContext.current
    if (joined) LaunchedEffect(channel?.id, rows) {
        if (channel == null) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        val loader = coil3.SingletonImageLoader.get(prefetchCtx)
        for (row in rows.take(8)) {
            if (row.startMs in full) continue
            // The guide's window leaves synopses out; each one is read on its own, by the programme's id.
            val whole = if (!row.description.isNullOrBlank()) row
                else row.copy(description = runCatching { guideReader.description(row.id) }.getOrNull())
            full[row.startMs] = whole
            whole.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                loader.enqueue(coil3.request.ImageRequest.Builder(prefetchCtx).data(url).size(coil3.size.Size(640, 360)).build())
            }
        }
    }
    val ch = channel ?: return
    if (rows.isEmpty()) return
    val fmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val now = remember(rows) { System.currentTimeMillis() }
    val accent = stageAccent.accent
    val line = StageColors.Ink.copy(alpha = 0.12f)
    Column(
        modifier
            .fillMaxHeight()
            .then(if (compact) Modifier else Modifier.width(560.mpx))
            .then(
                if (compact) Modifier
                else if (joined) Modifier.drawBehind {
                    val inset = 26.mpx.toPx()
                    drawLine(line, androidx.compose.ui.geometry.Offset(0f, inset), androidx.compose.ui.geometry.Offset(0f, size.height - inset), strokeWidth = 1.mpx.toPx())
                } else Modifier.stageGlass(StageRadii.Sheet, overContent = true, share = tv.own.owntv.glcn.PlayerGlass.share(tv.own.owntv.features.live.PLAYER_SHEET_GLASS)),
            )
            .padding(horizontal = if (compact) 0.mpx else 22.mpx, vertical = if (compact) 4.mpx else 20.mpx),
        verticalArrangement = Arrangement.spacedBy(5.mpx),
    ) {
        Text(
            stringResource(R.string.glcn_zap_schedule_title, ProviderTags.parse(ch.name).name),
            style = stageText(22, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 12.mpx),
        )
        // glcn: joined — the programme first (picture, title, time, synopsis), then the evening under it; no gap.
        if (joined && loadNow != null) {
            val pick = rows.getOrNull(selected)
            ZapPreviewPanel(
                channel = channel, loadNow = loadNow, modifier = Modifier.fillMaxWidth().padding(bottom = 14.mpx), framed = false, peek = peek,
                bareAspect = 16f / 9f,
                bareLines = if (compact) 2 else 5,
                showing = pick?.let { (full[it.startMs] ?: it) to rows.getOrNull(selected + 1) },
                showNext = false,
                picture = !compact,
            )
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = if (joined) 40.mpx else 0.mpx)) {
            // Only as many rows as fit under the programme card, so none is cut in half.
            val fit = if (joined) (maxHeight / 58.mpx).toInt().coerceAtLeast(1) else rows.size
            // glcn: the window follows the picked programme down the evening.
            val start = if (selected >= fit) selected - fit + 1 else 0
            Column(verticalArrangement = Arrangement.spacedBy(5.mpx)) {
            rows.drop(start).take(fit).forEachIndexed { k, p ->
            val onAir = now in p.startMs until p.stopMs
            val picked = start + k == selected
            val onAccent = stageAccent.onAccent
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.mpx))
                    .background(if (picked) accent else if (onAir) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 12.mpx, vertical = 13.mpx),
                horizontalArrangement = Arrangement.spacedBy(14.mpx),
            ) {
                Text(
                    fmt.format(java.util.Date(p.startMs)),
                    style = stageText(19, 700), color = if (picked) onAccent else if (onAir) accent else StageColors.Muted, maxLines = 1,
                    modifier = Modifier.width(78.mpx),
                )
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = stageText(20, if (onAir || picked) 800 else 600), color = if (picked) onAccent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (onAir && !joined) {
                        val span = (p.stopMs - p.startMs).coerceAtLeast(1L)
                        val f = ((now - p.startMs).toFloat() / span).coerceIn(0f, 1f)
                        Box(Modifier.padding(top = 6.mpx).fillMaxWidth().height(4.mpx).clip(RoundedCornerShape(2.mpx)).background(StageColors.Ink.copy(alpha = 0.12f))) {
                            Box(Modifier.fillMaxWidth(f).height(4.mpx).background(accent))
                        }
                        p.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = stageText(15, 400), color = StageColors.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx))
                        }
                    }
                }
            }
        }
            }
        }
    }
}

/**
 * glcn: once the D-pad rests, warms the pictures of the channels just above and below the focused
 * one (programme on air), so stepping to them shows the picture at once instead of after a fetch.
 */
@Composable
internal fun NeighbourArtPrefetch(
    channels: List<ChannelEntity>,
    focusedIndex: Int,
    loadNow: suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(focusedIndex, channels.size) {
        kotlinx.coroutines.delay(200)
        val loader = coil3.SingletonImageLoader.get(ctx)
        // glcn: the whole stretch around the cursor, nearest first, so a zap through the list never waits.
        val order = buildList { for (d in 1..14) { add(d); add(-d) } }
        for (d in order) {
            val ch = channels.getOrNull(focusedIndex + d) ?: continue
            val url = runCatching { loadNow(ch).first?.imageUrl }.getOrNull()?.takeIf { it.isNotBlank() } ?: continue
            loader.enqueue(
                coil3.request.ImageRequest.Builder(ctx).data(url)
                    // glcn: the size the panels draw it at (about 620 wide), not twice that: twenty-eight neighbours at
                    // 960 × 540 held ~60 MB of pictures in memory for one list.
                    .size(coil3.size.Size(640, 360)).build(),
            )
            kotlinx.coroutines.delay(25)
        }
    }
}

/** glcn: the preview's picture — a wide picture fills its frame; a logo-like one stands whole, sharp, on a soft tile. */
@Composable
private fun PreviewPicture(url: String, logoLike: Boolean, aspect: Float) {
    var asLogo by remember(url) { mutableStateOf(logoLike || (PreviewShapes.logoLike[url] ?: false)) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(
        Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(StageRadii.Sheet))
            .then(if (asLogo) Modifier.background(StageColors.Ink.copy(alpha = 0.06f)) else Modifier),
    ) {
        coil3.compose.AsyncImage(
            model = remember(url, asLogo) {
                coil3.request.ImageRequest.Builder(ctx).data(url)
                    // A logo is small: read it whole, so it is not scaled up from a thumbnail.
                    .apply { if (asLogo) size(coil3.size.Size.ORIGINAL) }
                    .build()
            },
            contentDescription = null,
            contentScale = if (asLogo) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop,
            onSuccess = { st ->
                val img = st.result.image
                if (!asLogo && img.height > 0) {
                    val r = img.width.toFloat() / img.height
                    // glcn: a logo is squarish or drawn on a transparent background (the guides that give a
                    // channel's logo as the programme's picture send a wide transparent PNG — cropped, it lost the logo).
                    val transparent = (img as? coil3.BitmapImage)?.bitmap?.hasAlpha() == true
                    // The size is NOT a sign: the picture is read at the size it is drawn at, so a wide photo of a news set
                    // came back under 480 wide and stood as a small logo in the middle of an empty frame.
                    if (r < 1.25f || transparent) { asLogo = true; PreviewShapes.logoLike[url] = true }
                }
            },
            modifier = if (asLogo) Modifier.align(Alignment.Center).fillMaxWidth(0.56f).fillMaxHeight(0.56f) else Modifier.fillMaxSize(),
        )
    }
}

private object PreviewShapes {
    /** Pictures found to be logos, so the next time they open as such straight away. */
    val logoLike = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
}

/** glcn: the channel list's entrance — how long, and from how far to the side. */
private const val OVERLAY_ENTER_MS = 380
private const val OVERLAY_ENTER_PX = 120

// Upstream (two-pane channel browser): the list as a reusable panel.
/**
 * The glass list itself — heading, "Now playing" line, the rows and the key hints — shared by
 * [ChannelListOverlay] and the two-pane [ChannelBrowserOverlay]. [entryIndex] is the row focus lands on;
 * with [autoFocus] the list takes focus as it opens, otherwise it waits for [focusTarget] to be asked
 * and scrolls back to [entryIndex] whenever [channels] changes (a new category on the right).
 * Pushing towards [outward] calls [onOutward]; the caller owns Back.
 */
@Composable
internal fun ChannelListPanel(
    channels: List<ChannelEntity>,
    currentId: Long?,
    entryIndex: Int,
    onSelect: (ChannelEntity) -> Unit,
    outward: HorizontalDirection,
    onOutward: () -> Unit,
    outwardHint: Pair<String, String>?,
    modifier: Modifier = Modifier,
    nowProgrammes: Map<Long, XtEpgEntry> = emptyMap(),
    nowPlaying: Map<Long, String> = emptyMap(),
    favoriteIds: Set<Long> = emptySet(),
    playlistMarks: Map<Long, PlaylistMark> = emptyMap(),
    title: String? = null,
    showNumbers: Boolean = true,
    watchedAt: Map<Long, Long>? = null,
    focusTarget: FocusRequester = remember { FocusRequester() },
    autoFocus: Boolean = true,
    onFocusChannel: (ChannelEntity) -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val history = watchedAt != null
    val playingIndex = remember(channels, currentId) { channels.indexOfFirst { it.id == currentId } }
    val listState = rememberLazyListState()
    // The row that owns [focusTarget]: the entry row at first, then wherever paging jumped to.
    var targetIndex by remember(channels, entryIndex) { mutableIntStateOf(entryIndex) }
    var focusedIndex by remember(channels, entryIndex) { mutableIntStateOf(entryIndex) }
    var listFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    LaunchedEffect(if (autoFocus) Unit else channels) {
        runCatching { listState.scrollToItem(entryIndex) }
        if (autoFocus) runCatching { focusTarget.requestFocus() }
    }

    val accent = stageAccent.accent
    Column(
        modifier = modifier
            .stageGlass(StageRadii.Sheet, overContent = true)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key.horizontalDirection(layoutDirection) == outward) {
                    onOutward()
                    true
                } else false
            }
            .padding(horizontal = 16.mpx, vertical = 26.mpx),
    ) {
        // "Catch-up · 978 channels        Live TV", as the Live TV header and category sheet have it.
        Row(
            Modifier.fillMaxWidth().padding(start = 14.mpx, end = 14.mpx),
            horizontalArrangement = Arrangement.spacedBy(16.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title ?: stringResource(R.string.content_channel_overlay_title),
                    style = stageText(26, 800), color = StageColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "· " + pluralStringResource(R.plurals.content_live_channel_count, channels.size, channels.size),
                    style = stageText(18, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(stringResource(R.string.common_nav_live_tv), style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val playing = channels.getOrNull(playingIndex)
        val subLine = when {
            history -> stringResource(R.string.content_history_overlay_hint)
            playing != null -> listOfNotNull(
                stringResource(R.string.shell_now_playing),
                playing.number?.toString()?.takeIf { showNumbers },
                ProviderTags.parse(playing.name).name,
            ).joinToString(" · ")
            else -> null
        }
        subLine?.let {
            Text(
                it, style = stageText(16, 400), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.mpx, end = 14.mpx, top = 4.mpx, bottom = 12.mpx),
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { listFocused = it.hasFocus }
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { listFocused },
                    lastIndex = { channels.lastIndex },
                    currentTargetIndex = { focusedIndex },
                    onJumpToIndex = { idx ->
                        targetIndex = idx
                        scope.jumpLazyListTo(listState, idx) { focusTarget.requestFocus() }
                    },
                ),
            // Room for the focus ring, which the list would otherwise clip.
            contentPadding = PaddingValues(horizontal = 4.mpx, vertical = 4.mpx),
            verticalArrangement = Arrangement.spacedBy(2.mpx),
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }) { index, ch ->
                val isPlaying = index == playingIndex
                val nowMs = remember { System.currentTimeMillis() }
                LiveStageRow(
                    channel = ch,
                    name = ProviderTags.parse(ch.name),
                    now = nowProgrammes[ch.id],
                    nowTitle = nowPlaying[ch.id],
                    isFavorite = ch.id in favoriteIds,
                    showNumber = showNumbers,
                    mark = playlistMarks[ch.sourceId],
                    onFocus = { focusedIndex = index; onFocusChannel(ch) },
                    onClick = { onSelect(ch) },
                    onLongClick = {},
                    logoTile = false,
                    trailing = watchedAt?.let { times ->
                        {
                            Text(
                                if (isPlaying) stringResource(R.string.shell_now_playing)
                                else times[ch.id]?.let { relativeLastWatchedLabel(it, nowMs) }.orEmpty(),
                                style = stageText(15, if (isPlaying) 700 else 500),
                                color = if (isPlaying) accent else StageColors.Dim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    modifier = Modifier
                        .playingMark(isPlaying)
                        .then(if (index == targetIndex) Modifier.focusRequester(focusTarget) else Modifier),
                )
            }
        }
        // glcn: the watched channel scrolled out of sight: a pill at the edge it went past.
        val watchSide by remember(listState, playingIndex) {
            androidx.compose.runtime.derivedStateOf {
                val vis = listState.layoutInfo.visibleItemsInfo
                when {
                    playingIndex < 0 || vis.isEmpty() -> 0
                    playingIndex < vis.first().index -> 1
                    playingIndex > vis.last().index -> 2
                    else -> 0
                }
            }
        }
        val watchName = channels.getOrNull(playingIndex)?.let { ProviderTags.parse(it.name).name }.orEmpty()
        androidx.compose.animation.AnimatedVisibility(watchSide == 1, Modifier.align(Alignment.TopCenter).padding(top = 6.mpx), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) { tv.own.owntv.glcn.WatchingPill(watchName, true) }
        androidx.compose.animation.AnimatedVisibility(watchSide == 2, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.mpx), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) { tv.own.owntv.glcn.WatchingPill(watchName, false) }
        }
        StageKeyHints(
            listOfNotNull(
                stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
                outwardHint,
                "CH±" to stringResource(R.string.content_key_page),
                stringResource(R.string.common_back) to stringResource(R.string.content_close),
            ),
            textSize = 16,
            modifier = Modifier.padding(start = 14.mpx, top = 14.mpx),
        )
    }
}
