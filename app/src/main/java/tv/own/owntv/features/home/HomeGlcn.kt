package tv.own.owntv.features.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import tv.own.owntv.ui.stage.stageTilt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.home.FeaturedItem
import tv.own.owntv.core.home.TonightItem
import tv.own.owntv.core.live.SportsNow
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.ProgrammeArt
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.backdropLeftWash
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.dissolveEdges
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: Home's renewed pieces — the featured billboard, live cards drawn from the programme's
 * picture, "Tonight", the greeting, and the backdrop that follows the focused card.
 */

/** A card tells Home which picture it shows when it takes focus; Home paints it behind the rows. */
internal val LocalHomeFocusArt = staticCompositionLocalOf<(String?) -> Unit> { {} }

/** The focused card's picture, top right, dissolving into the page, with the page tinted to match. */
@Composable
internal fun HomeFocusBackdrop(url: String?, modifier: Modifier = Modifier, trailer: String? = null, trailerTitle: String? = null, focusKey: Any? = null) {
    var settled by remember { mutableStateOf(url) }
    // glcn: long enough that holding ◀ ▶ across a row does not load a picture per card.
    LaunchedEffect(url) { if (url != settled) { delay(CLASSIC_BACKDROP_SETTLE_MS); settled = url } }
    Box(modifier.fillMaxSize()) {
        tv.own.owntv.ui.components.ArtTint(settled, Modifier.fillMaxSize(), strength = 0.55f)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.6f)
                .height(560.mpx)
                .dissolveEdges(start = 0.45f, bottom = 0.45f),
        ) {
            val animate = tv.own.owntv.ui.theme.animationsOn
            Crossfade(targetState = settled, label = "homeFocusArt") { u ->
                if (!u.isNullOrBlank()) {
                    // glcn: a bright picture is brought down to the page, so cards always read over it.
                    val glow = tv.own.owntv.ui.components.rememberArtColor(u)?.luminance() ?: 0.3f
                    // glcn: the backdrop drifts slowly closer too, in step with the focused card.
                    val drift = remember(u) { androidx.compose.animation.core.Animatable(1f) }
                    val driftOn = animate && !tv.own.owntv.ui.stage.GlcnMotion.subtle
                    LaunchedEffect(u, driftOn) {
                        if (driftOn) drift.animateTo(1.06f, androidx.compose.animation.core.tween(8_000, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                    }
                    AsyncImage(
                        model = u, contentDescription = null, contentScale = ContentScale.Crop,
                        alignment = androidx.compose.ui.BiasAlignment(0f, -0.4f),
                        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = drift.value; scaleY = drift.value },
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (0.1f + glow * 0.5f).coerceAtMost(0.5f))))
                }
            }
            Box(Modifier.fillMaxSize().backdropLeftWash(0f to 0.85f, 0.35f to 0.45f, 0.7f to 0.15f, 1f to 0f))
            // glcn: the focused film's trailer in the same window, silent, after a few calm seconds.
            FocusTrailer(trailer, trailerTitle, focusKey, Modifier.fillMaxSize(), dissolve = false)
        }
    }
}

/** "Buenas noches, Carlos" and the time, above the first row. */
@Composable
internal fun HomeGreeting(name: String?, start: Dp, mark: String? = null) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val hour = remember(now) { java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when (hour) {
            in 6..13 -> R.string.glcn_greeting_morning
            in 14..20 -> R.string.glcn_greeting_afternoon
            else -> R.string.glcn_greeting_evening
        },
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val date = remember(now) { java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault()).format(java.util.Date(now)) }
    Column(Modifier.padding(start = start, bottom = 6.mpx)) {
        Text(
            listOfNotNull(greeting, name?.takeIf { it.isNotBlank() }).joinToString(", ") + (mark?.let { "  $it" } ?: ""),
            style = stageText(34, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            fmt.format(java.util.Date(now)) + "  ·  " + date.replaceFirstChar { it.uppercase() },
            style = stageText(18, 500), color = StageColors.Muted, maxLines = 1,
        )
    }
}

/** glcn: the live cards' width when a style sets it (Panel fits exactly three beside its panel); null = 400. */
internal val LocalHomeCardWidth = androidx.compose.runtime.compositionLocalOf<Dp?> { null }

/** Room under a picture card for its title and line. */
private val CAPTION_HEIGHT = 74.mpx

/** "EN DIRECTO" in a red pill. */
@Composable
internal fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.glcn_live_badge),
        style = stageText(13, 800),
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(6.mpx))
            .background(Color(0xFFE5243B))
            .padding(horizontal = 10.mpx, vertical = 3.mpx),
    )
}

