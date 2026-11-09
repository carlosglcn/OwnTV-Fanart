package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import tv.own.owntv.glcn.groupGlyph
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.features.live.liveCategoryEntries
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the zap deck — ◀ over the playing channel, as TiviMate does it but in the app's own look. One sheet of
 * glass slides in from the left over the dimmed video: the groups in a narrow column (the playlists as chips
 * above them when there are several), the channels of a group with their logo, number, programme on air and how
 * far through it is, and on the right the focused channel's programme picture with what follows it — or its
 * schedule (▶ switches, remembered).
 *
 * It opens on the playing channel. One cursor glides between rows and the lists glide under it, the same in
 * every column; keys are handled by the deck itself, so nothing jumps while a list loads. ◀ goes to the groups
 * (moving there shows each group's channels a moment later), ▶ or OK comes back to the channels, ▲ from the first
 * group reaches the playlists, OK on a channel tunes it, Back closes. CH± pages.
 */
@Composable
fun ZapDeck(
    railItems: List<LiveRailItem>,
    railCounts: Map<LiveKey, Int>,
    /** The list the player zaps in, and its key (the group it came from). */
    channels: List<ChannelEntity>,
    currentKey: LiveKey?,
    /** True when [channels] really is [currentKey]'s list; false when the player came from elsewhere (a Home row). */
    listIsGroup: Boolean,
    currentId: Long?,
    showNumbers: Boolean,
    /** 0 = programme picture, 1 = schedule. */
    detail: Int,
    onCycleDetail: (() -> Unit)?,
    peekChannels: suspend (LiveKey) -> List<ChannelEntity>,
    loadNowMany: suspend (List<ChannelEntity>) -> Map<Long, EpgProgrammeEntity>,
    loadNow: suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>,
    loadSchedule: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    /** Makes [key] the list the player zaps in (a channel tuned from another group). */
    onArmCategory: (LiveKey) -> Unit,
    onTune: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (entries, _) = liveCategoryEntries(railItems, railCounts)
    val playlists = tv.own.owntv.features.live.LocalCategoryPlaylists.current?.takeIf { it.playlists.size > 1 }
    val allText = stringResource(R.string.content_all_playlists_short)
    val chips = remember(playlists?.playlists, allText) {
        playlists?.let { listOf(-1L to allText) + it.playlists.map { p -> p.id to p.name } }.orEmpty()
    }
    val activeChip = playlists?.activeId?.takeIf { it > 0L } ?: -1L
    // glcn: each playlist's coloured tag (its place in the profile picks the colour), for the categories and the heading.
    val marks = remember(playlists?.playlists) {
        playlists?.playlists?.withIndex()?.associate { (i, s) -> s.id to tv.own.owntv.ui.stage.PlaylistMark.of(s.name, i) }.orEmpty()
    }

    // 0 = groups, 1 = channels, -1 = the playlist chips, 2 = the focused channel's coming programmes.
    var column by remember { mutableIntStateOf(1) }
    // glcn: the coming programme picked in the schedule (column 2), and how many there are.
    var schedIdx by remember { mutableIntStateOf(0) }
    var schedCount by remember { mutableIntStateOf(0) }
    var groupIdx by remember { mutableIntStateOf(entries.indexOfFirst { it.item.key == currentKey }.coerceAtLeast(0)) }
    var chipIdx by remember { mutableIntStateOf(chips.indexOfFirst { it.first == activeChip }.coerceAtLeast(0)) }
    // The group whose channels are shown: the zap list itself when it is that group's, else the group read whole.
    // A channel opened from a Home row zaps in that row (twenty recent channels), not in its group, so the
    // group is read here rather than labelling those twenty with the group's name.
    fun usesZap(key: LiveKey?) = listIsGroup && key == currentKey
    var shownKey by remember { mutableStateOf(currentKey) }
    var loaded by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var loadedFor by remember { mutableStateOf<Pair<LiveKey?, Long>?>(null) }
    val list = if (usesZap(shownKey)) channels else loaded
    var chIdx by remember { mutableIntStateOf(if (listIsGroup) channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0) else 0) }
    var listVersion by remember { mutableIntStateOf(0) }
    var nowMap by remember { mutableStateOf<Map<Long, EpgProgrammeEntity>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val key = currentKey ?: return@LaunchedEffect
        if (usesZap(key)) return@LaunchedEffect
        loaded = runCatching { peekChannels(key) }.getOrDefault(emptyList())
        loadedFor = key to activeChip
        chIdx = loaded.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        listVersion++
    }
    // Resting on a group shows its channels (read again when the playlist changes).
    LaunchedEffect(groupIdx, column, entries.size, activeChip) {
        if (column != 0) return@LaunchedEffect
        val key = entries.getOrNull(groupIdx)?.item?.key ?: return@LaunchedEffect
        if (usesZap(key)) {
            if (shownKey != key) {
                shownKey = key
                chIdx = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                listVersion++
            }
            return@LaunchedEffect
        }
        if (shownKey == key && loadedFor == key to activeChip) return@LaunchedEffect
        delay(220)
        val got = runCatching { peekChannels(key) }.getOrDefault(emptyList())
        loaded = got
        loadedFor = key to activeChip
        shownKey = key
        chIdx = got.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        listVersion++
    }
    // What is on: the rows around the cursor, read once the cursor rests.
    LaunchedEffect(listVersion, chIdx / 12, list.size) {
        delay(120)
        // glcn: read once, then clamped — the list may have changed group in the 120 ms (a crash: fromIndex past its end).
        val rows = list
        val at = chIdx.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        val window = rows.subList((at - 30).coerceAtLeast(0), (at + 31).coerceAtMost(rows.size)).filter { it.id !in nowMap }
        if (window.isNotEmpty()) {
            val got = runCatching { loadNowMany(window) }.getOrDefault(emptyMap())
            if (got.isNotEmpty()) nowMap = nowMap + got
        }
    }

    // glcn: programmes read once and kept for the deck's life; the rows around the cursor are read (and their
    // pictures decoded) ahead, so moving shows the next channel's picture in the same frame.
    val nowCache = remember { java.util.concurrent.ConcurrentHashMap<Long, Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>>() }
    val cachedNow: suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?> = remember(loadNow) {
        { ch -> nowCache[ch.id] ?: loadNow(ch).also { nowCache[ch.id] = it } }
    }
    NeighbourArtPrefetch(list, chIdx, cachedNow)
    LaunchedEffect(chIdx, listVersion) {
        delay(200)
        for (d in buildList { for (k in 1..6) { add(k); add(-k) } }) list.getOrNull(chIdx + d)?.let { runCatching { loadSchedule(it) } }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }

    // Slides in.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    val density = LocalDensity.current
    val slidePx = with(density) { 70.mpx.toPx() }

    fun tuneFocused() {
        val ch = list.getOrNull(chIdx) ?: return
        val key = shownKey
        if (key != null && !usesZap(key)) onArmCategory(key)
        onTune(ch)
    }

    val page = 8
    // glcn: the widths chosen in Settings → Layout → Panel widths; while the channels or the schedule have the
    // cursor the groups fold to their icons and the programme column takes the room they leave.
    val widths = tv.own.owntv.glcn.DeckWidths.shares
    val groupsOpenSet = tv.own.owntv.glcn.DeckWidths.width(widths.groups)
    val foldTarget = if (column == 1 || column == 2) 1f else 0f
    val fold by androidx.compose.animation.core.animateFloatAsState(
        foldTarget,
        if (tv.own.owntv.glcn.LowRam.on) androidx.compose.animation.core.snap<Float>() else tween<Float>(260, easing = FastOutSlowInEasing),
        label = "deckFold",
    )
    val shade = tv.own.owntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    // glcn: squeeze-back — the picture shrinks into the free space right of the list (ZapStage) and the list stands
    // on a plain page beside it: nothing covers the picture, and nothing behind the words can make them hard to read.
    androidx.compose.runtime.DisposableEffect(Unit) {
        tv.own.owntv.glcn.ZapStage.liveWindow = true
        onDispose { tv.own.owntv.glcn.ZapStage.liveWindow = false; tv.own.owntv.glcn.ZapStage.frame = null }
    }
    var deckBox by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    // glcn: the picture's place is fixed — half the screen's width, up on the right — and set once: it never follows
    // the list's columns (folding groups, the schedule…), because every resize of a playing picture costs the box a
    // stutter. The programme is told under it, the groups open over the channels instead of pushing them.
    // glcn: the picture takes all the width the list leaves (from just past the list to the screen's edge), as large
    // as it can be while the programme and what follows it still have their room under it.
    val chW = tv.own.owntv.glcn.DeckWidths.width(widths.channels).coerceIn(320.mpx, 860.mpx)
    val pictureMargin = with(density) { 40.mpx.toPx() }
    val pictureTop = with(density) { 40.mpx.toPx() }
    val pictureGap = with(density) { 28.mpx.toPx() }
    val listEdge = with(density) { (14.mpx + chW).toPx() }
    val underRoom = with(density) { UNDER_PICTURE.toPx() }
    val pictureLocal = remember(deckBox.width, deckBox.height, listEdge) {
        var pw = (deckBox.width - pictureMargin - listEdge - pictureGap).coerceAtLeast(0f)
        val maxH = (deckBox.height - pictureTop - underRoom).coerceAtLeast(0f)
        if (pw * 9f / 16f > maxH) pw = maxH * 16f / 9f
        androidx.compose.ui.geometry.Rect(deckBox.width - pictureMargin - pw, pictureTop, deckBox.width - pictureMargin, pictureTop + pw * 9f / 16f)
    }
    LaunchedEffect(deckBox) {
        if (deckBox.width > 0f) tv.own.owntv.glcn.ZapStage.frame = pictureLocal.translate(deckBox.left, deckBox.top)
    }
    val pictureRadius = with(LocalDensity.current) { 18.mpx.toPx() }
    // glcn: a held ▲/▼ on the channels turns the list like a wheel — steps on the screen's frames at an even pace
    // instead of the key's own repeats, then coasts a few rows and stops — and what is under the picture waits
    // until it stands still (as Live TV's list does, glcn/ListWheel).
    val wheelScope = androidx.compose.runtime.rememberCoroutineScope()
    val wheelList = androidx.compose.runtime.rememberUpdatedState(list)
    var wheelHeld by remember { mutableStateOf(false) }
    var wheelTurning by remember { mutableStateOf(false) }
    var wheelJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun turnWheel(dir: Int) {
        wheelHeld = true
        if (wheelJob?.isActive == true) return
        wheelTurning = true
        wheelJob = wheelScope.launch {
            try {
                var acc = 0f
                var speed = 0f
                var last = androidx.compose.runtime.withFrameNanos { it }
                val began = last
                while (wheelHeld) {
                    val now = androidx.compose.runtime.withFrameNanos { it }
                    val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                    last = now
                    val t = (now - began) / 1e9f
                    speed = WHEEL_CRUISE * wheelEase((t / WHEEL_START_S).coerceIn(0f, 1f)) +
                        (WHEEL_TOP - WHEEL_CRUISE) * wheelEase(((t - WHEEL_RAMP_DELAY_S) / WHEEL_RAMP_S).coerceIn(0f, 1f))
                    acc += speed * dt
                    while (acc >= 1f) {
                        acc -= 1f
                        val next = chIdx + dir
                        if (next !in wheelList.value.indices) { wheelHeld = false; break }
                        chIdx = next
                    }
                }
                // Let go: it coasts on a few rows, each step a little slower, and stops.
                val extra = (speed * WHEEL_COAST_S).toInt().coerceIn(0, 4)
                for (k in 0 until extra) {
                    delay(70L + 55L * k)
                    val next = chIdx + dir
                    if (next !in wheelList.value.indices) break
                    chIdx = next
                }
            } finally {
                wheelHeld = false
                wheelTurning = false
            }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { deckBox = it.boundsInWindow() }
            .okAfterFreshPress()
            .graphicsLayer { alpha = enter.value; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                // The page, with a hole where the picture is on its way to (or sits in) its place.
                drawRect(shade)
                val hole = deckHole(size, deckBox, pictureRadius)
                drawRoundRect(
                    Color.Black, topLeft = hole.rect.topLeft, size = hole.rect.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(hole.radius),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                )
                drawContent()
            }
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                // glcn: the key comes up — the wheel coasts and stops.
                if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionUp || e.key == Key.DirectionDown) && wheelHeld) {
                    wheelHeld = false; return@onPreviewKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val first = e.nativeKeyEvent.repeatCount == 0
                when (e.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        val up = e.key == Key.DirectionUp
                        if (column == 1 && !tv.own.owntv.glcn.LowRam.on && list.size > 1) {
                            if (!first) { turnWheel(if (up) -1 else 1); return@onPreviewKeyEvent true }
                            if (wheelTurning) { wheelHeld = false; return@onPreviewKeyEvent true }
                        }
                        when (column) {
                            2 -> schedIdx = (schedIdx + if (up) -1 else 1).coerceIn(0, (schedCount - 1).coerceAtLeast(0))
                            1 -> if (list.size > 1) chIdx = if (up) (if (chIdx == 0) list.lastIndex else chIdx - 1) else (if (chIdx == list.lastIndex) 0 else chIdx + 1)
                            0 -> when {
                                up && groupIdx == 0 && chips.isNotEmpty() -> { column = -1; chipIdx = chips.indexOfFirst { it.first == activeChip }.coerceAtLeast(0) }
                                up -> groupIdx = (groupIdx - 1).coerceAtLeast(0)
                                else -> groupIdx = (groupIdx + 1).coerceAtMost(entries.lastIndex.coerceAtLeast(0))
                            }
                            -1 -> if (!up) column = 0
                        }
                        true
                    }
                    Key.ChannelUp, Key.ChannelDown, Key.PageUp, Key.PageDown -> {
                        val up = e.key == Key.ChannelUp || e.key == Key.PageUp
                        when (column) {
                            1 -> chIdx = (chIdx + if (up) -page else page).coerceIn(0, list.lastIndex.coerceAtLeast(0))
                            0 -> groupIdx = (groupIdx + if (up) -page else page).coerceIn(0, entries.lastIndex.coerceAtLeast(0))
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        when (column) {
                            2 -> column = 1
                            1 -> { column = 0; groupIdx = entries.indexOfFirst { it.item.key == shownKey }.takeIf { it >= 0 } ?: groupIdx }
                            -1 -> chipIdx = (chipIdx - 1).coerceAtLeast(0)
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        when (column) {
                            0 -> if (list.isNotEmpty() && entries.getOrNull(groupIdx)?.item?.key == shownKey) column = 1
                            // ▶ from the channels gives the picture back, as Back does.
                            // glcn: in the schedule view ▶ walks into the coming programmes (their picture and synopsis below).
                            1 -> if (first) {
                                if (schedCount > 1) { column = 2; schedIdx = 1 } // glcn: into what follows, at the first of it
                                else onDismiss()
                            }
                            -1 -> chipIdx = (chipIdx + 1).coerceAtMost(chips.lastIndex)
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (first) when (column) {
                            1, 2 -> tuneFocused()
                            0 -> if (list.isNotEmpty() && entries.getOrNull(groupIdx)?.item?.key == shownKey) column = 1
                            -1 -> chips.getOrNull(chipIdx)?.let { (id, _) -> playlists?.onSelect?.invoke(id); column = 0; groupIdx = 0 }
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        // glcn: the focused programme's picture behind the page, soft, faint behind the list and fuller on the right;
        // it changes only once the cursor rests, so zapping through the list does not flicker.
        val restingChannel = list.getOrNull(chIdx)
        var backdropProgramme by remember { mutableStateOf<EpgProgrammeEntity?>(null) }
        LaunchedEffect(restingChannel?.id) {
            val ch = restingChannel ?: return@LaunchedEffect
            delay(BACKDROP_REST_MS)
            backdropProgramme = runCatching { cachedNow(ch).first }.getOrNull()
        }
        val backdropUrl = tv.own.owntv.ui.components.programmeArtOrTmdb(backdropProgramme)
        DeckBackdrop(
            url = backdropUrl,
            shade = shade,
            picture = pictureLocal,
            hole = { sz -> deckHole(sz, deckBox, pictureRadius) },
        )
        Row(
            Modifier
                .graphicsLayer { translationX = (1f - enter.value) * -slidePx }
                .align(Alignment.CenterStart)
                .padding(horizontal = 14.mpx, vertical = 8.mpx)
                .fillMaxHeight(),
        ) {
            // glcn: both parts on a share of the same glass, so the picture shows through everywhere, both solid
            // enough for their words to read over a bright picture or its subtitles.
            Box(Modifier.fillMaxHeight()) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = tv.own.owntv.glcn.PlayerGlass.share(DECK_LIST_GLASS) }.stageGlass(StageRadii.Sheet, overContent = true))
            Row(Modifier.fillMaxHeight().padding(vertical = 20.mpx)) {
            // ---- Groups (and the playlists above them).
            // glcn: as TiviMate does — folded, the groups are not there at all (no column of initials); ◀ opens them
            // over the channels, which dim behind them, with their whole names, counts and playlist tags.
            val groupsOpen = groupsOpenSet.coerceAtMost(chW + 20.mpx)
            val slideGroupsPx = with(density) { 40.mpx.toPx() }
            Box(Modifier.width(0.mpx).fillMaxHeight().wrapContentWidth(Alignment.Start, unbounded = true).zIndex(1f)) {
            if (fold < 0.999f) Box(Modifier.width(groupsOpen).fillMaxHeight().graphicsLayer { alpha = 1f - fold; translationX = -slideGroupsPx * fold }) {
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(StageRadii.Sheet)).background(shade))
            Column(Modifier.fillMaxSize().padding(start = 14.mpx, end = 8.mpx)) {
                if (chips.isNotEmpty()) {
                    DeckChips(chips, activeChip, if (column == -1) chipIdx else -1)
                    Spacer(Modifier.height(10.mpx))
                }
                Text(
                    stringResource(R.string.content_category_browser_title),
                    style = stageText(17, 700), color = StageColors.Dim, maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(start = 14.mpx, bottom = 8.mpx),
                )
                GlideColumn(
                    count = entries.size,
                    index = groupIdx,
                    rowHeight = 60.mpx,
                    active = column == 0,
                    resetKey = entries.size to activeChip,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { i, focused ->
                    val entry = entries.getOrNull(i) ?: return@GlideColumn
                    val shown = entry.item.key == shownKey
                    val lit = focused && column == 0
                    val tint by animateColorAsState(
                        when {
                            lit -> stageAccent.onAccent
                            shown -> StageColors.Text
                            else -> StageColors.Muted
                        },
                        tween(140), label = "deckGroupText",
                    )
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 14.mpx),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.mpx),
                    ) {
                        entry.icon?.let { OwnTVIcon(it, tint, Modifier.size(20.mpx)) }
                        val glyph = entry.groupGlyph()
                        glyph?.let { tv.own.owntv.glcn.CategoryGlyphView(it, tint, size = 20.mpx, name = entry.label) }
                        Text(
                            if (tv.own.owntv.glcn.CategoryIcons.hidesName(glyph)) "" else entry.label, style = stageText(19, if (shown) 800 else 600), color = tint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false,
                            modifier = Modifier.weight(1f),
                        )
                        // The playlist it comes from, when all playlists are listed together.
                        if (activeChip == -1L) entry.item.sourceId?.let(marks::get)?.let { tv.own.owntv.ui.stage.StagePlaylistMark(it, onLight = lit) }
                        entry.count?.let { Text(it.toString(), style = stageText(15, 500), color = tint.copy(alpha = 0.7f), maxLines = 1, softWrap = false) }
                    }
                }
            }
            }
            }
            // ---- Channels of the shown group.
            Column(Modifier.width(chW).fillMaxHeight().graphicsLayer { alpha = 0.3f + 0.7f * fold }.padding(horizontal = 10.mpx)) {
                val shownEntry = entries.firstOrNull { it.item.key == shownKey }
                val groupName = shownEntry?.label.orEmpty()
                Row(
                    Modifier.padding(start = 14.mpx, end = 14.mpx, bottom = 12.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.mpx),
                ) {
                    Text(groupName, style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    shownEntry?.item?.sourceId?.let(marks::get)?.let { tv.own.owntv.ui.stage.StagePlaylistMark(it) }
                    Text(
                        pluralStringResource(R.plurals.content_live_channel_count, list.size, list.size),
                        style = stageText(17, 500), color = StageColors.Muted, maxLines = 1,
                    )
                }
                GlideColumn(
                    count = list.size,
                    index = chIdx,
                    rowHeight = 84.mpx,
                    active = column == 1,
                    resetKey = listVersion,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { i, focused ->
                    val ch = list.getOrNull(i) ?: return@GlideColumn
                    DeckChannelRow(
                        channel = ch,
                        now = nowMap[ch.id],
                        playing = ch.id == currentId,
                        lit = focused && column == 1,
                        showNumber = showNumbers,
                    )
                }
                StageKeyHints(
                    listOfNotNull(
                        stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
                        "◀" to stringResource(R.string.content_category_browser_title),
                        "▶" to stringResource(R.string.glcn_zap_coming_up),
                        (if (onCycleDetail != null) stringResource(R.string.common_back) else "▶ / " + stringResource(R.string.common_back)) to stringResource(R.string.content_close),
                    ),
                    textSize = 15,
                    modifier = Modifier.padding(start = 14.mpx, top = 12.mpx),
                )
            }
            }
            }
        }
        // ---- The focused channel, under the picture: its programme, or its schedule.
        val panelHold = remember { arrayOfNulls<ChannelEntity>(1) }
        val focusedChannel = if (wheelTurning) panelHold[0] else list.getOrNull(chIdx).also { panelHold[0] = it }
        // glcn: over the programme's picture the text is light, as on a dark page.
        val onArt = backdropUrl != null && !tv.own.owntv.glcn.LowRam.on
        if (pictureLocal.width > 0f) androidx.compose.runtime.CompositionLocalProvider(
            tv.own.owntv.ui.theme.LocalStagePalette provides if (onArt) tv.own.owntv.ui.theme.StagePalette.Dark else tv.own.owntv.ui.theme.LocalStagePalette.current,
        ) { with(density) {
            Column(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(pictureLocal.left.toInt(), (pictureLocal.bottom + 24.mpx.toPx()).toInt()) }
                    .width(pictureLocal.width.toDp())
                    .height((deckBox.height - pictureLocal.bottom - 24.mpx.toPx() - 28.mpx.toPx()).coerceAtLeast(0f).toDp())
                    .graphicsLayer { alpha = enter.value }
                    .clipToBounds(),
            ) {
                androidx.compose.animation.Crossfade(focusedChannel, modifier = Modifier.fillMaxWidth().weight(1f), animationSpec = tween(140), label = "deckDetail") { ch ->
                    if (ch != null) DeckInfoPanel(
                        channel = ch, loadNow = cachedNow, loadSchedule = loadSchedule,
                        peek = { nowCache[it.id] },
                        selected = if (column == 2) schedIdx else -1,
                        onRowCount = { schedCount = it },
                    )
                }
            }
        } }
    }
}

