package tv.own.owntv.features.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
internal fun HomeFocusBackdrop(url: String?, modifier: Modifier = Modifier) {
    var settled by remember { mutableStateOf(url) }
    // glcn: long enough that holding ◀ ▶ across a row does not load a picture per card.
    LaunchedEffect(url) { if (url != settled) { delay(350); settled = url } }
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
) {
    val a = stageAccent
    val setArt = LocalHomeFocusArt.current
    val setInfo = LocalHomeFocusInfo.current
    val rawArt = programme?.imageUrl?.takeIf { it.isNotBlank() }
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
                Text(
                    programme?.title?.let(SportsNow::displayTitle) ?: channel.name,
                    style = stageText(20, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val line = when {
                    startLabel != null -> "$startLabel · ${channel.name}"
                    programme != null -> channel.name + " · " + timeLeftText(((programme.stopMs - now).coerceAtLeast(0L) + 59_999L) / 60_000L)
                    else -> null
                }
                line?.let {
                    Text(it, style = stageText(15, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
) {
    if (channels.isEmpty()) return
    Column(modifier) {
        HomeRowHeader(title, small = stringResource(R.string.home_row_on_now), start = start)
        // glcn: a channel that moves to the front (the one just watched) shows as the first card, whole,
        // instead of the row holding its old place and leaving it half off the left edge.
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(channels.firstOrNull()?.id) { runCatching { rowState.scrollToItem(0) } }
        HomeLazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                ArtLiveCard(
                    channel = channel,
                    programme = programmes[channel.id].orEmpty().firstOrNull { now in it.startMs until it.stopMs },
                    now = now,
                    onClick = { onChannelClick(channel.id, channels) },
                    onLongClick = onLongPress?.let { lp -> { lp(channel) } },
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
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
) {
    if (items.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val now = remember(items) { System.currentTimeMillis() }
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
                    startLabel = fmt.format(java.util.Date(item.programme.startMs)),
                    bell = reminded(item),
                    onClick = { onToggleReminder(item) },
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
    Box(
        modifier
            .fillMaxWidth()
            .height(560.mpx)
            .padding(start = start, end = 64.mpx),
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.mpx))) {
            Crossfade(targetState = artUrl, label = "billboard") { url ->
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    alignment = androidx.compose.ui.BiasAlignment(0f, -0.3f),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.92f), 0.5f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.1f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 48.mpx, bottom = 40.mpx).widthIn(max = 900.mpx),
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
private fun FullBackdrop(url: String?, modifier: Modifier = Modifier) {
    val animate = tv.own.owntv.ui.theme.animationsOn
    Crossfade(targetState = url, label = "homeFullArt", modifier = modifier) { u ->
        if (!u.isNullOrBlank()) {
            val drift = remember(u) { androidx.compose.animation.core.Animatable(1f) }
            val driftOn = animate && !tv.own.owntv.ui.stage.GlcnMotion.subtle
            LaunchedEffect(u, driftOn) {
                if (driftOn) drift.animateTo(1.05f, androidx.compose.animation.core.tween(12_000, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
            }
            AsyncImage(
                model = u, contentDescription = null, contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(0f, -0.3f),
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = drift.value; scaleY = drift.value },
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
private fun FocusInfoBlock(info: HomeFocusInfo, titleSize: Int, plotLines: Int, modifier: Modifier = Modifier, chips: Boolean = false) {
    val nowArt: tv.own.owntv.core.live.NowArt = org.koin.compose.koinInject()
    val live by androidx.compose.runtime.produceState<Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>?>(null, info.channel?.id) {
        value = info.channel?.let { ch -> runCatching { nowArt.nowAndNext(ch) }.getOrNull() }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    Column(modifier) {
        info.channel?.let { ch ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx), modifier = Modifier.padding(bottom = 10.mpx)) {
                ChannelPlate(ch, Modifier.size(78.mpx, 50.mpx))
                Text(ch.name, style = stageText(18, 700), color = StageColors.Text.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(info.title, style = stageText(titleSize, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val rating = info.rateTitle?.let { tv.own.owntv.ui.components.rememberTmdbRating(it, info.rateCategories) }
        val line = listOfNotNull(info.line?.takeIf { it.isNotBlank() }, rating).joinToString("  ·  ")
        if (chips && line.isNotBlank()) {
            // glcn (Cine): the facts as separate tags — time, channel, year, length — the rating in the accent.
            val parts = info.line.orEmpty().split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), modifier = Modifier.padding(top = 14.mpx)) {
                parts.take(4).forEach { InfoChip(it, accent = false) }
                rating?.let { InfoChip(it, accent = true) }
            }
        } else if (line.isNotBlank()) {
            Text(line, style = stageText(19, 600), color = StageColors.Text.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx))
        }
        live?.first?.let { p ->
            val span = (p.stopMs - p.startMs).coerceAtLeast(1L)
            val f = ((System.currentTimeMillis() - p.startMs).toFloat() / span).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx), modifier = Modifier.padding(top = 12.mpx)) {
                Text(fmt.format(java.util.Date(p.startMs)), style = stageText(16, 600), color = StageColors.Text.copy(alpha = 0.7f))
                Box(Modifier.width(260.mpx).height(5.mpx).clip(RoundedCornerShape(3.mpx)).background(StageColors.Text.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(f).height(5.mpx).background(stageAccent.accent))
                }
                Text(fmt.format(java.util.Date(p.stopMs)), style = stageText(16, 600), color = StageColors.Text.copy(alpha = 0.7f))
            }
        }
        (info.plot?.takeIf { it.isNotBlank() } ?: live?.first?.description?.takeIf { it.isNotBlank() })?.let {
            Text(
                it, style = stageText(19, 400).copy(lineHeight = (19 * 1.45f).mpxSp), color = StageColors.Text.copy(alpha = 0.88f),
                maxLines = plotLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.mpx),
            )
        }
        live?.second?.let { n ->
            Text(
                stringResource(R.string.content_live_next) + " · " + fmt.format(java.util.Date(n.startMs)) + "  " + SportsNow.displayTitle(n.title),
                style = stageText(17, 600), color = StageColors.Text.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.mpx),
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
    val page = tv.own.owntv.ui.theme.stageTone(Color(0xFF070B0E), tv.own.owntv.ui.theme.LocalStagePalette.current.page)
    Box(modifier.fillMaxSize()) {
        tv.own.owntv.ui.components.ArtTint(art, Modifier.fillMaxSize(), strength = 0.6f)
        FullBackdrop(art, Modifier.fillMaxSize())
        // Words on the left, rows at the foot: dark only where they are.
        Box(Modifier.fillMaxSize().backdropLeftWash(0f to 0.94f, 0.32f to 0.78f, 0.58f to 0.25f, 0.85f to 0f))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.32f to Color.Transparent, 0.5f to page.copy(alpha = 0.72f), 0.66f to page.copy(alpha = 0.94f), 1f to page),
            ),
        )
        Box(Modifier.padding(start = start, top = 112.mpx, end = 760.mpx).height(CINEMA_ROWS_TOP - 130.mpx).then(Modifier.clipToBounds())) {
            if (shown != null) FocusInfoBlock(shown, titleSize = 52, plotLines = 2, chips = true) else greeting()
        }
    }
}

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
    val (art, shown) = rememberSettled(url, info)
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
        Box(Modifier.fillMaxWidth().height(PANEL_WIDTH * 9f / 16f - 30.mpx).clip(RoundedCornerShape(18.mpx)).background(Color.White.copy(alpha = 0.06f))) {
            Crossfade(targetState = art, label = "panelArt") { u ->
                if (!u.isNullOrBlank()) AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (shown != null) FocusInfoBlock(shown, titleSize = 34, plotLines = 7, modifier = Modifier.padding(top = 22.mpx))
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
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides tv.own.owntv.features.live.edgeScrollSpec,
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}