/**
 * A live programme as a picture card: the programme's fanart filling the card, the channel logo in the
 * corner, title and time over a dark foot, a progress bar — or the channel logo alone when the guide
 * has no picture. [startLabel] replaces the time left for an upcoming programme ("21:30").
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ArtLiveCard(
    channel: ChannelEntity,
    programme: EpgProgrammeEntity?,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    width: Dp = LocalHomeCardWidth.current ?: 400.mpx,
    startLabel: String? = null,
    bell: Boolean = false,
    /** glcn: Recent channels — "watched 10 min ago", added to the line under the title. */
    watchedAgo: String? = null,
    /** glcn: pinned to the front of Recent channels. */
    pinned: Boolean = false,
) {
    val a = stageAccent
    val setArt = LocalHomeFocusArt.current
    val setInfo = LocalHomeFocusInfo.current
    val rawArt = tv.own.owntv.ui.components.programmeArtOrTmdb(programme)
    // A blank placeholder picture reads as a broken card: the channel's logo stands in for it.
    val art = rawArt?.takeUnless { tv.own.owntv.ui.components.rememberPlainArt(it) }
    var held by remember { mutableStateOf(false) }
    StageSurface(
        onClick = onClick,
        radius = StageRadii.Row,
        modifier = modifier
            .size(width, width * 9f / 16f + CAPTION_HEIGHT)
            .onFocusChanged {
                held = it.hasFocus
                if (it.hasFocus) {
                    // glcn: a card with no picture of its own keeps the backdrop that is there.
                    if (art != null) setArt(art)
                    setInfo(
                        HomeFocusInfo(
                            title = programme?.title?.let(SportsNow::displayTitle) ?: channel.name,
                            line = if (startLabel != null) "$startLabel · ${channel.name}" else channel.name,
                            plot = programme?.description,
                            rateTitle = programme?.title,
                            rateCategories = programme?.categories,
                            channel = channel.takeIf { startLabel == null },
                            art = art,
                            programmeId = programme?.id,
                        ),
                    )
                }
            }
            .stageTilt(held),
        onLongClick = onLongClick,
        focusStyle = StageFocus.FX,
        idle = Modifier.background(StageColors.Ink.copy(alpha = 0.06f), RoundedCornerShape(StageRadii.Row)),
        contentAlignment = Alignment.TopStart,
    ) { focused ->
        val animate = tv.own.owntv.ui.theme.animationsOn
        // glcn: a focused card's picture drifts slowly closer (a Ken Burns push-in)…
        val zoom by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (focused && animate) (if (tv.own.owntv.ui.stage.GlcnMotion.subtle) 1.03f else 1.08f) else 1f,
            animationSpec = androidx.compose.animation.core.tween(if (focused) 6_000 else 400, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
            label = "cardZoom",
        )
        // …and a soft band of light crosses it once as focus lands.
        val shine = remember { androidx.compose.animation.core.Animatable(-1f) }
        val sweep = animate && !tv.own.owntv.ui.stage.GlcnMotion.subtle
        LaunchedEffect(focused, sweep) {
            if (focused && sweep) {
                shine.snapTo(-0.6f)
                shine.animateTo(1.6f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            } else shine.snapTo(-1f)
        }
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(StageRadii.Row))) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(width * 9f / 16f)
                    .clip(RoundedCornerShape(topStart = StageRadii.Row, topEnd = StageRadii.Row)),
            ) {
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }) {
                    if (art != null) {
                        ProgrammeArt(url = art, modifier = Modifier.fillMaxSize())
                    } else {
                        // glcn: no picture — the channel's own colour, from its logo, behind the logo; never a blank card.
                        val logoTone = tv.own.owntv.ui.components.rememberArtColor(channel.displayLogoUrl)
                            ?.takeIf { it.luminance() in 0.03f..0.75f } ?: Color(0xFF2A3340)
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.linearGradient(
                                    listOf(
                                        androidx.compose.ui.graphics.lerp(logoTone, Color.Black, 0.3f),
                                        androidx.compose.ui.graphics.lerp(logoTone, Color.Black, 0.78f),
                                    ),
                                ),
                            ),
                        )
                        ChannelPlate(channel, Modifier.align(Alignment.Center).size(150.mpx, 96.mpx))
                    }
                }
                if (shine.value > -0.9f && shine.value < 1.5f) {
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            val x = size.width * shine.value
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                                    start = androidx.compose.ui.geometry.Offset(x - size.width * 0.35f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x + size.width * 0.05f, size.height),
                                ),
                            )
                        },
                    )
                }
                if (art != null) ChannelPlate(channel, Modifier.padding(12.mpx).size(66.mpx, 44.mpx))
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.mpx),
                    horizontalArrangement = Arrangement.spacedBy(8.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (programme != null && startLabel == null && SportsNow.isLive(programme)) LiveBadge()
                    if (pinned) Box(
                        Modifier.size(34.mpx).clip(RoundedCornerShape(17.mpx)).background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) { tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.STAR, a.accent, Modifier.size(18.mpx), filled = true) }
                    if (bell) {
                        Box(
                            Modifier.size(34.mpx).clip(RoundedCornerShape(17.mpx)).background(a.accent),
                            contentAlignment = Alignment.Center,
                        ) { tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.BELL, Color.White, Modifier.size(20.mpx)) }
                    }
                }
                if (programme != null && startLabel == null) {
                    val span = (programme.stopMs - programme.startMs).coerceAtLeast(1L)
                    val p = ((now - programme.startMs).toFloat() / span).coerceIn(0f, 1f)
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.mpx).background(Color.Black.copy(alpha = 0.4f))) {
                        Box(Modifier.fillMaxWidth(p).height(5.mpx).background(Brush.horizontalGradient(listOf(a.accent, a.focus))))
                    }
                }
            }
            // The words under the picture, never over it, where the picture's own lettering would fight them.
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.mpx, vertical = 10.mpx)) {
                // glcn: a focused card whose words do not fit lets them drift past, smoothly, then start over.
                val drift = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = tv.own.owntv.glcn.DriftPace.START_MS, repeatDelayMillis = 1_500, velocity = tv.own.owntv.glcn.DriftPace.velocity()) else Modifier
                Text(
                    programme?.title?.let(SportsNow::displayTitle) ?: channel.name,
                    style = stageText(20, 800), color = StageColors.Text, maxLines = 1,
                    overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = drift,
                )
                val line = when {
                    startLabel != null -> "$startLabel · ${channel.name}"
                    programme != null -> channel.name + " · " + timeLeftText(((programme.stopMs - now).coerceAtLeast(0L) + 59_999L) / 60_000L)
                    else -> null
                }.let { l -> if (watchedAgo != null) listOfNotNull(l ?: channel.name, watchedAgo).joinToString(" · ") else l }
                line?.let {
                    Text(it, style = stageText(15, 500), color = StageColors.Muted, maxLines = 1, overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis, modifier = drift)
                }
            }
        }
    }
}