/** glcn: the room kept under the picture for the programme and what follows it. */
private val UNDER_PICTURE = 330.mpx

/** glcn: the channel wheel's pace (rows a second): from rest up to cruise, more only after a long hold; its coast. */
private const val WHEEL_CRUISE = 12f
private const val WHEEL_TOP = 18f
private const val WHEEL_START_S = 0.3f
private const val WHEEL_RAMP_DELAY_S = 1.4f
private const val WHEEL_RAMP_S = 1.6f
private const val WHEEL_COAST_S = 0.2f
private fun wheelEase(x: Float) = x * x * (3f - 2f * x)

/** glcn: how long the cursor rests on a channel before its programme's picture becomes the page's backdrop. */
private const val BACKDROP_REST_MS = 450L

/** glcn: where the page is open onto the playing picture, on its way to (or in) its place beside the list. */
private class DeckHole(val rect: androidx.compose.ui.geometry.Rect, val radius: Float)

private fun deckHole(size: androidx.compose.ui.geometry.Size, deckBox: androidx.compose.ui.geometry.Rect, radius: Float): DeckHole {
    val p = tv.own.owntv.glcn.ZapStage.progress
    val f = tv.own.owntv.glcn.ZapStage.frame ?: return DeckHole(androidx.compose.ui.geometry.Rect(Offset.Zero, size), 0f)
    val r = f.translate(-deckBox.left, -deckBox.top)
    val w = size.width + (r.width - size.width) * p
    val h = size.height + (r.height - size.height) * p
    return DeckHole(androidx.compose.ui.geometry.Rect(Offset(r.left * p, r.top * p), androidx.compose.ui.geometry.Size(w, h)), radius * p)
}

