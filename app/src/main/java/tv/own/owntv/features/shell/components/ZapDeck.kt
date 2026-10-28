package tv.own.owntv.features.shell.components

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

    // 0 = groups, 1 = channels, -1 = the playlist chips.
    var column by remember { mutableIntStateOf(1) }
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
        val window = list.subList((chIdx - 30).coerceAtLeast(0), (chIdx + 31).coerceAtMost(list.size)).filter { it.id !in nowMap }
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
    if (detail == 1) LaunchedEffect(chIdx, listVersion) {
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
    val shade = tv.own.owntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .gradientWash(false, 0f to shade.copy(alpha = 0.78f), 0.55f to shade.copy(alpha = 0.5f), 0.85f to Color.Transparent)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val first = e.nativeKeyEvent.repeatCount == 0
                when (e.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        val up = e.key == Key.DirectionUp
                        when (column) {
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
                            1 -> { column = 0; groupIdx = entries.indexOfFirst { it.item.key == shownKey }.takeIf { it >= 0 } ?: groupIdx }
                            -1 -> chipIdx = (chipIdx - 1).coerceAtLeast(0)
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        when (column) {
                            0 -> if (list.isNotEmpty() && entries.getOrNull(groupIdx)?.item?.key == shownKey) column = 1
                            // ▶ from the channels gives the picture back, as Back does.
                            1 -> if (first) { if (onCycleDetail != null) onCycleDetail() else onDismiss() }
                            -1 -> chipIdx = (chipIdx + 1).coerceAtMost(chips.lastIndex)
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (first) when (column) {
                            1 -> tuneFocused()
                            0 -> if (list.isNotEmpty() && entries.getOrNull(groupIdx)?.item?.key == shownKey) column = 1
                            -1 -> chips.getOrNull(chipIdx)?.let { (id, _) -> playlists?.onSelect?.invoke(id); column = 0; groupIdx = 0 }
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Row(
            Modifier
                .graphicsLayer { translationX = (1f - enter.value) * -slidePx }
                .align(Alignment.CenterStart)
                .padding(horizontal = 14.mpx, vertical = 8.mpx)
                .fillMaxHeight(),
        ) {
            // glcn: both parts on a share of the same glass, so the picture shows through everywhere: the groups and
            // channels on a little more of it, so their words still read; the column on the right on less.
            Box(Modifier.fillMaxHeight()) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = DECK_LIST_GLASS }.stageGlass(StageRadii.Sheet, overContent = true))
            Row(Modifier.fillMaxHeight().padding(vertical = 20.mpx)) {
            // ---- Groups (and the playlists above them).
            Column(Modifier.width(280.mpx).fillMaxHeight().padding(start = 14.mpx, end = 8.mpx)) {
                if (chips.isNotEmpty()) {
                    DeckChips(chips, activeChip, if (column == -1) chipIdx else -1)
                    Spacer(Modifier.height(10.mpx))
                }
                Text(
                    stringResource(R.string.content_category_browser_title),
                    style = stageText(17, 700), color = StageColors.Dim, maxLines = 1,
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
                    val entry = entries[i]
                    val shown = entry.item.key == shownKey
                    val tint by animateColorAsState(
                        when {
                            focused && column == 0 -> stageAccent.onAccent
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
                        Text(
                            entry.label, style = stageText(19, if (shown) 800 else 600), color = tint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        entry.count?.let { Text(it.toString(), style = stageText(15, 500), color = tint.copy(alpha = 0.7f), maxLines = 1) }
                    }
                }
            }
            DeckDivider()
            // ---- Channels of the shown group.
            Column(Modifier.width(570.mpx).fillMaxHeight().padding(horizontal = 10.mpx)) {
                val groupName = entries.firstOrNull { it.item.key == shownKey }?.label.orEmpty()
                Row(
                    Modifier.padding(start = 14.mpx, end = 14.mpx, bottom = 12.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.mpx),
                ) {
                    Text(groupName, style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
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
                    DeckChannelRow(
                        channel = list[i],
                        now = nowMap[list[i].id],
                        playing = list[i].id == currentId,
                        lit = focused && column == 1,
                        showNumber = showNumbers,
                    )
                }
                StageKeyHints(
                    listOfNotNull(
                        stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
                        "◀" to stringResource(R.string.content_category_browser_title),
                        if (onCycleDetail != null) "▶" to stringResource(R.string.glcn_zap_view_next) else null,
                        (if (onCycleDetail != null) stringResource(R.string.common_back) else "▶ / " + stringResource(R.string.common_back)) to stringResource(R.string.content_close),
                    ),
                    textSize = 15,
                    modifier = Modifier.padding(start = 14.mpx, top = 12.mpx),
                )
            }
            }
            }
            // ---- The focused channel: its programme, or its schedule.
            val focusedChannel = list.getOrNull(chIdx)
            Box(Modifier.fillMaxHeight().padding(start = 8.mpx)) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = DECK_DETAIL_GLASS }.stageGlass(StageRadii.Sheet, overContent = true))
            Row(Modifier.fillMaxHeight().padding(vertical = 20.mpx)) {
            if (detail == 1) {
                ZapSchedulePanel(channel = focusedChannel, loadSchedule = loadSchedule, modifier = Modifier.width(480.mpx), loadNow = cachedNow, joined = true, peek = { nowCache[it.id] })
            } else {
                Column(Modifier.width(480.mpx).fillMaxHeight().padding(horizontal = 22.mpx)) {
                    focusedChannel?.let { ch ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.mpx),
                            modifier = Modifier.padding(bottom = 14.mpx),
                        ) {
                            ChannelLogoTile(logoUrl = ch.displayLogoUrl, modifier = Modifier.width(70.mpx).height(46.mpx), fill = Color.Transparent) { }
                            Text(ProviderTags.displayName(ch.name), style = stageText(22, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    androidx.compose.animation.Crossfade(focusedChannel, animationSpec = tween(140), label = "deckDetail") { ch ->
                        if (ch != null) ZapPreviewPanel(
                            channel = ch, loadNow = cachedNow, framed = false, bareAspect = 16f / 9f, bareLines = 5,
                            peek = { nowCache[it.id] },
                        )
                    }
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
            scroll.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f))
        }
        LaunchedEffect(resetKey, index) {
            glide.animateTo(index * h, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f))
        }
        // glcn: the list is laid out again only when the glide passes a whole row (it is placed on that row);
        // the part of a row in between is a translation of the drawn layer, read in drawing. Placing the list
        // on every frame (as before) measured it again sixty times a second, and in whole pixels, which is
        // what made the scroll judder, worst of all on slower boxes.
        val firstRow by remember(resetKey) { derivedStateOf { (scroll.value / h).toInt().coerceAtLeast(0) } }
        LaunchedEffect(listState, firstRow) { runCatching { listState.scrollToItem(firstRow, 0) } }
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
                Box(Modifier.fillMaxWidth().height(rowHeight)) { row(i, i == index) }
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
            val (id, name) = chips[i]
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

@Composable
private fun DeckDivider() {
    val line = StageColors.Ink.copy(alpha = 0.12f)
    Box(
        Modifier
            .fillMaxHeight()
            .width(1.mpx)
            .padding(vertical = 26.mpx)
            .background(line),
    )
}

/** glcn: how much of the stage glass the panel draws: the list keeps enough to read; the detail lets more picture through. */
private const val DECK_LIST_GLASS = 0.74f
private const val DECK_DETAIL_GLASS = 0.5f