/** A live rail drawn as picture cards ("On now" mode of Recent, Favourite channels and Sports now). */
@Composable
internal fun ArtLiveRow(
    title: String,
    channels: List<ChannelEntity>,
    programmes: Map<Long, List<EpgProgrammeEntity>>,
    now: Long,
    onChannelClick: (Long, List<ChannelEntity>) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    start: Dp,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((ChannelEntity) -> Unit)? = null,
    /** glcn: Recent channels — when each was last watched, and which are pinned. */
    watchedAt: Map<Long, Long> = emptyMap(),
    pinned: (ChannelEntity) -> Boolean = { false },
) {
    if (channels.isEmpty()) return
    Column(modifier) {
        HomeRowHeader(title, small = stringResource(R.string.home_row_on_now), start = start)
        // glcn: a channel that moves to the front (the one just watched) shows as the first card, whole,
        // instead of the row holding its old place and leaving it half off the left edge.
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        // glcn: what is on changes under the cursor (a programme ends, the guide refreshes) and the focused card can
        // leave the row. Its focus then went nowhere and the next key sent Home back to the top; now the card that
        // takes its place (or the last one) gets it, and the page stays on this row.
        val cardFocus = remember { HashMap<Long, FocusRequester>() }
        val focusedAt = remember { intArrayOf(-1) }
        val focusedId = remember { longArrayOf(Long.MIN_VALUE) }
        // When the row last lost focus: the loss caused by the card going happens in the same frame as the change.
        val lostAtMs = remember { longArrayOf(0L) }
        LaunchedEffect(channels) {
            val justLost = android.os.SystemClock.uptimeMillis() - lostAtMs[0] < FOCUS_LOSS_WINDOW_MS
            val lost = justLost && focusedAt[0] >= 0 && channels.none { it.id == focusedId[0] }
            if (lost && channels.isNotEmpty()) {
                val c = channels[focusedAt[0].coerceAtMost(channels.lastIndex)]
                androidx.compose.runtime.withFrameNanos { }
                runCatching { cardFocus[c.id]?.requestFocus() }
            }
            cardFocus.keys.retainAll(channels.map { it.id }.toSet())
        }
        val rowHasFocus = remember { booleanArrayOf(false) }
        // Back to the start for a new first card — not while the cursor is in the row, which would leave it off screen.
        LaunchedEffect(channels.firstOrNull()?.id) { if (!rowHasFocus[0]) rowState.requestScrollToItem(0) }
        HomeLazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup().onFocusChanged { rowHasFocus[0] = it.hasFocus; if (!it.hasFocus) lostAtMs[0] = android.os.SystemClock.uptimeMillis() },
        ) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                ArtLiveCard(
                    channel = channel,
                    programme = programmes[channel.id].orEmpty().firstOrNull { now in it.startMs until it.stopMs },
                    now = now,
                    onClick = { onChannelClick(channel.id, channels) },
                    onLongClick = onLongPress?.let { lp -> { lp(channel) } },
                    watchedAgo = watchedAt[channel.id]?.let { watchedAgoText(now, it) },
                    pinned = pinned(channel),
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .focusRequester(cardFocus.getOrPut(channel.id) { FocusRequester() })
                        .tracked(track = track)
                        .onFocusChanged { if (it.hasFocus) { focusedAt[0] = index; focusedId[0] = channel.id; onFocus() } },
                )
            }
        }
    }
}

/** "Tonight": what the user's channels show from 21:00; OK sets or clears a reminder, long-press tunes. */
@Composable
internal fun TonightRow(
    title: String,
    items: List<TonightItem>,
    reminded: (TonightItem) -> Boolean,
    onToggleReminder: (TonightItem) -> Unit,
    onOpenChannel: (TonightItem) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    start: Dp,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    /** glcn: "Starting soon" — the start as "In 5 min", and OK opens the channel once it is about to begin. */
    soon: Boolean = false,
) {
    if (items.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    var now by remember(items) { mutableLongStateOf(System.currentTimeMillis()) }
    if (soon) LaunchedEffect(items) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    Column(modifier) {
        HomeRowHeader(title, small = stringResource(R.string.content_remind_me), start = start)
        HomeLazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(items, key = { _, t -> "${t.channel.id}:${t.programme.startMs}" }) { index, item ->
                ArtLiveCard(
                    channel = item.channel,
                    programme = item.programme,
                    now = now,
                    startLabel = if (soon) {
                        val mins = ((item.programme.startMs - now + 59_999L) / 60_000L).toInt()
                        if (mins <= 0) stringResource(R.string.glcn_starting_now) else stringResource(R.string.glcn_starts_in, mins)
                    } else fmt.format(java.util.Date(item.programme.startMs)),
                    bell = reminded(item),
                    onClick = { if (soon && item.programme.startMs - now <= SOON_OPEN_MS) onOpenChannel(item) else onToggleReminder(item) },
                    onLongClick = { onOpenChannel(item) },
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                )
            }
        }
    }
}

/**
 * The featured billboard: one big picture at a time — a match on now, a favourite on air, the newest
 * film or show — with its title, a line about it and "Watch now". ◀ ▶ step through; it turns on its own
 * every few seconds while nobody is on it.
 */
