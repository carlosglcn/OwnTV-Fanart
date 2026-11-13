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
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import tv.own.owntv.ui.stage.shownColor
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
        playlists?.playlists?.withIndex()?.associate { (i, s) -> s.id to tv.own.owntv.ui.stage.PlaylistMark.forSource(s.id, s.name, i) }.orEmpty()
    }

    // 0 = groups, 1 = channels, -1 = the playlist chips, 2 = the focused channel's coming programmes.
    var column by remember { mutableIntStateOf(1) }
    // glcn: the coming programme picked in the schedule (column 2), and how many there are.
    var schedIdx by remember { mutableIntStateOf(0) }
    var schedCount by remember { mutableIntStateOf(0) }
    // glcn: the first programme of "Up next" (1 when the schedule starts with the one on air, else 0).
    var schedFirst by remember { mutableIntStateOf(1) }
    // glcn: the programme picked in "Up next" (its picture becomes the card's).
    var pickedProgramme by remember { mutableStateOf<EpgProgrammeEntity?>(null) }
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
    // glcn: a channel number typed on the remote — the cursor jumps to it as the digits come, and the number shows a moment.
    var typed by remember { mutableStateOf("") }
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
                if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionUp || e.key == Key.DirectionDown) && wheelTurning) {
                    wheelTurning = false; return@onPreviewKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val first = e.nativeKeyEvent.repeatCount == 0
                when (e.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        val up = e.key == Key.DirectionUp
                        // glcn: the wheel (turnWheel) is off: a held key steps row by row, the column's springs glide it; what
                        // is under the picture waits for the key to come up, so the run is not slowed by it.
                        if (column == 1 && !first) wheelTurning = true
                        when (column) {
                            // glcn: ▲ on the first of "Up next" gives the picture back, full screen (a fresh press, not a held run).
                            2 -> if (up && schedIdx <= schedFirst) { if (first) onDismiss() }
                                else schedIdx = (schedIdx + if (up) -1 else 1).coerceIn(schedFirst, (schedCount - 1).coerceAtLeast(schedFirst))
                            1 -> if (list.size > 1) {
                                // glcn: held, the cursor speeds up — one row at a time for the first moment, then 3, then 6.
                                val held = e.nativeKeyEvent.eventTime - e.nativeKeyEvent.downTime
                                val step = when {
                                    first || held < ZAP_ACCEL_1_MS -> 1
                                    held < ZAP_ACCEL_2_MS -> ZAP_ACCEL_1_STEP
                                    else -> ZAP_ACCEL_2_STEP
                                }
                                chIdx = if (step == 1) (if (up) (if (chIdx == 0) list.lastIndex else chIdx - 1) else (if (chIdx == list.lastIndex) 0 else chIdx + 1))
                                    else (chIdx + if (up) -step else step).coerceIn(0, list.lastIndex)
                            }
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
                    // glcn: a number on the remote finds the channel with it (or the first after it) in this list.
                    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
                    Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9 -> {
                        val d = e.nativeKeyEvent.number.takeIf { it.isDigit() } ?: return@onPreviewKeyEvent false
                        if (!first || list.isEmpty()) return@onPreviewKeyEvent true
                        typed = (typed + d).takeLast(ZAP_DIGITS_MAX)
                        val target = typed.toIntOrNull() ?: return@onPreviewKeyEvent true
                        val exact = list.indexOfFirst { it.number == target }
                        val at = if (exact >= 0) exact else list.indexOfFirst { (it.number ?: -1) >= target }.takeIf { it >= 0 } ?: list.lastIndex
                        column = 1; chIdx = at
                        true
                    }
                    Key.DirectionLeft -> {
                        when (column) {
                            2 -> column = 1
                            1 -> { column = 0; groupIdx = entries.indexOfFirst { it.item.key == shownKey }.takeIf { it >= 0 } ?: groupIdx }
                            // glcn: ◀ ◀ from the channels — up to the playlist chips, on the active one (as TiviMate).
                            0 -> if (chips.isNotEmpty()) { column = -1; chipIdx = chips.indexOfFirst { it.first == activeChip }.coerceAtLeast(0) }
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
                                if (schedCount > schedFirst) { column = 2; schedIdx = schedFirst } // glcn: into what follows, at the first of it
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
        // glcn: walking "Up next", the picture follows the picked programme (once the cursor rests on it), and goes
        // back to the one on air when the cursor leaves the list.
        LaunchedEffect(pickedProgramme?.id, column) {
            val p = pickedProgramme?.takeIf { column == 2 }
            if (p != null) { delay(BACKDROP_REST_MS); backdropProgramme = p }
            else restingChannel?.let { ch -> backdropProgramme = runCatching { cachedNow(ch).first }.getOrNull() }
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
            // glcn: wide enough for the categories' whole names (the panel covers the channels, so it can take their room).
            // glcn: the categories cover the channel list exactly — the same width as the sheet under them.
            val groupsOpen = chW + 20.mpx
            val slideGroupsPx = with(density) { 40.mpx.toPx() }
            Box(Modifier.width(0.mpx).fillMaxHeight().wrapContentWidth(Alignment.Start, unbounded = true).zIndex(1f)) {
            if (fold < 0.999f) Box(Modifier.width(groupsOpen).fillMaxHeight().graphicsLayer { alpha = 1f - fold; translationX = -slideGroupsPx * fold }) {
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(StageRadii.Sheet)).background(shade))
            // glcn: a tab on the panel's right edge with a › — ▶ goes back to the channels.
            tv.own.owntv.glcn.EdgeTab(visible = column == 0 || column == -1, toRight = true, modifier = Modifier.align(Alignment.CenterEnd))
            // glcn: ‹ on the left edge while on the categories: ◀ goes up to the playlists.
            if (chips.isNotEmpty()) tv.own.owntv.glcn.EdgeTab(visible = column == 0, modifier = Modifier.align(Alignment.CenterStart))
            Column(Modifier.fillMaxSize().padding(start = 14.mpx, end = 8.mpx + DECK_TAB_ROOM)) {
                if (chips.isNotEmpty()) {
                    DeckChips(chips, activeChip, if (column == -1) chipIdx else -1, colors = marks.mapValues { it.value.shownColor })
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
                    // glcn: a ‹ before the group's name says the categories are there, to the left (◀ opens them).
                    OwnTVIcon(OwnTVIcon.CHEVRON_LEFT, StageColors.Muted, Modifier.size(22.mpx))
                    Text(groupName, style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    shownEntry?.item?.sourceId?.let(marks::get)?.let { tv.own.owntv.ui.stage.StagePlaylistMark(it) }
                    Text(
                        pluralStringResource(R.plurals.content_live_channel_count, list.size, list.size),
                        style = stageText(17, 500), color = StageColors.Muted, maxLines = 1,
                    )
                }
                val playingIdx = remember(list, currentId) { list.indexOfFirst { it.id == currentId } }
                val playingName = list.getOrNull(playingIdx)?.let { ProviderTags.displayName(it.name) }.orEmpty()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                // glcn: a tab on the list's left edge says the categories are there; it nudges left as the list opens.
                tv.own.owntv.glcn.EdgeTab(visible = column == 1, modifier = Modifier.align(Alignment.CenterStart))
                GlideColumn(
                    count = list.size,
                    index = chIdx,
                    rowHeight = 84.mpx,
                    active = column == 1,
                    resetKey = listVersion,
                    modifier = Modifier.fillMaxSize().padding(start = DECK_TAB_ROOM),
                    pinIndex = playingIdx,
                    pinned = { above -> tv.own.owntv.glcn.WatchingPill(playingName, above) },
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
        // With the light theme the card is light too, so the text keeps the theme's own (dark) ink.
        val onArt = !tv.own.owntv.glcn.LowRam.on && !tv.own.owntv.ui.theme.LocalStagePalette.current.isLight
        if (pictureLocal.width > 0f) androidx.compose.runtime.CompositionLocalProvider(
            tv.own.owntv.ui.theme.LocalStagePalette provides if (onArt) tv.own.owntv.ui.theme.StagePalette.Dark else tv.own.owntv.ui.theme.LocalStagePalette.current,
        ) { with(density) {
            Column(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((pictureLocal.left + 28.mpx.toPx()).toInt(), (pictureLocal.bottom + 30.mpx.toPx()).toInt()) }
                    .width((pictureLocal.width - 56.mpx.toPx()).coerceAtLeast(0f).toDp())
                    .height((deckBox.height - pictureLocal.bottom - 30.mpx.toPx() - DECK_FOOT.toPx() - 18.mpx.toPx()).coerceAtLeast(0f).toDp())
                    .graphicsLayer { alpha = enter.value }
                    .clipToBounds(),
            ) {
                androidx.compose.animation.Crossfade(focusedChannel, modifier = Modifier.fillMaxWidth().weight(1f), animationSpec = tween(140), label = "deckDetail") { ch ->
                    if (ch != null) DeckInfoPanel(
                        channel = ch, loadNow = cachedNow, loadSchedule = loadSchedule,
                        peek = { nowCache[it.id] },
                        selected = if (column == 2) schedIdx else -1,
                        onRowCount = { schedCount = it },
                        onFirstRow = { schedFirst = it },
                        onPicked = { pickedProgramme = it },
                    )
                }
            }
        } }
        // glcn: the number being typed, a moment; it clears itself.
        LaunchedEffect(typed) { if (typed.isNotEmpty()) { delay(ZAP_DIGIT_HOLD_MS); typed = "" } }
        if (typed.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 40.mpx)
                    .background(Color(0xEE0E1726), RoundedCornerShape(18.mpx))
                    .padding(horizontal = 28.mpx, vertical = 10.mpx),
            ) {
                Text(typed, style = stageText(40, 800), color = Color.White, maxLines = 1)
            }
        }
    }
}

private const val ZAP_ACCEL_1_MS = 900L
private const val ZAP_ACCEL_2_MS = 2_200L
private const val ZAP_ACCEL_1_STEP = 3
private const val ZAP_ACCEL_2_STEP = 6
private const val ZAP_DIGITS_MAX = 4
private const val ZAP_DIGIT_HOLD_MS = 1_500L

/** glcn: the categories panel at least this wide, so their names read whole. */

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
 * glcn: a dark card under the playing picture, as wide as it, its foot rounded — the picture and what is on form one
 * piece. The programme's picture sits whole (16:9) at its right, melting into the card towards the left, where the
 * programme is told in light text.
 */
@Composable
private fun DeckBackdrop(url: String?, shade: Color, picture: androidx.compose.ui.geometry.Rect, hole: (androidx.compose.ui.geometry.Size) -> DeckHole) {
    if (tv.own.owntv.glcn.LowRam.on || picture.width <= 0f) return
    val density = LocalDensity.current
    val radius = with(density) { 18.mpx.toPx() }
    val top = picture.bottom - radius // tucked under the picture's rounded foot
    val footGap = with(density) { DECK_FOOT.toPx() }
    // glcn: the card follows the theme — light with the light theme; with the dark one, a deep tone taken from the
    // programme's own picture (navy when it has no colour). The veil over the picture is as strong as its brightness
    // needs for every word over it to read, and no stronger.
    val palette = tv.own.owntv.ui.theme.LocalStagePalette.current
    val light = palette.isLight
    // glcn: with the light theme the card is the channel list's own glass colour, so both read as one sheet.
    val lightCard = palette.glassTint.copy(alpha = 1f)
    val tone by tv.own.owntv.glcn.ArtTone.of(url)
    val card by animateColorAsState(
        if (light) lightCard else tone?.darkCard() ?: DECK_CARD,
        tween(600), label = "deckCard",
    )
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
                val w = picture.width.toInt().coerceAtLeast(1)
                val h = (c.maxHeight - top - footGap).toInt().coerceAtLeast(1)
                val pl = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                layout(c.maxWidth, c.maxHeight) { pl.place(picture.left.toInt(), top.toInt()) }
            }
                .clip(RoundedCornerShape(bottomStart = 18.mpx, bottomEnd = 18.mpx))
                .drawBehind { drawRect(card) },
        ) {
            androidx.compose.animation.Crossfade(url, animationSpec = tween(600), label = "deckBackdrop") { u ->
                if (u != null) androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    val artW = maxHeight * 16f / 9f
                    val artTone by tv.own.owntv.glcn.ArtTone.of(u)
                    val veil by androidx.compose.animation.core.animateFloatAsState(artTone?.veil(light) ?: VEIL_UNREAD, tween(300), label = "deckVeil")
                    Box(Modifier.align(Alignment.CenterEnd).width(artW).fillMaxHeight()) {
                        coil3.compose.AsyncImage(
                            model = u,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier.fillMaxSize().drawBehind {
                                // Melts into the card on its left; a light veil over the rest, for the list that crosses it.
                                drawRect(card.copy(alpha = veil))
                                drawRect(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        0f to card, 0.45f to card.copy(alpha = 0.55f), 1f to card.copy(alpha = 0f),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** glcn: the card under the picture, and the room left under it. */
private val DECK_CARD = Color(0xFF0E1726)
/** The veil until the picture has been read: strong, so text never shows unreadable for a moment. */
private const val VEIL_UNREAD = 0.7f
private val DECK_FOOT = 28.mpx

/**
 * glcn: a guide's synopsis without what the guide tacks on — "(Genre, Genre)" in front, "País: …", "Año: …" after —
 * returned apart: the genres (shown as a tag) and the text.
 */
internal fun cleanSynopsis(raw: String?): Pair<List<String>, String> {
    var text = raw?.trim().orEmpty()
    var genres = emptyList<String>()
    SYNOPSIS_GENRES.find(text)?.let { m ->
        genres = m.groupValues[1].split(',', '/').map { it.trim() }.filter { it.isNotEmpty() }
        text = text.substring(m.range.last + 1).trim()
    }
    text = text.replace(SYNOPSIS_FACTS, "").replace(SYNOPSIS_SPACES, " ").trim().trimEnd('·').trim()
    return genres to text
}

private val SYNOPSIS_GENRES = Regex("""^\(([^()]{2,60})\)""")
private val SYNOPSIS_FACTS = Regex("""\s*·?\s*\b(País|Año|Pais)\s*:\s*[^·\n]*""")
private val SYNOPSIS_SPACES = Regex("""[ \t]{2,}""")

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
    onFirstRow: (Int) -> Unit = {},
    onPicked: (EpgProgrammeEntity?) -> Unit = {},
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
        onFirstRow(if (rows.firstOrNull()?.let { now >= it.startMs } == true) 1 else 0)
        onRowCount(rows.size)
    }
    // The schedule's rows are the guide's light ones: the picked one's synopsis is read on its own.
    val guideReader: tv.own.owntv.core.live.GuideReader = org.koin.compose.koinInject()
    // A coming programme picked on the right (not the one on air, which the column already tells).
    val picked = rows.getOrNull(selected)?.takeIf { it.startMs > System.currentTimeMillis() }
    LaunchedEffect(picked?.id) { onPicked(picked) }
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
            val (genres, synopsis) = remember(p.description) { cleanSynopsis(p.description) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                Text(time, style = stageText(18, 500), color = StageColors.Muted, maxLines = 1)
                if (genres.isNotEmpty()) Text(
                    genres.take(2).joinToString(" · "), style = stageText(14, 700), color = StageColors.Text, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(8.mpx)).background(StageColors.Ink.copy(alpha = 0.12f)).padding(horizontal = 10.mpx, vertical = 3.mpx),
                )
            }
            if (live) {
                val span = (p.stopMs - p.startMs).coerceAtLeast(1L)
                val f = ((nowMs - p.startMs).toFloat() / span).coerceIn(0f, 1f)
                Box(Modifier.padding(vertical = 4.mpx).fillMaxWidth().height(5.mpx).clip(RoundedCornerShape(3.mpx)).background(StageColors.Ink.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(f).height(5.mpx).background(stageAccent.accent))
                }
            }
            synopsis.takeIf { it.isNotBlank() }?.let {
                Text(it, style = stageText(17, 400), color = StageColors.Body, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
            }
        }
        // ---- What follows.
        val coming = rows.withIndex().filter { (i, r) -> i > 0 || r.startMs > nowMs }
        if (coming.isNotEmpty()) Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                stringResource(R.string.content_live_next).uppercase(), style = stageText(16, 800), color = deckAccentInk(),
                maxLines = 1, modifier = Modifier.height(37.mpx).wrapContentHeight(Alignment.CenterVertically).padding(start = 12.mpx),
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
                                color = if (lit) stageAccent.onAccent else deckAccentInk(), maxLines = 1, modifier = Modifier.width(70.mpx),
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
    /** A row to keep in sight: when it is scrolled out, [pinned] is shown at the edge it went past (true = above). */
    pinIndex: Int = -1,
    pinned: (@Composable (Boolean) -> Unit)? = null,
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
        // glcn: the pinned row, when out of sight: a pill at the edge it went past. Only its side is read in composition.
        if (pinned != null && pinIndex in 0 until count) {
            val side by remember(resetKey, pinIndex) {
                derivedStateOf {
                    val mid = (pinIndex + 0.5f) * h - scroll.value
                    when { mid < 0f -> 1; mid > viewH -> 2; else -> 0 }
                }
            }
            val shownSide = remember { mutableIntStateOf(0) }
            if (side != 0) shownSide.intValue = side
            androidx.compose.animation.AnimatedVisibility(
                visible = side == 1,
                enter = androidx.compose.animation.fadeIn(tween(160)) + androidx.compose.animation.slideInVertically(tween(200)) { -it / 2 },
                exit = androidx.compose.animation.fadeOut(tween(140)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.mpx),
            ) { pinned(true) }
            androidx.compose.animation.AnimatedVisibility(
                visible = side == 2,
                enter = androidx.compose.animation.fadeIn(tween(160)) + androidx.compose.animation.slideInVertically(tween(200)) { it / 2 },
                exit = androidx.compose.animation.fadeOut(tween(140)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.mpx),
            ) { pinned(false) }
        }
    }
}

private const val PLAYING_WASH = 0.16f

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
                // glcn: the playing channel — a soft accent wash over the whole row (the focus is the solid one).
                if (playing && !lit) Modifier.drawBehind {
                    drawRoundRect(accent.copy(alpha = PLAYING_WASH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.mpx.toPx()))
                } else Modifier,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.mpx),
    ) {
        if (showNumber) {
            Text(channel.number?.toString().orEmpty(), style = stageText(17, 700), color = sub, maxLines = 1, modifier = Modifier.width(50.mpx))
        }
        // glcn: the lit row is the solid accent — the logo goes on it as on any focused fill.
        androidx.compose.runtime.CompositionLocalProvider(tv.own.owntv.ui.components.LocalLogoInk provides (if (lit) stageAccent.onAccent else null)) {
            ChannelLogoTile(logoUrl = channel.displayLogoUrl, modifier = Modifier.width(72.mpx).height(48.mpx), fill = Color.Transparent) { }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                if (playing) tv.own.owntv.glcn.EqualizerBars(if (lit) stageAccent.onAccent else accent, Modifier.size(18.mpx))
                Text(ProviderTags.displayName(channel.name), style = stageText(20, if (playing) 800 else 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
private fun DeckChips(chips: List<Pair<Long, String>>, activeId: Long, cursor: Int, colors: Map<Long, Color> = emptyMap()) {
    val appAccent = stageAccent.accent
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
            // glcn: a playlist's chip wears that playlist's own colour (its tag's); "All" the app's accent.
            val accent = colors[id] ?: appAccent
            val onChip = if (accent.luminance() > 0.55f) Color(0xFF111418) else Color.White
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
                color = if (focused) onChip else if (on) StageColors.Text else StageColors.Muted,
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

/** glcn: the accent as text on the deck's card — the theme's ink instead when the accent would vanish on it (a white accent on the light card, a near-black one on the dark). */
@Composable
private fun deckAccentInk(): Color {
    val a = stageAccent.accent
    val light = tv.own.owntv.ui.theme.LocalStagePalette.current.isLight
    val l = a.luminance()
    return if ((light && l > 0.5f) || (!light && l < 0.06f)) StageColors.Text else a
}

/** glcn: room on the channel list's left for the categories tab. */
private val DECK_TAB_ROOM = tv.own.owntv.glcn.EDGE_TAB_ROOM