/**
 * glcn: what the focused channel is showing, as a picture under the playing one — from halfway down the picture to
 * the foot of the screen, edge to edge on the right; the page stays plain behind the list. It melts into the page at
 * its left and top, and darkens towards the foot, where the programme is told in light text over it.
 */
@Composable
private fun DeckBackdrop(url: String?, shade: Color, picture: androidx.compose.ui.geometry.Rect, hole: (androidx.compose.ui.geometry.Size) -> DeckHole) {
    if (tv.own.owntv.glcn.LowRam.on || picture.width <= 0f) return
    val density = LocalDensity.current
    val fade = with(density) { 90.mpx.toPx() }
    val left = (picture.left - fade).coerceAtLeast(0f)
    val top = picture.bottom - with(density) { 24.mpx.toPx() } // tucked under the picture's rounded foot
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                val h = hole(size)
                val path = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(h.rect, androidx.compose.ui.geometry.CornerRadius(h.radius)))
                }
                clipPath(path, androidx.compose.ui.graphics.ClipOp.Difference) { this@drawWithContent.drawContent() }
            },
    ) {
        Box(
            Modifier.layout { m, c ->
                // From the picture's left edge (less the fade) and its middle, to the screen's right edge and foot.
                val w = (c.maxWidth - left.toInt()).coerceAtLeast(1)
                val h = (c.maxHeight - top.toInt()).coerceAtLeast(1)
                val pl = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                layout(c.maxWidth, c.maxHeight) { pl.place(left.toInt(), top.toInt()) }
            },
        ) {
            androidx.compose.animation.Crossfade(url, animationSpec = tween(600), label = "deckBackdrop") { u ->
                if (u != null) Box(Modifier.fillMaxSize()) {
                    coil3.compose.AsyncImage(
                        model = u,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            // Dark enough for light text, a little more towards the foot.
                            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f), 1f to Color.Black.copy(alpha = 0.78f)))
                            // Melts into the page at its left edge.
                            drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to shade, fade / size.width to shade.copy(alpha = 0f)))
                        },
                    )
                }
            }
        }
    }
}