@Composable
internal fun FeaturedBillboard(
    allItems: List<FeaturedItem>,
    onPlayChannel: (ChannelEntity) -> Unit,
    onOpenMovie: (tv.own.owntv.core.database.entity.MovieEntity) -> Unit,
    onOpenSeries: (tv.own.owntv.core.database.entity.SeriesEntity) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    start: Dp,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    /** glcn: first on Home — edge to edge, taller, its foot melting into the page instead of a framed card. */
    fullWidth: Boolean = false,
) {
    // glcn: only slides whose picture can fill the screen (see rememberBillboardSlides).
    val slides = tv.own.owntv.ui.components.rememberBillboardSlides(allItems)
    if (slides.isEmpty()) return
    val items = slides.map { it.first }
    var index by remember(items) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val item = items[index.coerceIn(0, items.lastIndex)]
    val artUrl = slides[index.coerceIn(0, slides.lastIndex)].second
    // glcn: live slides never play by themselves — the billboard only shows pictures.
    LaunchedEffect(items, focused, index) {
        if (items.size < 2) return@LaunchedEffect
        delay(if (focused) 12_000 else 8_000)
        index = (index + 1) % items.size
    }
    val setInfo = LocalHomeFocusInfo.current
    val setArt = LocalHomeFocusArt.current
    val a = stageAccent
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val page = tv.own.owntv.ui.theme.stageTone(Color(0xFF070B0E), tv.own.owntv.ui.theme.LocalStagePalette.current.page)
    val animate = tv.own.owntv.ui.theme.animationsOn && !tv.own.owntv.glcn.LowRam.on
    // glcn (full width): as the page scrolls down past it the billboard fades away whole, instead of leaving its foot
    // cut off at the top of the screen over the backdrop behind the rows. Read in the draw phase: no recomposition.
    val topPx = remember { floatArrayOf(0f, Float.NEGATIVE_INFINITY) }
    val fadeTick = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    Box(
        modifier
            .fillMaxWidth()
            .height(if (fullWidth) BILLBOARD_FULL_HEIGHT else 560.mpx)
            .then(
                if (fullWidth) Modifier
                    // Measured from where it rests (the top of the page), so its resting place reads as fully shown.
                    .onGloballyPositioned { c ->
                        val y = c.positionInRoot().y
                        if (y > topPx[1]) topPx[1] = y
                        if (y != topPx[0]) { topPx[0] = y; fadeTick.floatValue = y - topPx[1] }
                    }
                    .graphicsLayer {
                        val h = size.height.coerceAtLeast(1f)
                        // Fully gone by the time half of it has left the screen.
                        alpha = (1f + fadeTick.floatValue / (h * 0.5f)).coerceIn(0f, 1f)
                    }
                else Modifier.padding(start = start, end = 64.mpx),
            ),
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(if (fullWidth) 0.mpx else 28.mpx))) {
            Crossfade(targetState = artUrl, label = "billboard") { url ->
                // glcn (full width): a slow push into each slide's picture.
                val zoom = remember(url) { androidx.compose.animation.core.Animatable(1f) }
                LaunchedEffect(url, fullWidth, animate) {
                    if (fullWidth && animate) zoom.animateTo(1.07f, androidx.compose.animation.core.tween(10_000, easing = androidx.compose.animation.core.LinearEasing))
                }
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    alignment = androidx.compose.ui.BiasAlignment(0f, -0.3f),
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value },
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.92f), 0.5f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.1f))))
            if (fullWidth) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.2f to Color.Transparent, 0.62f to Color.Transparent, 0.86f to page.copy(alpha = 0.85f), 1f to page)))
            else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = if (fullWidth) start + 8.mpx else 48.mpx, bottom = if (fullWidth) 64.mpx else 40.mpx).widthIn(max = 900.mpx),
            verticalArrangement = Arrangement.spacedBy(10.mpx),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                when (item) {
                    is FeaturedItem.Live -> {
                        if (item.isLiveEvent) LiveBadge()
                        ChannelPlate(item.channel, Modifier.size(60.mpx, 40.mpx))
                        Text(item.channel.name, style = stageText(18, 700), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                    }
                    is FeaturedItem.Movie -> Text(stringResource(R.string.glcn_recent_movies).uppercase(), style = stageText(15, 800), color = a.accent)
                    is FeaturedItem.Series -> Text(stringResource(R.string.glcn_recent_series).uppercase(), style = stageText(15, 800), color = a.accent)
                }
            }
            Text(
                when (item) {
                    is FeaturedItem.Live -> SportsNow.displayTitle(item.programme.title)
                    is FeaturedItem.Movie -> item.movie.name
                    is FeaturedItem.Series -> item.series.name
                },
                style = stageText(52, 800), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val line = when (item) {
                is FeaturedItem.Live -> listOfNotNull(
                    fmt.format(java.util.Date(item.programme.startMs)) + " – " + fmt.format(java.util.Date(item.programme.stopMs)),
                    tv.own.owntv.ui.components.rememberTmdbRating(item.programme.title, item.programme.categories),
                ).joinToString("  ·  ")
                is FeaturedItem.Movie -> listOfNotNull(item.movie.year?.toString(), item.movie.rating?.let { "★ %.1f".format(it) }).joinToString("  ·  ")
                is FeaturedItem.Series -> listOfNotNull(item.series.year?.toString(), item.series.rating?.let { "★ %.1f".format(it) }).joinToString("  ·  ")
            }
            if (line.isNotBlank()) Text(line, style = stageText(19, 600), color = Color.White.copy(alpha = 0.8f))
            val plot = when (item) {
                is FeaturedItem.Live -> item.programme.description
                is FeaturedItem.Movie -> item.movie.plot
                is FeaturedItem.Series -> item.series.plot
            }
            plot?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = stageText(17, 400), color = Color.White.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.mpx), verticalAlignment = Alignment.CenterVertically) {
                tv.own.owntv.ui.stage.StageButton(
                    text = stringResource(R.string.glcn_watch_now),
                    icon = OwnTVIcon.PLAY,
                    iconFilled = true,
                    onClick = {
                        when (item) {
                            is FeaturedItem.Live -> onPlayChannel(item.channel)
                            is FeaturedItem.Movie -> onOpenMovie(item.movie)
                            is FeaturedItem.Series -> onOpenSeries(item.series)
                        }
                    },
                    modifier = Modifier
                        .then(if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged {
                            focused = it.hasFocus
                            if (it.hasFocus) { onFocus(); setArt(null); setInfo(null) }
                        }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown || items.size < 2) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionRight -> { index = (index + 1) % items.size; true }
                                Key.DirectionLeft -> if (index > 0) { index -= 1; true } else false
                                else -> false
                            }
                        },
                )
                // Where in the run of slides this one is.
                Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
                    items.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == index) 28.mpx else 10.mpx, 10.mpx)
                                .clip(RoundedCornerShape(5.mpx))
                                .background(if (i == index) a.accent else Color.White.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
    }
}

/** glcn: what the focused card is, for the backdrop styles that say so beside the picture. */
@androidx.compose.runtime.Immutable
internal data class HomeFocusInfo(
    val title: String,
    val line: String? = null,
    val plot: String? = null,
    /** A guide programme's own title and categories, for its TMDB rating; null for films and series. */
    val rateTitle: String? = null,
    val rateCategories: String? = null,
    /** A channel on air now: the panels then read its programme in full (synopsis, progress, what's next). */
    val channel: ChannelEntity? = null,
    /** glcn: the card's own picture, so the side panel always shows what has focus (never the last backdrop). */
    val art: String? = null,
    /** glcn: a guide programme whose synopsis was left out of the light rail query; the panel reads it. */
    val programmeId: Long? = null,
    /** glcn (Cinema): the film or series behind the card — its TMDB logo, genres, cast and trailer. */
    val movie: tv.own.owntv.core.database.entity.MovieEntity? = null,
    val series: tv.own.owntv.core.database.entity.SeriesEntity? = null,
)

/** A card tells Home what it is when it takes focus (alongside [LocalHomeFocusArt]). */
internal val LocalHomeFocusInfo = staticCompositionLocalOf<(HomeFocusInfo?) -> Unit> { {} }

/** The settled focus — picture and words change together, once the D-pad rests. */
@Composable
private fun rememberSettled(url: String?, info: HomeFocusInfo?): Pair<String?, HomeFocusInfo?> {
    var settled by remember { mutableStateOf(url to info) }
    LaunchedEffect(url, info) {
        if ((url to info) != settled) { delay(350); settled = url to info }
    }
    return settled
}

