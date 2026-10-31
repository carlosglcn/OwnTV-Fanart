package tv.own.owntv.features.home

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.SportsNow
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.stage.StagePill
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn (TV profile): a Home made only for watching TV — "the TV already on". The focused channel plays behind
 * everything (the live preview engine, sound as the Live preview is set), its programme picture until the video
 * starts; over it, on a panel of its own, the programme — channel, title, how long is left, synopsis and what
 * comes next. Along the bottom, your channels by group as picture cards; ◀ ▶ (or CH±) move along them and the
 * picture follows once the focus rests; OK goes full screen on the stream already playing, with no new start.
 * The same channel in HD and SD shows as one card (the HD one).
 */
@Composable
internal fun TvHome(
    load: suspend () -> List<LiveViewModel.StripGroup>,
    loadNow: suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>,
    onTune: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onChildFocused: () -> Unit,
    contentStart: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    /** The live engine the focused channel plays on behind Home; null keeps the programme pictures only. */
    previewEngine: tv.own.owntv.player.LivePreviewEngine? = null,
    /** Start [previewEngine] on a channel (muted or not as the Live preview is set). */
    playPreview: (ChannelEntity) -> Unit = {},
    /** glcn: the guide-made rows (Starting now, Films, Sport, Tonight) for these channels. */
    loadSmart: suspend (List<ChannelEntity>) -> List<LiveViewModel.StripGroup> = { emptyList() },
) {
    val groups by produceState<List<LiveViewModel.StripGroup>?>(null) {
        val plain = runCatching { load() }.getOrDefault(emptyList())
            .map { it.copy(channels = oneCardPerChannel(it.channels)) }.filter { it.channels.isNotEmpty() }
        value = plain
        // glcn: then the rows that say something — Starting now, Films on air, Sport on air, Tonight — ahead of the groups.
        val smart = runCatching { loadSmart(plain.flatMap { it.channels }) }.getOrDefault(emptyList())
            .map { g -> g.copy(channels = oneCardPerChannel(g.channels)) }.filter { it.channels.isNotEmpty() }
        if (smart.isNotEmpty()) value = smart + plain
    }
    var selected by remember { mutableIntStateOf(0) }
    val nowNext = remember { mutableStateMapOf<Long, Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>>() }
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    // glcn: the programme that put the focused card in its row (Tonight's 22:00 film), shown in the panel instead of now.
    var focusedShow by remember { mutableStateOf<EpgProgrammeEntity?>(null) }
    var focusedArt by remember { mutableStateOf<String?>(null) }
    // The picture changes once the focus rests, so running along a row does not flash a picture per card.
    var shownArt by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedArt) { delay(ART_SETTLE_MS); shownArt = focusedArt }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { clock = System.currentTimeMillis(); delay(CLOCK_TICK_MS) } }
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(groups != null) {
        if (groups != null) { androidx.compose.runtime.withFrameNanos { }; runCatching { firstCard.requestFocus() } }
    }
    val context = LocalContext.current
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val page = tv.own.owntv.ui.theme.stageTone(Color(0xFF05080B), tv.own.owntv.ui.theme.LocalStagePalette.current.page)
    val animate = tv.own.owntv.ui.theme.animationsOn && !tv.own.owntv.glcn.LowRam.on
    // The focused channel starts behind Home once the focus rests on it — running along the row tunes nothing.
    LaunchedEffect(focusedChannel?.id, previewEngine != null) {
        val ch = focusedChannel ?: return@LaunchedEffect
        if (previewEngine == null) return@LaunchedEffect
        delay(PREVIEW_SETTLE_MS)
        playPreview(ch)
    }
    val previewState = previewEngine?.state?.collectAsStateWithLifecycle()?.value
    val videoOn = previewState != null &&
        previewState != tv.own.owntv.player.LivePreviewEngine.State.IDLE &&
        previewState != tv.own.owntv.player.LivePreviewEngine.State.ERROR &&
        previewState != tv.own.owntv.player.LivePreviewEngine.State.LOADING
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Box(modifier.fillMaxSize().background(page).onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        // The programme's picture, edge to edge, drifting closer.
        Crossfade(shownArt, animationSpec = tv.own.owntv.ui.theme.ownTvTween(900), label = "tvHomeArt") { url ->
            if (!url.isNullOrBlank()) {
                val drift = remember(url) { androidx.compose.animation.core.Animatable(1f) }
                LaunchedEffect(url, animate) {
                    if (animate) drift.animateTo(1.08f, androidx.compose.animation.core.tween(DRIFT_MS, easing = androidx.compose.animation.core.LinearEasing))
                }
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = drift.value; scaleY = drift.value },
                )
            }
        }
        // The channel itself, live, over its picture once the video runs.
        if (previewEngine != null) {
            androidx.compose.animation.AnimatedVisibility(
                visible = videoOn,
                enter = androidx.compose.animation.fadeIn(tv.own.owntv.ui.theme.ownTvTween(600)),
                exit = androidx.compose.animation.fadeOut(tv.own.owntv.ui.theme.ownTvTween(300)),
            ) { tv.own.owntv.player.ExoPreviewSurface(engine = previewEngine, modifier = Modifier.fillMaxSize()) }
        }
        // Only the bottom is shaded, for the cards; the picture itself stays whole (the text has its own panel).
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 0.78f to page.copy(alpha = 0.6f), 1f to page.copy(alpha = 0.92f))))


        // The programme on the focused channel.
        val ch = focusedChannel
        val (now, next) = focusedShow?.let { it to null } ?: ch?.let { nowNext[it.id] } ?: (null to null)
        if (ch != null) Column(
            Modifier.align(Alignment.TopStart).padding(start = contentStart, top = 170.mpx).widthIn(max = 1040.mpx)
                // A panel of the page colour behind the text: readable in light and dark over any picture or video.
                .clip(RoundedCornerShape(28.mpx)).background(page.copy(alpha = 0.78f)).padding(horizontal = 28.mpx, vertical = 24.mpx),
            verticalArrangement = Arrangement.spacedBy(12.mpx),
        ) {
            run {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    tv.own.owntv.ui.components.ChannelLogoTile(
                        ch.displayLogoUrl, Modifier.size(88.mpx, 56.mpx).clip(RoundedCornerShape(10.mpx)),
                    ) { Text(ch.name.take(2), style = stageText(20, 800), color = StageColors.Text) }
                    Column {
                        Text(
                            now?.takeIf { it.startMs > clock }?.let { timeFormat.format(java.util.Date(it.startMs)) } ?: stringResource(R.string.content_epg_now_short),
                            style = stageText(16, 800), color = stageAccent.accent, maxLines = 1,
                        )
                        Text(ch.name, style = stageText(22, 700), color = StageColors.Text.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    now?.title?.let(SportsNow::displayTitle) ?: ch.name,
                    style = stageText(52, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                now?.let { p ->
                    val total = (p.stopMs - p.startMs).coerceAtLeast(1L)
                    val done = ((clock - p.startMs).toFloat() / total).coerceIn(0f, 1f)
                    val leftMin = ((p.stopMs - clock) / 60_000L).coerceAtLeast(0L).toInt()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                        Text(
                            timeFormat.format(java.util.Date(p.startMs)) + " – " + timeFormat.format(java.util.Date(p.stopMs)),
                            style = stageText(20, 600), color = StageColors.Text.copy(alpha = 0.85f), maxLines = 1,
                        )
                        StageProgress(done, Modifier.width(220.mpx))
                        Text(
                            stringResource(R.string.home_time_left, tv.own.owntv.features.shell.components.vodRuntime(leftMin * 60)),
                            style = stageText(20, 600), color = StageColors.Text.copy(alpha = 0.85f), maxLines = 1,
                        )
                    }
                    p.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = stageText(19, 400), color = StageColors.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                next?.let { n ->
                    Text(
                        stringResource(R.string.content_live_next) + "  " + timeFormat.format(java.util.Date(n.startMs)) + "  " + SportsNow.displayTitle(n.title),
                        style = stageText(19, 600), color = StageColors.Text.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Your channels, by group, as picture cards.
        val list = groups.orEmpty()
        val group = list.getOrNull(selected.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 40.mpx),
            verticalArrangement = Arrangement.spacedBy(16.mpx),
        ) {
            LazyRow(
                contentPadding = PaddingValues(start = contentStart + 24.mpx, end = 64.mpx),
                horizontalArrangement = Arrangement.spacedBy(12.mpx),
                modifier = Modifier.focusRestorer().focusGroup(),
            ) {
                itemsIndexed(list) { i, g ->
                    StagePill(
                        text = tvHomeTitle(g),
                        onClick = { selected = i; runCatching { firstCard.requestFocus() } },
                        small = g.channels.size.toString(),
                        highlighted = i == selected,
                        modifier = Modifier.onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { selected = i; runCatching { firstCard.requestFocus() }.isSuccess } else false
                        }.onFocusChanged { if (it.hasFocus) selected = i },
                    )
                }
            }
            group?.let { g ->
                androidx.compose.runtime.key(g) {
                    LazyRow(
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(start = contentStart + 24.mpx, end = 64.mpx, top = 16.mpx, bottom = 16.mpx),
                        horizontalArrangement = Arrangement.spacedBy(20.mpx),
                        modifier = Modifier.fillMaxWidth().focusRestorer().focusGroup().onPreviewKeyEvent { e ->
                            // CH+ / CH- step along the channels, as on a TV remote.
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.ChannelUp -> { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right); true }
                                Key.ChannelDown -> { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left); true }
                                else -> false
                            }
                        },
                    ) {
                        itemsIndexed(g.channels, key = { _, c -> c.id }) { i, c ->
                            LaunchedEffect(c.id) { if (c.id !in nowNext) nowNext[c.id] = runCatching { loadNow(c) }.getOrDefault(null to null) }
                            val pair = g.shows[c.id]?.let { it to null } ?: nowNext[c.id]
                            CompositionLocalProvider(LocalHomeFocusArt provides { art -> focusedArt = art }) {
                                ArtLiveCard(
                                    channel = c,
                                    programme = pair?.first,
                                    now = clock,
                                    // glcn: a programme still to come (Starting now, Tonight) says when it starts.
                                    startLabel = pair?.first?.takeIf { it.startMs > clock }?.let { timeFormat.format(java.util.Date(it.startMs)) },
                                    onClick = { onTune(c, g.channels) },
                                    width = TV_CARD_WIDTH.mpx,
                                    modifier = (if (i == 0) Modifier.focusRequester(firstCard) else Modifier)
                                        .onFocusChanged { if (it.hasFocus) { focusedChannel = c; focusedShow = g.shows[c.id]; if (pair?.first == null) focusedArt = null } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun tvHomeTitle(g: LiveViewModel.StripGroup): String = when (g.kind) {
    LiveViewModel.StripKind.FAVORITES -> stringResource(R.string.glcn_strip_my_channels)
    LiveViewModel.StripKind.GROUP -> g.title.orEmpty()
    LiveViewModel.StripKind.RECENT -> stringResource(R.string.content_category_recently_watched)
    LiveViewModel.StripKind.ALL -> stringResource(R.string.content_category_all_channels)
    LiveViewModel.StripKind.STARTING -> stringResource(R.string.glcn_tvhome_starting)
    LiveViewModel.StripKind.FILMS -> stringResource(R.string.glcn_tvhome_films)
    LiveViewModel.StripKind.SPORTS -> stringResource(R.string.glcn_tvhome_sports)
    LiveViewModel.StripKind.TONIGHT -> stringResource(R.string.glcn_tvhome_tonight)
}

/**
 * One card per channel: "La 1 HD" and "LA 1", or "M+ Estrenos" and "M+ Estrenos HD", are the same channel twice.
 * Names are compared without quality marks, case, accents or punctuation; the HD one is kept, in the place of the first.
 */
internal fun oneCardPerChannel(channels: List<ChannelEntity>): List<ChannelEntity> {
    if (channels.size < 2) return channels
    val order = LinkedHashMap<String, ChannelEntity>()
    for (c in channels) {
        val key = sameChannelKey(tv.own.owntv.core.live.ProviderTags.parse(c.name).name)
        if (key.isEmpty()) { order["#" + c.id] = c; continue }
        val kept = order[key]
        if (kept == null) order[key] = c
        else if (!isHd(kept) && isHd(c)) order[key] = c
    }
    return order.values.toList()
}

private val QUALITY_WORDS = Regex("\\b(uhd|fhd|hd|sd|4k|1080p?|720p?|hevc|h265)\\b")
private val HD_WORDS = Regex("\\b(uhd|fhd|hd|4k|1080p?)\\b")
private val NOT_ALNUM = Regex("[^a-z0-9+]")
private val MARKS = Regex("\\p{M}+")
private val HD_TAGS = setOf("HD", "FHD", "UHD", "4K")

private fun sameChannelKey(name: String): String {
    val plain = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD).replace(MARKS, "")
    return plain.replace(QUALITY_WORDS, " ").replace(NOT_ALNUM, "")
}

private fun isHd(c: ChannelEntity): Boolean {
    val parsed = tv.own.owntv.core.live.ProviderTags.parse(c.name)
    return parsed.tags.any { it in HD_TAGS } || HD_WORDS.containsMatchIn(parsed.name.lowercase())
}

private const val TV_CARD_WIDTH = 340
private const val PREVIEW_SETTLE_MS = 900L
private const val ART_SETTLE_MS = 450L
private const val CLOCK_TICK_MS = 15_000L
private const val DRIFT_MS = 14_000
