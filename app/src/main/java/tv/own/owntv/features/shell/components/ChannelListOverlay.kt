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
    val layoutDirection = LocalLayoutDirection.current
    val dismissDirection = if (alignEnd) HorizontalDirection.END else HorizontalDirection.START
    val history = watchedAt != null
    val playingIndex = remember(channels, currentId) { channels.indexOfFirst { it.id == currentId } }
    val currentIndex = remember(channels, playingIndex, history) {
        when {
            history && playingIndex == 0 && channels.size > 1 -> 1
            else -> playingIndex.coerceAtLeast(0)
        }
    }
    // glcn: opens already on the current channel — scrolling there after the first frame showed the top for a blink.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    val focusTarget = remember { FocusRequester() }
    // The row that owns [focusTarget]: the current channel at first, then wherever paging jumped to.
    var targetIndex by remember(currentIndex) { mutableIntStateOf(currentIndex) }
    var focusedIndex by remember(currentIndex) { mutableIntStateOf(currentIndex) }
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
    Box(
        modifier = modifier
            .fillMaxSize()
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
                .padding(horizontal = 14.mpx, vertical = 8.mpx)
                .fillMaxHeight()
                .then(if (unified) Modifier.stageGlass(StageRadii.Sheet, overContent = true) else Modifier),
        ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (history) 760.mpx else 720.mpx)
                .then(if (unified) Modifier else Modifier.stageGlass(StageRadii.Sheet, overContent = true))
                .onPreviewKeyEvent { e ->
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                        onFocus = { focusedIndex = index },
                        onClick = { onSelect(ch) },
                        onLongClick = {},
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
                            .then(if (isPlaying) Modifier.stageSelectedBar(accent, 22.mpx) else Modifier)
                            .then(if (index == targetIndex) Modifier.focusRequester(focusTarget) else Modifier),
                    )
                }
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
                    if (onCyclePanel != null && !alignEnd) "▶" to stringResource(R.string.glcn_zap_view_next) else null,
                    stringResource(R.string.common_back) to stringResource(R.string.content_close),
                ),
                textSize = 16,
                modifier = Modifier.padding(start = 14.mpx, top = 14.mpx),
            )
        }
        if (loadNow != null) NeighbourArtPrefetch(channels, focusedIndex, loadNow)
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
                loadNow = loadNow,
                joined = true,
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
                loadNow = loadNow,
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
) {
    // glcn: the previous channel's programme goes at once — a stale poster for seconds was worse than none.
    var programme by remember(channel?.id) { mutableStateOf(channel?.let { peek?.invoke(it) }) }
    LaunchedEffect(channel?.id) {
        if (channel == null || programme != null) return@LaunchedEffect
        programme = runCatching { loadNow(channel) }.getOrNull()
    }
    val p = programme?.first ?: return
    val next = programme?.second
    val fmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    Column(
        modifier
            .then(if (framed) Modifier.width(620.mpx).stageGlass(StageRadii.Sheet, overContent = true).padding(18.mpx) else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.mpx),
    ) {
        val logo = channel?.displayLogoUrl?.takeIf { it.isNotBlank() }
        val art = p.imageUrl?.takeIf { it.isNotBlank() }
        if (art != null) {
            tv.own.owntv.ui.components.ProgrammeArt(
                url = art,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (framed) 16f / 9f else bareAspect)
                    .clip(RoundedCornerShape(StageRadii.Sheet)),
            )
        } else if (logo != null) {
            // glcn: no picture for the programme → the channel's logo; no logo either → the words alone.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (framed) 120.mpx else 90.mpx)
                    .clip(RoundedCornerShape(StageRadii.Sheet))
                    .background(StageColors.Ink.copy(alpha = 0.06f)),
            ) {
                tv.own.owntv.ui.components.ChannelLogoTile(
                    logoUrl = logo,
                    modifier = Modifier.align(Alignment.Center).size(180.mpx, 80.mpx),
                    fill = Color.Transparent,
                ) { }
            }
        }
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
            DriftingSynopsis(it, style = stageText(16, 400), color = StageColors.Dim, lines = if (framed) 4 else bareLines)
        }
        // glcn: and what follows it.
        next?.let { n ->
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
) {
    var rows by remember { mutableStateOf<List<EpgProgrammeEntity>>(emptyList()) }
    LaunchedEffect(channel?.id) {
        if (channel == null) { rows = emptyList(); return@LaunchedEffect }
        rows = runCatching { loadSchedule(channel) }.getOrDefault(emptyList())
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
            .width(560.mpx)
            .then(
                if (joined) Modifier.drawBehind {
                    val inset = 26.mpx.toPx()
                    drawLine(line, androidx.compose.ui.geometry.Offset(0f, inset), androidx.compose.ui.geometry.Offset(0f, size.height - inset), strokeWidth = 1.mpx.toPx())
                } else Modifier.stageGlass(StageRadii.Sheet, overContent = true),
            )
            .padding(horizontal = 22.mpx, vertical = 20.mpx),
        verticalArrangement = Arrangement.spacedBy(5.mpx),
    ) {
        Text(
            stringResource(R.string.glcn_zap_schedule_title, ProviderTags.parse(ch.name).name),
            style = stageText(22, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 12.mpx),
        )
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Only as many rows as fit above the programme card, so none is cut in half.
            val fit = if (joined) ((maxHeight - 180.mpx) / 56.mpx).toInt().coerceAtLeast(3) else rows.size
            Column(verticalArrangement = Arrangement.spacedBy(5.mpx)) {
            rows.take(fit).forEach { p ->
            val onAir = now in p.startMs until p.stopMs
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.mpx))
                    .background(if (onAir) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 12.mpx, vertical = 13.mpx),
                horizontalArrangement = Arrangement.spacedBy(14.mpx),
            ) {
                Text(
                    fmt.format(java.util.Date(p.startMs)),
                    style = stageText(19, 700), color = if (onAir) accent else StageColors.Muted, maxLines = 1,
                    modifier = Modifier.width(78.mpx),
                )
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = stageText(20, if (onAir) 800 else 600), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        if (joined && loadNow != null) {
            ZapPreviewPanel(channel = channel, loadNow = loadNow, modifier = Modifier.fillMaxWidth().padding(bottom = 46.mpx), framed = false, peek = peek)
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
                    .size(coil3.size.Size(960, 540)).build(),
            )
            kotlinx.coroutines.delay(25)
        }
    }
}