/** The focused card's picture over the whole screen, drifting slowly when animations allow. */
@Composable
private fun FullBackdrop(url: String?, modifier: Modifier = Modifier, kenBurns: Boolean = false) {
    val animate = tv.own.owntv.ui.theme.animationsOn
    // glcn: clipped — the slow push-in drew the picture past its own edges, out beside the menu rail.
    Crossfade(targetState = url, label = "homeFullArt", modifier = modifier.clipToBounds()) { u ->
        if (!u.isNullOrBlank()) {
            val drift = remember(u) { androidx.compose.animation.core.Animatable(1f) }
            val driftOn = animate && !tv.own.owntv.ui.stage.GlcnMotion.subtle
            // glcn (Cinema): Ken Burns — a slower, deeper push with a drift to one side, the side chosen by the picture.
            val burns = kenBurns && driftOn && !tv.own.owntv.glcn.LowRam.on
            val side = remember(u) { if ((u.hashCode() and 1) == 0) 1f else -1f }
            LaunchedEffect(u, driftOn, burns) {
                if (burns) drift.animateTo(1f + KEN_BURNS_ZOOM, androidx.compose.animation.core.tween(KEN_BURNS_MS, easing = androidx.compose.animation.core.LinearEasing))
                else if (driftOn) drift.animateTo(1.05f, androidx.compose.animation.core.tween(12_000, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
            }
            AsyncImage(
                model = u, contentDescription = null, contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(0f, -0.3f),
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = drift.value; scaleY = drift.value
                    // The drift stays inside the zoom's margin, so no edge ever shows.
                    if (burns) translationX = side * (drift.value - 1f) * size.width * 0.42f
                },
            )
            val glow = tv.own.owntv.ui.components.rememberArtColor(u)?.luminance() ?: 0.3f
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (0.05f + glow * 0.45f).coerceAtMost(0.45f))))
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}

/**
 * Title, a line (with TMDB's rating for a film or series on air) and the synopsis. For a channel on air
 * the programme is read in full: its synopsis, how far through it is, and what comes next.
 */
@Composable
private fun FocusInfoBlock(
    info: HomeFocusInfo, titleSize: Int, plotLines: Int, modifier: Modifier = Modifier, chips: Boolean = false,
    /** glcn (Cinema): the title's TMDB lettering in place of its name, more tags (genres), the leading cast, and words that arrive one after another. */
    logo: String? = null, onLogoFailed: () -> Unit = {}, moreChips: List<String> = emptyList(), cast: String? = null, stagger: Boolean = false,
) {
    val nowArt: tv.own.owntv.core.live.NowArt = org.koin.compose.koinInject()
    val live by androidx.compose.runtime.produceState<Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>?>(null, info.channel?.id) {
        value = info.channel?.let { ch -> runCatching { nowArt.nowAndNext(ch) }.getOrNull() }
    }
    // glcn: Home's rails read the guide without synopses (a light query); the one in focus reads its own.
    val epgDao: tv.own.owntv.core.database.dao.EpgDao = org.koin.compose.koinInject()
    val loadedPlot by androidx.compose.runtime.produceState<String?>(null, info.programmeId, info.plot) {
        value = if (info.plot.isNullOrBlank() && info.programmeId != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { epgDao.programmeDescription(info.programmeId) }.getOrNull()
            }
        } else null
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val enter = if (stagger) rememberStagger() else null
    fun Modifier.step(i: Int): Modifier = if (enter == null) this else this.graphicsLayer {
        val t = enter.at(i)
        alpha = t; translationY = (1f - t) * STAGGER_RISE_PX
    }
    Column(modifier) {
        info.channel?.let { ch ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx), modifier = Modifier.padding(bottom = 10.mpx).step(0)) {
                ChannelPlate(ch, Modifier.size(78.mpx, 50.mpx))
                Text(ch.name, style = stageText(18, 700), color = StageColors.Text.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (logo != null) tv.own.owntv.ui.stage.StageTitleArt(
            url = logo, contentDescription = info.title, onFailed = onLogoFailed,
            modifier = Modifier.height(124.mpx).widthIn(max = 620.mpx).step(0),
        ) else Text(info.title, style = stageText(titleSize, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.step(0))
        val rating = info.rateTitle?.let { tv.own.owntv.ui.components.rememberTmdbRating(it, info.rateCategories) }
        val line = listOfNotNull(info.line?.takeIf { it.isNotBlank() }, rating).joinToString("  ·  ")
        if (chips && line.isNotBlank()) {
            // glcn (Cine): the facts as separate tags — time, channel, year, length — the rating in the accent.
            val parts = info.line.orEmpty().split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), modifier = Modifier.padding(top = 14.mpx).step(1)) {
                parts.take(4).forEach { InfoChip(it, accent = false) }
                moreChips.take(2).forEach { InfoChip(it, accent = false) }
                rating?.let { InfoChip(it, accent = true) }
            }
        } else if (line.isNotBlank()) {
            Text(line, style = stageText(19, 600), color = StageColors.Text.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx).step(1))
        } else if (chips && moreChips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), modifier = Modifier.padding(top = 14.mpx).step(1)) { moreChips.take(3).forEach { InfoChip(it, accent = false) } }
        }
        live?.first?.let { p ->
            val span = (p.stopMs - p.startMs).coerceAtLeast(1L)
            val f = ((System.currentTimeMillis() - p.startMs).toFloat() / span).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx), modifier = Modifier.padding(top = 12.mpx).step(2)) {
                Text(fmt.format(java.util.Date(p.startMs)), style = stageText(16, 600), color = StageColors.Text.copy(alpha = 0.7f))
                Box(Modifier.width(260.mpx).height(5.mpx).clip(RoundedCornerShape(3.mpx)).background(StageColors.Text.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(f).height(5.mpx).background(stageAccent.accent))
                }
                Text(fmt.format(java.util.Date(p.stopMs)), style = stageText(16, 600), color = StageColors.Text.copy(alpha = 0.7f))
            }
        }
        (info.plot?.takeIf { it.isNotBlank() } ?: loadedPlot?.takeIf { it.isNotBlank() } ?: live?.first?.description?.takeIf { it.isNotBlank() })?.let {
            Text(
                it, style = stageText(19, 400).copy(lineHeight = (19 * 1.45f).mpxSp), color = StageColors.Text.copy(alpha = 0.88f),
                maxLines = plotLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.mpx).step(2),
            )
        }
        cast?.let {
            Text(it, style = stageText(17, 600), color = StageColors.Text.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx).step(3))
        }
        live?.second?.let { n ->
            Text(
                stringResource(R.string.content_live_next) + " · " + fmt.format(java.util.Date(n.startMs)) + "  " + SportsNow.displayTitle(n.title),
                style = stageText(17, 600), color = StageColors.Text.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.mpx).step(3),
            )
        }
    }
}