/**
 * glcn: under the playing picture, two columns. On the left the programme — on air, or the one picked on the right
 * with ▶ ▲ ▼ — its title, time (and what is left of it), progress and synopsis; on the right what follows, one row a
 * programme, as many as fit, the window following the pick down the evening.
 */
@Composable
private fun DeckInfoPanel(
    channel: ChannelEntity,
    loadNow: suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>,
    loadSchedule: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    peek: (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>?,
    /** The programme picked (an index of the schedule, 0 = on air), or -1. */
    selected: Int,
    onRowCount: (Int) -> Unit,
) {
    var onAir by remember(channel.id) { mutableStateOf(peek(channel)?.first) }
    LaunchedEffect(channel.id) {
        if (onAir == null) onAir = runCatching { loadNow(channel).first }.getOrNull()
    }
    var rows by remember(channel.id) { mutableStateOf<List<EpgProgrammeEntity>>(emptyList()) }
    LaunchedEffect(channel.id) {
        delay(120) // a channel only passed over reads nothing
        val now = System.currentTimeMillis()
        rows = runCatching { loadSchedule(channel) }.getOrDefault(emptyList()).filter { it.stopMs > now }.sortedBy { it.startMs }
        onRowCount(rows.size)
    }
    // The schedule's rows are the guide's light ones: the picked one's synopsis is read on its own.
    val guideReader: tv.own.owntv.core.live.GuideReader = org.koin.compose.koinInject()
    val picked = rows.getOrNull(selected)?.takeIf { selected > 0 }
    var pickedWhole by remember(picked?.id) { mutableStateOf(picked) }
    LaunchedEffect(picked?.id) {
        val p = picked ?: return@LaunchedEffect
        if (p.description.isNullOrBlank()) {
            delay(150)
            pickedWhole = p.copy(description = runCatching { guideReader.description(p.id) }.getOrNull())
        }
    }
    val p = pickedWhole ?: onAir ?: rows.firstOrNull()
    val fmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val nowMs = remember(channel.id, rows) { System.currentTimeMillis() }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.mpx)) {
        // ---- The programme.
        Column(Modifier.weight(1.15f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
            if (p == null) {
                Text(ProviderTags.displayName(channel.name), style = stageText(28, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                return@Column
            }
            Text(p.title, style = stageText(28, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val live = nowMs in p.startMs until p.stopMs
            val time = fmt.format(java.util.Date(p.startMs)) + " – " +
                if (live) stringResource(R.string.content_live_time_remaining, fmt.format(java.util.Date(p.stopMs)), ((p.stopMs - nowMs) / 60_000L).toInt().coerceAtLeast(0))
                else fmt.format(java.util.Date(p.stopMs))
            Text(time, style = stageText(18, 500), color = StageColors.Muted, maxLines = 1)
            if (live) {
                val span = (p.stopMs - p.startMs).coerceAtLeast(1L)
                val f = ((nowMs - p.startMs).toFloat() / span).coerceIn(0f, 1f)
                Box(Modifier.padding(vertical = 4.mpx).fillMaxWidth().height(5.mpx).clip(RoundedCornerShape(3.mpx)).background(StageColors.Ink.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(f).height(5.mpx).background(stageAccent.accent))
                }
            }
            p.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = stageText(17, 400), color = StageColors.Body, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
        // ---- What follows.
        val coming = rows.withIndex().filter { (i, r) -> i > 0 || r.startMs > nowMs }
        if (coming.isNotEmpty()) Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                stringResource(R.string.content_live_next).uppercase(), style = stageText(16, 800), color = stageAccent.accent,
                maxLines = 1, modifier = Modifier.padding(start = 12.mpx, bottom = 6.mpx),
            )
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val fit = (maxHeight / 50.mpx).toInt().coerceAtLeast(1)
                val at = coming.indexOfFirst { it.index == selected }
                val start = if (at >= fit) at - fit + 1 else 0
                Column(verticalArrangement = Arrangement.spacedBy(2.mpx)) {
                    for ((i, r) in coming.drop(start).take(fit)) {
                        val lit = i == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.mpx)
                                .clip(RoundedCornerShape(12.mpx))
                                .background(if (lit) stageAccent.accent else Color.Transparent)
                                .padding(horizontal = 12.mpx),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.mpx),
                        ) {
                            Text(
                                fmt.format(java.util.Date(r.startMs)), style = stageText(19, 700),
                                color = if (lit) stageAccent.onAccent else stageAccent.accent, maxLines = 1, modifier = Modifier.width(70.mpx),
                            )
                            Text(
                                r.title, style = stageText(19, if (lit) 800 else 600), color = if (lit) stageAccent.onAccent else StageColors.Text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * glcn: a column of fixed-height rows with one highlight that glides to the focused row while the list glides
 * under it, keeping the row near the middle. Both are springs that keep their speed when the target moves again,
 * so a held key runs smoothly instead of in steps. Not focusable itself — the deck moves [index].
 */
@Composable
private fun GlideColumn(
    count: Int,
    index: Int,
    rowHeight: Dp,
    active: Boolean,
    resetKey: Any,
    modifier: Modifier = Modifier,
    row: @Composable (Int, Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val h = with(density) { rowHeight.toPx() }
    val cursor = androidx.compose.runtime.rememberUpdatedState(index)
    val liveCount = androidx.compose.runtime.rememberUpdatedState(count)
    val accent = stageAccent.accent
    val fill by animateColorAsState(if (active) accent else accent.copy(alpha = 0.20f), tween(180), label = "deckFill")
    BoxWithConstraints(modifier.clipToBounds()) {
        val viewH = constraints.maxHeight.toFloat()
        val maxScroll = (count * h - viewH).coerceAtLeast(0f)
        val target = ((index + 0.5f) * h - viewH / 2f).coerceIn(0f, maxScroll)
        val scroll = remember(resetKey) { Animatable(target) }
        val glide = remember(resetKey) { Animatable(index * h) }
        val listState = remember(resetKey) {
            androidx.compose.foundation.lazy.LazyListState((target / h).toInt(), (target - (target / h).toInt() * h).toInt())
        }
        LaunchedEffect(resetKey, target) {
            scroll.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f))
        }
        LaunchedEffect(resetKey, index) {
            glide.animateTo(index * h, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f))
        }
        // glcn: the list is laid out again only when the glide passes a whole row (it is placed on that row);
        // the part of a row in between is a translation of the drawn layer, read in drawing. Placing the list
        // on every frame (as before) measured it again sixty times a second, and in whole pixels, which is
        // what made the scroll judder, worst of all on slower boxes.
        val firstRow by remember(resetKey) { derivedStateOf { (scroll.value / h).toInt().coerceAtLeast(0) } }
        // Asked of the list's next layout (no forced re-measure from inside a coroutine, which is where a list that had
        // just become shorter could be read past its end).
        LaunchedEffect(listState, firstRow) { listState.requestScrollToItem(firstRow.coerceAtMost(liveCount.value), 0) }
        if (count > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .graphicsLayer { translationY = glide.value - scroll.value }
                    .clip(RoundedCornerShape(14.mpx))
                    .background(fill),
            )
        }
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(with(density) { (viewH + h).toDp() })
                .graphicsLayer {
                    // Where the list really is (after its last layout), so a new placement and its offset land together.
                    val placed = listState.firstVisibleItemIndex * h + listState.firstVisibleItemScrollOffset
                    translationY = placed - scroll.value
                },
        ) {
            items(count) { i ->
                // Only the row that gains or loses the cursor is composed again, not every row on screen.
                val lit by remember(i) { derivedStateOf { cursor.value == i } }
                // glcn: a row the list still holds (the focused one is kept composed) can outlive a shorter list for a
                // frame; it draws nothing then rather than read past the end (the crash of 28 Oct: index 4, size 4).
                Box(Modifier.fillMaxWidth().height(rowHeight)) { if (i < liveCount.value) row(i, lit) }
            }
            // Room below the last row, so the list can always be placed on the row the glide is in.
            item { Spacer(Modifier.fillMaxWidth().height(rowHeight)) }
        }
    }
}

@Composable
private fun DeckChannelRow(channel: ChannelEntity, now: EpgProgrammeEntity?, playing: Boolean, lit: Boolean, showNumber: Boolean) {
    val accent = stageAccent.accent
    val text by animateColorAsState(if (lit) stageAccent.onAccent else StageColors.Text, tween(140), label = "deckRowText")
    val sub by animateColorAsState(if (lit) stageAccent.onAccent.copy(alpha = 0.8f) else StageColors.Muted, tween(140), label = "deckRowSub")
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.mpx)
            .then(
                if (playing && !lit) Modifier.drawBehind {
                    val w = 4.mpx.toPx()
                    drawRect(accent, Offset(0f, size.height * 0.24f), androidx.compose.ui.geometry.Size(w, size.height * 0.52f))
                } else Modifier,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.mpx),
    ) {
        if (showNumber) {
            Text(channel.number?.toString().orEmpty(), style = stageText(17, 700), color = sub, maxLines = 1, modifier = Modifier.width(50.mpx))
        }
        ChannelLogoTile(logoUrl = channel.displayLogoUrl, modifier = Modifier.width(72.mpx).height(48.mpx), fill = Color.Transparent) { }
        Column(Modifier.weight(1f)) {
            Text(ProviderTags.displayName(channel.name), style = stageText(20, if (playing) 800 else 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) {
                Text(now.title, style = stageText(16, 400), color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val span = (now.stopMs - now.startMs).coerceAtLeast(1L)
                val f = ((System.currentTimeMillis() - now.startMs).toFloat() / span).coerceIn(0f, 1f)
                Box(Modifier.padding(top = 5.mpx).fillMaxWidth(0.9f).height(3.mpx).clip(RoundedCornerShape(2.mpx)).background(sub.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(f).height(3.mpx).background(if (lit) stageAccent.onAccent else accent))
                }
            }
        }
    }
}

@Composable
private fun DeckChips(chips: List<Pair<Long, String>>, activeId: Long, cursor: Int) {
    val accent = stageAccent.accent
    // Whole names on one row that slides to the chip under the cursor (or the active one).
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val target = if (cursor >= 0) cursor else chips.indexOfFirst { it.first == activeId }.coerceAtLeast(0)
    LaunchedEffect(target) { runCatching { rowState.animateScrollToItem((target - 1).coerceAtLeast(0)) } }
    androidx.compose.foundation.lazy.LazyRow(
        state = rowState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.mpx),
        horizontalArrangement = Arrangement.spacedBy(8.mpx),
    ) {
        items(chips.size) { i ->
            val (id, name) = chips.getOrNull(i) ?: return@items
            val focused = i == cursor
            val on = id == activeId
            val bg by animateColorAsState(
                when {
                    focused -> accent
                    on -> accent.copy(alpha = 0.22f)
                    else -> StageColors.Ink.copy(alpha = 0.06f)
                },
                tween(160), label = "deckChip",
            )
            Text(
                name,
                style = stageText(16, if (on) 800 else 600),
                color = if (focused) stageAccent.onAccent else if (on) StageColors.Text else StageColors.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 240.mpx)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .padding(horizontal = 14.mpx, vertical = 7.mpx),
            )
        }
    }
}

/** glcn: how much of the stage glass the panel draws: the list keeps enough to read; the detail lets more picture through. */
private const val DECK_LIST_GLASS = 0.74f
// glcn: as solid as the list — at 0.5 the schedule mixed with the subtitles of the picture behind it.
private const val DECK_DETAIL_GLASS = 0.74f