@Composable
private fun InfoChip(text: String, accent: Boolean) {
    val a = stageAccent
    Text(
        text,
        style = stageText(17, 700),
        color = if (accent) a.accent else StageColors.Text.copy(alpha = 0.9f),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(8.mpx))
            .background(if (accent) a.accent.copy(alpha = 0.18f) else StageColors.Text.copy(alpha = 0.14f))
            .padding(horizontal = 12.mpx, vertical = 5.mpx),
    )
}

/**
 * Backdrop style "Cinema": the picture fills the screen, clear in the top half, with what it is written
 * over it on the left; the rows live in the lower part ([CINEMA_ROWS_TOP]). Before anything has focus
 * the greeting stands where the title will be.
 */
@Composable
internal fun HomeCinemaBackdrop(url: String?, info: HomeFocusInfo?, greeting: @Composable () -> Unit, start: Dp, modifier: Modifier = Modifier) {
    val (art, shown) = rememberSettled(url, info)
    val basePage = tv.own.owntv.ui.theme.stageTone(Color(0xFF070B0E), tv.own.owntv.ui.theme.LocalStagePalette.current.page)
    // glcn: the page takes a little of the picture's own colour, so the foot of the screen belongs to it.
    val artColor = tv.own.owntv.ui.components.rememberArtColor(art)
    val tinted = artColor?.let { androidx.compose.ui.graphics.lerp(basePage, it, CINEMA_AMBIENT) } ?: basePage
    val page by androidx.compose.animation.animateColorAsState(tinted, tv.own.owntv.ui.theme.ownTvTween(900), label = "cinemaAmbient")
    // glcn: TMDB for the film or series in focus — its lettering, genres, cast and trailer.
    val metaRepo: tv.own.owntv.core.metadata.MetadataRepository = org.koin.compose.koinInject()
    val metaKey = shown?.movie?.let { "m" + it.id } ?: shown?.series?.let { "s" + it.id }
    val metas = remember { androidx.compose.runtime.mutableStateMapOf<String, tv.own.owntv.core.database.entity.MetadataCacheEntity>() }
    val logoFailed = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    LaunchedEffect(metaKey) {
        val k = metaKey ?: return@LaunchedEffect
        if (k in metas) return@LaunchedEffect
        val meta = runCatching { shown?.movie?.let { metaRepo.resolveMovie(it) } ?: shown?.series?.let { metaRepo.resolveSeries(it) } }.getOrNull()
        if (meta != null) metas[k] = meta
    }
    val meta = metaKey?.let { metas[it] }
    val animsOn = tv.own.owntv.ui.theme.animationsOn
    CinemaTrailer.full?.let { (key, title) ->
        tv.own.owntv.ui.components.TrailerPlayerScreen(videoKey = key, title = title) { CinemaTrailer.full = null }
    }
    Box(modifier.fillMaxSize()) {
        tv.own.owntv.ui.components.ArtTint(art, Modifier.fillMaxSize(), strength = 0.75f)
        FullBackdrop(art, Modifier.fillMaxSize(), kenBurns = true)
        // Words on the left, rows at the foot: dark only where they are.
        Box(Modifier.fillMaxSize().backdropLeftWash(0f to 0.94f, 0.32f to 0.78f, 0.58f to 0.25f, 0.85f to 0f))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.32f to Color.Transparent, 0.5f to page.copy(alpha = 0.72f), 0.66f to page.copy(alpha = 0.94f), 1f to page),
            ),
        )
        // glcn (Cinema only): lights down — a moment into the trailer the room darkens round it, and the words and the
        // rows step back, so the trailer is the one thing lit, as in a cinema. Any key brings the lights back.
        val playingHere = CinemaTrailer.playing != null
        LaunchedEffect(playingHere) {
            if (!playingHere) { CinemaTrailer.lightsDown = false; return@LaunchedEffect }
            delay(LIGHTS_DOWN_MS)
            CinemaTrailer.lightsDown = true
        }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { CinemaTrailer.lightsDown = false } }
        val sala by androidx.compose.animation.core.animateFloatAsState(
            if (CinemaTrailer.lightsDown) 1f else 0f,
            androidx.compose.animation.core.tween(if (CinemaTrailer.lightsDown) 1_600 else 350), label = "cinemaLights",
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = sala }.background(Color.Black.copy(alpha = 0.62f)))
        // glcn: the trailer in its own window up on the right, clear of the words and the washes, its edges melting
        // into the page — the picture there stays sharp instead of being washed out under the page's veil.
        FocusTrailer(
            meta?.trailerKey, shown?.title, metaKey,
            Modifier.align(Alignment.TopEnd).fillMaxWidth(TRAILER_WINDOW_WIDTH).height(CINEMA_ROWS_TOP + 40.mpx),
        )
        Box(Modifier.padding(start = start, top = 112.mpx, end = 760.mpx).height(CINEMA_ROWS_TOP - 130.mpx).then(Modifier.clipToBounds()).graphicsLayer { alpha = 1f - 0.7f * sala }) {
            if (shown != null) androidx.compose.runtime.key(shown) {
                val logo = metaKey?.takeIf { it !in logoFailed }?.let { meta?.logoPath }?.let { tv.own.owntv.core.metadata.MetadataImages.logo(it) }
                val genres = remember(meta?.genresJson) { cinemaJsonList(meta?.genresJson) }
                val castNames = remember(meta?.castJson) { tv.own.owntv.core.metadata.MetadataCast.parse(meta?.castJson).take(3).map { it.name } }
                val cast = castNames.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.glcn_cinema_cast, it.joinToString(", ")) }
                FocusInfoBlock(
                    shown, titleSize = 52, plotLines = if (cast != null) 1 else 2, chips = true,
                    logo = logo, onLogoFailed = { metaKey?.let { logoFailed.add(it) } },
                    moreChips = genres, cast = cast, stagger = animsOn && !tv.own.owntv.glcn.LowRam.on,
                )
            } else greeting()
        }
    }
}

/**
 * glcn: the focused title's trailer in a window on Home (Cinema, classic): got ready unseen after a moment on the card,
 * shown once the focus has rested [CINEMA_TRAILER_MS], silent; never in light mode or with animations off. While it
 * plays, OK opens it whole with sound ([CinemaTrailer]).
 */
@Composable
internal fun FocusTrailer(trailerKey: String?, title: String?, focusKey: Any?, modifier: Modifier = Modifier, dissolve: Boolean = true) {
    val allowed = tv.own.owntv.ui.theme.animationsOn && !tv.own.owntv.glcn.LowRam.on && tv.own.owntv.glcn.DockPrefs.autoTrailers
    var ready by remember { mutableStateOf<String?>(null) }
    var start by remember { mutableStateOf(false) }
    val stop = CinemaTrailer.stopTick
    var startAt by remember { mutableStateOf(0f) }
    LaunchedEffect(focusKey, trailerKey, stop, allowed) {
        ready = null; start = false; startAt = 0f
        CinemaTrailer.playing = null
        val t = trailerKey ?: return@LaunchedEffect
        if (!allowed) return@LaunchedEffect
        // glcn: the trailer that was playing on Home when this title was opened goes on, at once, from where it was.
        CinemaTrailer.takeHandoff(t)?.let { at -> startAt = at; ready = t; start = true; return@LaunchedEffect }
        delay(TRAILER_WARM_MS)
        ready = t
        delay(CINEMA_TRAILER_MS - TRAILER_WARM_MS)
        start = true
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { CinemaTrailer.playing = null } }
    val key = ready ?: return
    Box(if (dissolve) modifier.dissolveEdges(start = 0.35f, bottom = 0.35f) else modifier) {
        androidx.compose.runtime.key(key) {
            tv.own.owntv.ui.components.AmbientTrailer(
                key, Modifier.fillMaxSize(), start = start, muted = !CinemaTrailer.sound, startAt = startAt,
                onSecond = { CinemaTrailer.second = it },
                onPlaying = { CinemaTrailer.playing = key to title.orEmpty() },
            ) { ready = null; CinemaTrailer.playing = null }
        }
        // The one hint while it plays: Play/Pause for its sound.
        if (CinemaTrailer.playing?.first == key) TrailerSoundHint(Modifier.align(Alignment.TopEnd).padding(end = 56.mpx, top = TRAILER_HINT_TOP))
    }
}

/** glcn: "⏯ Sound" / "⏯ Mute" — what Play/Pause does to the trailer behind the page. */
@Composable
internal fun TrailerSoundHint(modifier: Modifier = Modifier) {
    val on = CinemaTrailer.sound
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 16.mpx, vertical = 8.mpx),
        horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically,
    ) {
        tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.PLAY, Color.White, Modifier.size(16.mpx))
        tv.own.owntv.ui.components.OwnTVIcon(if (on) OwnTVIcon.VOLUME_HIGH else OwnTVIcon.VOLUME_MUTE, Color.White, Modifier.size(18.mpx))
        Text(stringResource(if (on) R.string.glcn_trailer_mute else R.string.glcn_trailer_sound), style = stageText(15, 700), color = Color.White, maxLines = 1)
    }
}

/** glcn: TMDB for the film or series behind a Home card — its trailer — read once the focus rests on it. */
@Composable
internal fun rememberFocusMeta(info: HomeFocusInfo?): tv.own.owntv.core.database.entity.MetadataCacheEntity? {
    val repo: tv.own.owntv.core.metadata.MetadataRepository = org.koin.compose.koinInject()
    val key = info?.movie?.let { "m" + it.id } ?: info?.series?.let { "s" + it.id }
    val cache = remember { androidx.compose.runtime.mutableStateMapOf<String, tv.own.owntv.core.database.entity.MetadataCacheEntity>() }
    LaunchedEffect(key) {
        val k = key ?: return@LaunchedEffect
        if (k in cache) return@LaunchedEffect
        delay(250)
        runCatching { info?.movie?.let { repo.resolveMovie(it) } ?: info?.series?.let { repo.resolveSeries(it) } }.getOrNull()?.let { cache[k] = it }
    }
    return key?.let { cache[it] }
}

/**
 * glcn (Cinema): the trailer playing silently behind Home. While it plays, OK opens it whole with sound instead of
 * pressing the card; any other key quietens it. Home's root passes its keys through [onKey].
 */
internal object CinemaTrailer {
    /** The trailer playing silently behind the page (key, title), or null. */
    var playing by mutableStateOf<Pair<String, String>?>(null)
    var full by mutableStateOf<Pair<String, String>?>(null)
    var stopTick by mutableStateOf(0)
    /** Sound on — asked for with Play/Pause; kept for the next trailers until the app closes, as Netflix keeps it. */
    var sound by mutableStateOf(false)
    /** How far the playing trailer has got, for the carry-over to the title's page. */
    var second = 0f
    /** A trailer that was playing when OK opened its title: the page it lands on goes on with it (key, second, when). */
    var handoff: Triple<String, Float, Long>? = null
    /** Cinema's lights down: the trailer has played a moment, everything round it steps back. */
    var lightsDown by mutableStateOf(false)
    private var playTaken = false

    /** A carried-over trailer for [key], if it is fresh; taking it clears it. */
    fun takeHandoff(key: String): Float? {
        val h = handoff ?: return null
        if (h.first != key || android.os.SystemClock.uptimeMillis() - h.third > HANDOFF_MS) return null
        handoff = null
        return h.second
    }

    fun onKey(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val play = e.key == Key.MediaPlayPause || e.key == Key.MediaPlay || e.key == Key.MediaPause
        if (play) {
            // Play/Pause while a trailer plays: its sound on or off.
            if (e.type == KeyEventType.KeyDown && playing != null) { playTaken = true; sound = !sound; return true }
            if (playTaken) { if (e.type == KeyEventType.KeyUp) playTaken = false; return true }
            return false
        }
        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        if (e.type == KeyEventType.KeyDown && playing != null) {
            // OK does what it always does; the trailer is handed to the page it opens.
            if (ok) playing?.let { handoff = Triple(it.first, second, android.os.SystemClock.uptimeMillis()) }
            playing = null; stopTick++
        }
        return false
    }

    private const val HANDOFF_MS = 6_000L
}

/** glcn (Cinema): one word after another — each step of the words fades and rises in, a beat after the one before. */
private class Stagger(val anim: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>) {
    fun at(i: Int): Float = ((anim.value - i * STAGGER_STEP) / STAGGER_SPAN).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) * (1f - it) }
}

@Composable
private fun rememberStagger(): Stagger {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(STAGGER_STEP * 3 + STAGGER_SPAN, androidx.compose.animation.core.tween(STAGGER_TOTAL_MS, easing = androidx.compose.animation.core.LinearEasing)) }
    return remember(anim) { Stagger(anim) }
}

private fun cinemaJsonList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())
}

private const val KEN_BURNS_ZOOM = 0.12f
private const val KEN_BURNS_MS = 24_000
private const val CINEMA_AMBIENT = 0.16f
private const val CINEMA_TRAILER_MS = 3_000L
private const val TRAILER_WARM_MS = 1_200L
private const val LIGHTS_DOWN_MS = 2_500L
/** glcn: the trailer window's share of the screen's width, up on the right. */
internal const val TRAILER_WINDOW_WIDTH = 0.62f
private const val STAGGER_STEP = 0.18f
private const val STAGGER_SPAN = 0.46f
private const val STAGGER_TOTAL_MS = 620
private const val STAGGER_RISE_PX = 22f

/** Where the rows start under the "Cinema" backdrop. */
internal val CINEMA_ROWS_TOP = 470.mpx

/**
 * Backdrop style "Panel": the picture fills the screen behind everything, darkened so the rows read on
 * top, and a fixed panel at the side says what has focus.
 */
@Composable
internal fun HomePanelBackdrop(url: String?, modifier: Modifier = Modifier) {
    val (art, _) = rememberSettled(url, null)
    val page = tv.own.owntv.ui.theme.stageTone(Color(0xFF070B0E), tv.own.owntv.ui.theme.LocalStagePalette.current.page)
    Box(modifier.fillMaxSize()) {
        tv.own.owntv.ui.components.ArtTint(art, Modifier.fillMaxSize(), strength = 0.6f)
        FullBackdrop(art, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(page.copy(alpha = 0.58f)))
        Box(Modifier.fillMaxSize().backdropLeftWash(0f to 0.5f, 0.6f to 0.2f, 1f to 0f))
    }
}

/** The side panel of the "Panel" style: the picture small and sharp, then what it is. */
@Composable
internal fun HomeInfoPanel(url: String?, info: HomeFocusInfo?, modifier: Modifier = Modifier) {
    val (backdrop, shown) = rememberSettled(url, info)
    // glcn: the card's own picture when it says what it is — a card without one shows no picture rather
    // than the backdrop left over from the card before (which then belonged to something else).
    val art = if (shown != null) shown.art?.takeIf { it.isNotBlank() } else backdrop
    // glcn: nothing focused that it can describe (the billboard says it all itself): no empty box.
    if (art.isNullOrBlank() && shown == null) return
    Column(
        modifier
            .width(PANEL_WIDTH)
            .fillMaxHeight()
            // glcn: level with the first row, and the same 48 from the edge as everything else.
            .padding(top = 110.mpx, bottom = 48.mpx, end = 48.mpx)
            .clip(RoundedCornerShape(28.mpx))
            .background(if (StageColors.Text.luminance() < 0.5f) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.38f))
            .padding(24.mpx),
    ) {
        if (!art.isNullOrBlank()) Box(Modifier.fillMaxWidth().height(PANEL_WIDTH * 9f / 16f - 30.mpx).clip(RoundedCornerShape(18.mpx)).background(Color.White.copy(alpha = 0.06f))) {
            Crossfade(targetState = art, label = "panelArt") { u ->
                AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(0f, -0.4f), modifier = Modifier.fillMaxSize())
            }
        }
        if (shown != null) FocusInfoBlock(shown, titleSize = 34, plotLines = 7, modifier = Modifier.padding(top = if (art.isNullOrBlank()) 0.mpx else 22.mpx))
    }
}

internal val PANEL_WIDTH = 560.mpx

/**
 * glcn: a Home row. Home's own scroll rule (it keeps room under the last vertical row) must not reach the
 * horizontal rows: it put a card flush to the screen edge when focus came back to the start, past the
 * row's left padding. Rows scroll with the edge rule, which leaves a card of room and so rests at the start.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
internal fun HomeLazyRow(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(androidx.compose.ui.unit.Dp(0f)),
    horizontalArrangement: androidx.compose.foundation.layout.Arrangement.Horizontal = androidx.compose.foundation.layout.Arrangement.Start,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val layoutDir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val startPx = with(density) { contentPadding.calculateStartPadding(layoutDir).toPx() }
    val endPx = with(density) { contentPadding.calculateEndPadding(layoutDir).toPx() }
    val gapPx = with(density) { horizontalArrangement.spacing.toPx() }
    val spec = androidx.compose.runtime.remember(startPx, endPx, gapPx) { tv.own.owntv.glcn.WholeCardScroll(startPx, endPx, gapPx) }
    // glcn: nothing is drawn left of the first card's place (bar its focus ring), so no sliver of the card before it.
    // Up to just short of the gap before the first card: room for its ring, none for the card before it.
    val clipLeft = startPx - (gapPx - with(density) { 2.mpx.toPx() }).coerceIn(0f, with(density) { 14.mpx.toPx() })
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec,
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = modifier.then(
                if (clipLeft > 0f) androidx.compose.ui.Modifier.drawWithContent {
                    clipRect(left = clipLeft, top = -size.height, right = size.width * 2, bottom = size.height * 2) { this@drawWithContent.drawContent() }
                } else androidx.compose.ui.Modifier,
            ),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}

/** glcn: a row that lost focus this recently, as its cards changed, lost it because the focused card went. */
private const val FOCUS_LOSS_WINDOW_MS = 400L

/** glcn: classic Home — how long the focus rests on a card before its picture fills the backdrop. */
private const val CLASSIC_BACKDROP_SETTLE_MS = 1_800L

/** glcn: the billboard's height when it runs edge to edge as Home's first row. */
private val BILLBOARD_FULL_HEIGHT = 700.mpx

/** glcn: "Starting soon" — this close to its start, OK goes to the channel instead of setting a reminder. */
private const val SOON_OPEN_MS = 3 * 60_000L

/** glcn: "watched 10 min ago" / "watched 3 h ago" / "watched yesterday" for a recent channel. */
@Composable
internal fun watchedAgoText(now: Long, at: Long): String {
    val mins = ((now - at).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        mins < 1 -> stringResource(R.string.glcn_watched_just_now)
        mins < 60 -> stringResource(R.string.glcn_watched_minutes_ago, mins)
        mins < 24 * 60 -> stringResource(R.string.glcn_watched_hours_ago, mins / 60)
        else -> stringResource(R.string.glcn_watched_days_ago, mins / (24 * 60))
    }
}

/** glcn: the trailer's sound hint sits up on the right, under the clock — never behind the cards at the foot. */
internal val TRAILER_HINT_TOP = 150.mpx
