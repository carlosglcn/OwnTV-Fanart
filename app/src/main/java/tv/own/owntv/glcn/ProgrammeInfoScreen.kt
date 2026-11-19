package tv.own.owntv.glcn

import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.focusable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.metadata.ProgrammeArtResolver
import tv.own.owntv.features.shell.components.DriftingSynopsis
import tv.own.owntv.player.StreamInfoOverlay
import tv.own.owntv.player.PlaybackEngine
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.ProgrammeArt
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

// glcn: the screen follows the theme — dark ink on the light page, light ink on the dark one.
private val light: Boolean @androidx.compose.runtime.Composable get() = tv.own.owntv.ui.theme.LocalStagePalette.current.isLight
private val Ink: Color @androidx.compose.runtime.Composable get() = if (light) Color(0xFF111A1C) else Color(0xFFF2F6F5)
// glcn: near the title's own black in the light theme — the synopsis read grey over a busy picture.
private val Soft: Color @androidx.compose.runtime.Composable get() = if (light) Color(0xFF1C2729) else Color(0xFFC9D4D2)
private val Dim: Color @androidx.compose.runtime.Composable get() = if (light) Color(0xFF5E6B6E) else Color(0xFF93A09E)
/** The page behind the words, and the panels on it. */
private val Page: Color @androidx.compose.runtime.Composable get() = tv.own.owntv.ui.theme.LocalStagePalette.current.page
private val PanelFill: Color @androidx.compose.runtime.Composable get() = if (light) Color.White.copy(alpha = 0.72f) else Color(0x990A1012)

/**
 * glcn: the programme on air, on a screen of its own over the picture — its fanart behind, the channel's
 * logo, what it is (genres, year, age, rating), its synopsis, what follows, and when it airs again this
 * week. Back closes it. The stream's technical data hide behind a button here, off the player bar.
 */
@Composable
fun ProgrammeInfoScreen(
    startChannel: ChannelEntity,
    player: PlaybackEngine,
    loadSchedule: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    loadWeek: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** glcn: watch the programme on air from its start (catch-up channels only). */
    onStartOver: ((ChannelEntity, EpgProgrammeEntity) -> Unit)? = null,
    /** glcn: record the channel from now. */
    onRecord: ((ChannelEntity) -> Unit)? = null,
    /** glcn: the list being watched, in order — ◀ ▶ (and CH±) look at what is on along it without changing channel. */
    channels: List<ChannelEntity> = emptyList(),
    /** glcn: OK on another channel's page tunes it. */
    onTune: ((ChannelEntity) -> Unit)? = null,
    /** glcn: a film picked on the cast page — its page, out of the player. */
    onOpenFilm: ((tv.own.owntv.core.database.entity.MovieEntity) -> Unit)? = null,
) {
    // glcn: the channel whose page is shown — the one playing at first; the window keeps playing that one throughout.
    var channel by remember { mutableStateOf(startChannel) }
    val swap = remember { androidx.compose.animation.core.Animatable(0f) }
    val swapScope = androidx.compose.runtime.rememberCoroutineScope()
    var lastSwapAt by remember { mutableStateOf(0L) }
    val swapAnimated = DeviceClass.richMotion && tv.own.owntv.ui.theme.animationsOn
    // glcn: where ◀ ▶ are heading — counted from here, not from the page on screen, so quick presses all count
    // (a press during the slide used to cancel the one before and the page stayed where it was).
    var target by remember { mutableStateOf(startChannel) }
    var swapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val swapContext = androidx.compose.ui.platform.LocalContext.current
    // glcn: the frames this page draws are told apart in the report ("info", and "info·swap" while ◀ ▶ change page).
    androidx.compose.runtime.DisposableEffect(Unit) {
        val before = FrameWatch.screen
        FrameWatch.screen = "info"
        onDispose { FrameWatch.screen = before }
    }
    // glcn: the page on screen (its channel's schedule, repeats, synopsis and TMDB details), and how to read one.
    var page by remember { mutableStateOf<InfoPage?>(null) }
    var pageLoader: (suspend (ChannelEntity) -> InfoPage)? = null
    fun go(step: Int) {
        if (channels.size < 2) return
        val at = channels.indexOfFirst { it.id == target.id }.takeIf { it >= 0 } ?: return
        val next = channels[(at + step + channels.size) % channels.size]
        target = next
        val now = android.os.SystemClock.uptimeMillis()
        // A held key gets shorter moves, so the pages keep up with it.
        val quick = now - lastSwapAt < QUICK_SWAP_MS
        lastSwapAt = now
        val calm = swapAnimated
        swapJob?.cancel()
        swapJob = swapScope.launch {
            if (!calm) { page = pageLoader?.invoke(next); channel = next; return@launch }
            FrameWatch.screen = "info·swap"
            // glcn: the next page is read while the old one leaves and comes in whole, in one change.
            val load = pageLoader
            val ready = async { load?.invoke(next) }
            swap.animateTo(-step.toFloat(), androidx.compose.animation.core.tween(if (quick) 70 else SWAP_OUT_MS, easing = androidx.compose.animation.core.FastOutLinearInEasing))
            val p = kotlinx.coroutines.withTimeoutOrNull(if (quick) QUICK_READY_MS else SWAP_READY_MS) { ready.await() }
            channel = next
            page = p ?: InfoPage(next.id, emptyList(), loading = true)
            swap.snapTo(step.toFloat())
            swap.animateTo(0f, androidx.compose.animation.core.tween(if (quick) 140 else SWAP_IN_MS, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
            // Not ready in time (a slow server): it lands when it comes.
            if (p == null) ready.await()?.let { if (channel.id == it.channelId) page = it }
            kotlinx.coroutines.delay(SWAP_SETTLE_MS)
            if (FrameWatch.screen == "info·swap") FrameWatch.screen = "info"
        }
    }
    val slide = Modifier.graphicsLayer {
        translationX = swap.value * SWAP_SHIFT.toPx()
        alpha = 1f - kotlin.math.abs(swap.value) * 0.9f
    }
    // glcn: the film player's side-panel motion — the words glide in as one piece from their side (the left),
    // the window's things from the right, together with the picture settling into its window.
    val enterAnimated = !LowRam.on && tv.own.owntv.ui.theme.animationsOn
    val enter = remember { androidx.compose.animation.core.Animatable(if (enterAnimated) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        enter.animateTo(1f, androidx.compose.animation.core.tween(PANEL_IN_MS, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
    }
    val slideInLeft = Modifier.graphicsLayer { translationX = (1f - enter.value) * -PANEL_SHIFT.toPx(); alpha = enter.value }
    val slideInRight = Modifier.graphicsLayer { translationX = (1f - enter.value) * PANEL_SHIFT.toPx(); alpha = enter.value }
    BackHandler { onDismiss() }
    val accent = stageAccent.accent
    val accentInk = stageAccent.ink
    val resolver: ProgrammeArtResolver = org.koin.compose.koinInject()
    val timeFmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val dayFmt = remember { java.text.SimpleDateFormat("EEE d", java.util.Locale.getDefault()) }
    val nowMs = remember { System.currentTimeMillis() }

    // glcn: everything a page shows, read in one go away from the screen and put on screen at once — before, the
    // schedule, the week, the synopsis and TMDB's film each landed on its own frame while a page slid in (the old
    // film's picture behind the new title, names before their faces), and the slide stuttered under them.
    val epgDao: tv.own.owntv.core.database.dao.EpgDao = org.koin.compose.koinInject()
    suspend fun loadPage(ch: ChannelEntity): InfoPage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val rows = runCatching { loadSchedule(ch) }.getOrDefault(emptyList())
        val onAir = rows.filter { nowMs in it.startMs until it.stopMs }.maxByOrNull { it.startMs }
        if (onAir == null) return@withContext InfoPage(ch.id, rows)
        kotlinx.coroutines.coroutineScope {
            val week = async { runCatching { loadWeek(ch) }.getOrDefault(emptyList()) }
            val synopsis = async {
                onAir.description?.takeIf { it.isNotBlank() }
                    ?: runCatching { epgDao.programmeDescription(onAir.id) }.getOrNull()?.takeIf { it.isNotBlank() }
            }
            val lent = async { runCatching { resolver.detailsFor(onAir.title, onAir.categories) }.getOrNull() }
            val logo = async { runCatching { resolver.logoFor(onAir.title, onAir.categories) }.getOrNull() }
            val film = async { runCatching { resolver.filmFor(onAir.title, onAir.categories) }.getOrNull() }
            val f = film.await(); val l = lent.await()
            // The picture decoded too, so it is on screen with the words.
            pageArt(f, l, onAir)?.let { u -> runCatching { tv.own.owntv.ui.components.warmProgrammeArt(swapContext, u) } }
            // glcn: the cast's faces too (TMDB's or Wikipedia's), briefly — so the page does not open on initials.
            val people = f?.cast.orEmpty().ifEmpty { guideCast(synopsis.await()) }.take(CAST_WARM)
            kotlinx.coroutines.withTimeoutOrNull(CAST_WARM_MS) {
                people.map { m -> async { runCatching { warmCastFace(swapContext, m) } } }.forEach { it.await() }
            }
            InfoPage(ch.id, rows, airsAgain(onAir, week.await()), synopsis.await(), l, logo.await(), f, complete = true)
        }
    }
    pageLoader = { ch -> loadPage(ch) }
    LaunchedEffect(Unit) { if (page == null) page = loadPage(startChannel) }
    val shown = page?.takeIf { it.channelId == channel.id }
    val schedule = shown?.rows
    val rows = schedule.orEmpty()
    // glcn: with overlapping guides, the one that started last — as the banner, the lists and Home pick it.
    val cur = rows.filter { nowMs in it.startMs until it.stopMs }.maxByOrNull { it.startMs }
    // glcn: what follows — the rest of the evening, not two lines.
    val next = if (cur == null) emptyList() else rows.filter { it.startMs >= cur.stopMs }.take(NEXT_MAX)
    val again = shown?.again.orEmpty()
    val ownSynopsis = shown?.synopsis
    // glcn: the names the guide gives after "Reparto:", for the cast panel and the Cast button.
    val guidePeople = remember(ownSynopsis) { guideCast(ownSynopsis) }
    var castOpen by remember(cur?.id) { mutableStateOf(false) }
    // TMDB lends the wide picture, the rating and a synopsis when the guide says little.
    val lent = shown?.lent
    // A film's own lettering, when TMDB has it, stands where its name would.
    val titleLogo = shown?.titleLogo
    // glcn: a film on the channel, in full (TMDB): lettering, year, genres, rating, synopsis, cast and the wide picture.
    val film = shown?.film
    val filmLogo = film?.logoPath?.let { tv.own.owntv.core.metadata.MetadataImages.logo(it) }
    // glcn: who is in it — TMDB's cast for a film, else the guide's names; the Cast button opens the cast page on them.
    val pageCast = film?.cast.orEmpty().ifEmpty { guidePeople }
    if (castOpen && pageCast.isNotEmpty()) CastPage(
        film = cur?.title.orEmpty(),
        backdrop = film?.backdropPath?.let { tv.own.owntv.core.metadata.MetadataImages.backdrop(it, "w1280") } ?: cur?.imageUrl,
        cast = pageCast, currentId = -1L,
        onOpen = { m -> castOpen = false; onOpenFilm?.invoke(m) }, onDismiss = { castOpen = false },
    )
    var logoFailed by remember(titleLogo, filmLogo) { mutableStateOf(false) }

    // glcn: the channels either side are read ahead (schedule, then TMDB for what they show), so a page is ready before ◀ ▶.
    LaunchedEffect(channel.id, channels) {
        if (channels.size < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(PREFETCH_DELAY_MS)
        val at = channels.indexOfFirst { it.id == channel.id }.takeIf { it >= 0 } ?: return@LaunchedEffect
        for (d in listOf(1, -1, 2, -2)) {
            val n = channels[((at + d) % channels.size + channels.size) % channels.size]
            if (n.id == channel.id) continue
            val rows = runCatching { loadSchedule(n) }.getOrNull().orEmpty()
            val onAir = rows.filter { nowMs in it.startMs until it.stopMs }.maxByOrNull { it.startMs } ?: continue
            val f = runCatching { resolver.filmFor(onAir.title, onAir.categories) }.getOrNull()
            val l = runCatching { resolver.detailsFor(onAir.title, onAir.categories) }.getOrNull()
            // glcn: the neighbours' pictures too, decoded ahead — the page's backdrop is the heaviest thing it brings.
            pageArt(f, l, onAir)?.let { u -> runCatching { tv.own.owntv.ui.components.warmProgrammeArt(swapContext, u) } }
        }
    }
    var showTech by remember { mutableStateOf(false) }
    // glcn: the channel goes on in a small window up on the right (the shell moves the picture there).
    androidx.compose.runtime.DisposableEffect(Unit) {
        InfoStage.liveWindow = true
        onDispose { InfoStage.liveWindow = false }
    }
    val focus = remember { FocusRequester() }
    val pageFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { pageFocus.requestFocus() } }

    Box(
        modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                androidx.compose.ui.input.key.Key.ChannelUp -> { go(1); true }
                androidx.compose.ui.input.key.Key.ChannelDown -> { go(-1); true }
                else -> false
            }
        },
    ) {
        // glcn: the page itself takes the focus first: ◀ ▶ walk the channels, OK tunes the one shown, ▼ goes to the buttons.
        Box(
            Modifier.fillMaxSize().focusRequester(pageFocus).focusable().onKeyEvent { e ->
                if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    androidx.compose.ui.input.key.Key.DirectionLeft -> { go(-1); true }
                    androidx.compose.ui.input.key.Key.DirectionRight -> { go(1); true }
                    androidx.compose.ui.input.key.Key.DirectionDown -> { runCatching { focus.requestFocus() }; true }
                    androidx.compose.ui.input.key.Key.DirectionUp -> true
                    androidx.compose.ui.input.key.Key.DirectionCenter, androidx.compose.ui.input.key.Key.Enter, androidx.compose.ui.input.key.Key.NumPadEnter -> {
                        if (channel.id != startChannel.id && onTune != null) onTune(channel) else onDismiss()
                        true
                    }
                    else -> false
                }
            },
        )
        // glcn: the backdrop, with a window cut in it up on the right where the channel goes on playing.
        val windowRadius = 18.mpx
        var holeOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val holePath = remember { androidx.compose.ui.graphics.Path() }
        Box(
            Modifier.fillMaxSize()
                .onGloballyPositioned { holeOrigin = it.positionInWindow() }
                // glcn: the backdrop comes up as the picture shrinks, and the window follows the picture all the way —
                // the TV seems to fold itself into its place on the right instead of jumping there.
                .graphicsLayer { alpha = (InfoStage.progress * 1.4f).coerceAtMost(1f) }
                // glcn: the window is left out of the drawing (a clip) instead of cleared from an offscreen copy of the whole
                // screen — that copy was redrawn on every frame while a new page's picture, shade and tint came in.
                .drawWithContent {
                    val p = InfoStage.progress
                    val r = InfoStage.windowRect(size.width, size.height, holeOrigin)
                    val left = r.left * p
                    val top = r.top * p
                    val w = size.width + (r.width - size.width) * p
                    val h = size.height + (r.height - size.height) * p
                    holePath.reset()
                    holePath.addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(left, top, left + w, top + h, androidx.compose.ui.geometry.CornerRadius(windowRadius.toPx() * p)),
                    )
                    clipPath(holePath, androidx.compose.ui.graphics.ClipOp.Difference) { this@drawWithContent.drawContent() }
                },
        ) {
            Box(Modifier.fillMaxSize().background(Page))
            // glcn: while a page is still being read, the last picture stays rather than fading to nothing and back.
            val lastArt = remember { arrayOfNulls<String>(1) }
            val art = pageArt(film, lent, cur).also { if (it != null) lastArt[0] = it } ?: lastArt[0].takeIf { shown?.loading == true }
            androidx.compose.animation.Crossfade(targetState = art, animationSpec = androidx.compose.animation.core.tween(SWAP_IN_MS), label = "infoArt") { url ->
                if (url != null) ProgrammeArt(url = url, modifier = Modifier.fillMaxSize())
            }
            // The picture stays behind, never under the words: dark from the left and from the foot.
            // A deeper shade on the left, where the synopsis now runs long, so it reads over bright pictures too.
            val pg = Page
            // glcn: as much shade as this picture needs for the words over it to read, and no more — a pale picture
            // under the light theme (or a dark one under the dark theme) stays seen instead of washed out.
            val tone by ArtTone.of(art)
            val needState = androidx.compose.animation.core.animateFloatAsState(tone?.veil(light) ?: VEIL_UNREAD, androidx.compose.animation.core.tween(400), label = "infoVeil")
            // glcn: read while drawing, not composing — the shade's change no longer rebuilds the page on every frame.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val need = needState.value
                    drawRect(
                        Brush.horizontalGradient(
                            0f to pg.copy(alpha = (need + 0.12f).coerceAtMost(0.96f)), 0.55f to pg.copy(alpha = need),
                            0.75f to pg.copy(alpha = need * 0.55f), 1f to pg.copy(alpha = need * 0.3f),
                        ),
                    )
                },
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to pg.copy(alpha = 0.9f))))
            // glcn: the page takes a soft glow in the picture's own tone, as the film and series pages do.
            tv.own.owntv.ui.components.ArtTint(art, Modifier.fillMaxSize(), strength = 0.5f, focusX = 0.15f, focusY = 0.85f)
        }
        // A fine frame round the window, and the word "Live" on it.
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 96.mpx, top = WINDOW_TOP).width(WINDOW_W).height(WINDOW_H)
                // glcn: where the frame really is on screen (before its slide), so the picture and the hole follow it
                // whatever the UI zoom or the safe area — they were worked out for 1920 × 1080 and drifted otherwise.
                .onGloballyPositioned { InfoStage.frame = it.boundsInWindow() }
                .then(slideInRight)
                .border(1.5.mpx, Ink.copy(alpha = 0.22f), RoundedCornerShape(windowRadius)),
        ) {
            // glcn: "On air" is said once, by the channel's name on the left; the window only wears a live dot.
            Row(
                Modifier.align(Alignment.TopStart).padding(10.mpx)
                    .background(Color(0xCC0A1012), RoundedCornerShape(6.mpx)).padding(horizontal = 8.mpx, vertical = 3.mpx),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.mpx),
            ) {
                Box(Modifier.size(8.mpx).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFF3B30)))
                Text(onAirLabel(cur?.title), style = stageText(13, 800), color = Color.White, maxLines = 1)
            }
        }

        Column(Modifier.padding(start = 96.mpx, top = 80.mpx, end = 96.mpx).width(1240.mpx).then(slide)) {
            Row(Modifier.then(slideInLeft), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                val logo = channel.displayLogoUrl?.takeIf { it.isNotBlank() }
                if (logo != null) ChannelLogoTile(logoUrl = logo, modifier = Modifier.size(132.mpx, 80.mpx), fill = Color.Transparent, surfaceLuminance = if (light) 0.95f else 0.02f) { }
                Column {
                    Text(stringResource(R.string.glcn_pi_on_air).uppercase(), style = stageText(15, 800), color = accentInk, maxLines = 1)
                    Text(
                        tv.own.owntv.core.live.ProviderTags.parse(channel.name).name,
                        style = stageText(22, 700), color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(26.mpx))
            if (cur == null) {
                Text(
                    if (schedule == null || shown?.loading == true) "" else stringResource(R.string.glcn_pi_no_guide),
                    style = stageText(26, 600), color = Soft,
                )
            } else {
                val letterUrl = filmLogo ?: titleLogo
                if (!letterUrl.isNullOrBlank() && !logoFailed) {
                    tv.own.owntv.ui.stage.StageTitleArt(
                        url = letterUrl, contentDescription = cur.title,
                        modifier = Modifier.then(slideInLeft).width(640.mpx).height(150.mpx), alignment = Alignment.CenterStart,
                        onFailed = { logoFailed = true }, onDarkScreen = true,
                    )
                } else {
                    Text(cur.title, style = tv.own.owntv.ui.theme.stageDisplay(62), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.then(slideInLeft))
                }
                val remaining = ((cur.stopMs - nowMs) / 60_000L).toInt()
                Text(
                    if (remaining in 1..600) stringResource(R.string.glcn_pi_ends_left, timeFmt.format(java.util.Date(cur.stopMs)), remaining)
                    else stringResource(R.string.content_live_time_range, timeFmt.format(java.util.Date(cur.startMs)), timeFmt.format(java.util.Date(cur.stopMs))),
                    style = stageText(24, 600), color = Soft, modifier = Modifier.then(slideInLeft).padding(top = 10.mpx), maxLines = 1,
                )
                val span = (cur.stopMs - cur.startMs).coerceAtLeast(1L)
                val f = ((nowMs - cur.startMs).toFloat() / span).coerceIn(0f, 1f)
                Box(Modifier.then(slideInLeft).padding(top = 14.mpx).width(560.mpx).height(5.mpx).clip(RoundedCornerShape(3.mpx)).background(Ink.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(f).height(5.mpx).background(accent))
                }
                // glcn: a match the sports board knows: both crests, the score and the minute.
                tv.own.owntv.features.sports.rememberProgrammeMatch(cur.title, cur.startMs, cur.stopMs)?.let { m ->
                    tv.own.owntv.features.sports.ProgrammeMatchStrip(m, Modifier.then(slideInLeft).padding(top = 22.mpx))
                }
                // glcn: the guide's synopsis without its tacked-on "(Genre, Genre)" and "País: …" (the genres become tags).
                val (guideGenres, guideSynopsis) = remember(ownSynopsis) { tv.own.owntv.features.shell.components.cleanSynopsis(ownSynopsis) }
                val genres = (film?.genres?.takeIf { it.isNotEmpty() }
                    ?: cur.details.categoryList.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }
                    ?: guideGenres).distinct().take(3)
                val minutes = ((cur.stopMs - cur.startMs) / 60_000L).toInt()
                val facts = genres + listOfNotNull(
                    (film?.year ?: cur.year ?: lent?.year)?.toString(),
                    minutes.takeIf { it in 1..600 }?.let { m ->
                        if (m >= 60) stringResource(R.string.content_duration_hours, m / 60, m % 60) else stringResource(R.string.content_duration_minutes, m)
                    },
                    (film?.rating?.takeIf { it > 0 } ?: lent?.rating)?.let { "★ " + ProgrammeArtResolver.format(it) },
                ).filter { it.isNotBlank() }
                Row(Modifier.then(slideInLeft).padding(top = 16.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                    // glcn: the age rating as a badge of its own ("ES 7"), not lost in the line.
                    cur.rating?.takeIf { it.isNotBlank() }?.let { age ->
                        Text(
                            age, style = stageText(16, 800), color = Ink, maxLines = 1,
                            modifier = Modifier.border(1.5.mpx, Ink.copy(alpha = 0.6f), RoundedCornerShape(6.mpx)).padding(horizontal = 8.mpx, vertical = 2.mpx),
                        )
                    }
                    facts.forEach { fact ->
                        Text(
                            fact, style = stageText(17, 700), color = Soft, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(8.mpx)).background(Ink.copy(alpha = 0.12f)).padding(horizontal = 10.mpx, vertical = 3.mpx),
                        )
                    }
                }
                // A film: TMDB's synopsis (in your language) before the guide's, which is often a list of credits.
                ((film?.overview?.takeIf { it.isNotBlank() }) ?: guideSynopsis.takeIf { it.isNotBlank() } ?: lent?.overview)?.let {
                    // glcn: as wide as the room up to the panels on the right, and a line more, so most synopses read whole
                    // without drifting.
                    // glcn: the cast sits on the right now, so the synopsis has the whole column down to the button.
                    // glcn: kept to the left half, so the picture's faces stay clear of it.
                    // glcn: as many lines as fit between where it starts and the buttons fixed at the foot — a long synopsis
                    // reads whole instead of drifting, a short one changes nothing.
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val pageH = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toFloat()
                    var synTop by remember(cur.id) { mutableStateOf(-1f) }
                    val synStyle = stageText(21, if (light) 500 else 400)
                    val fitLines = if (synTop < 0f || pageH <= 0f) SYNOPSIS_LINES else with(density) {
                        val lineH = synStyle.fontSize.toPx() * SYNOPSIS_LINE_HEIGHT
                        val room = pageH - synTop - (INFO_ACTIONS_BOTTOM + INFO_BUTTON_H + SYNOPSIS_CLEARANCE).toPx()
                        (room / lineH).toInt().coerceIn(SYNOPSIS_LINES, SYNOPSIS_LINES_MAX)
                    }
                    DriftingSynopsis(
                        it,
                        modifier = Modifier.then(slideInLeft).padding(top = 18.mpx).width(SYNOPSIS_W)
                            .onGloballyPositioned { c -> if (synTop < 0f) synTop = c.positionInRoot().y },
                        style = synStyle, color = Soft,
                        lines = if (next.isNotEmpty() && NEXT_CARDS > 0) SYNOPSIS_LINES_WITH_CARDS else fitLines, fade = true,
                    )
                }
            }
            // glcn: what follows on the channel, as picture cards in the room under the buttons (the first one wider).
            if (NEXT_CARDS > 0 && next.isNotEmpty() && !showTech) NextCards(next.take(NEXT_CARDS), timeFmt, Modifier.then(slideInLeft).padding(top = 34.mpx))
        }

        // Right: when it airs again this week, and under it a film's cast.
        if (cur != null) Column(
            Modifier.align(Alignment.TopEnd).padding(end = 96.mpx, top = WINDOW_TOP + WINDOW_H + 16.mpx).width(WINDOW_W).then(slide),
            verticalArrangement = Arrangement.spacedBy(16.mpx),
        ) {
        // glcn: only when it does air again — "no repeats this week" was a panel saying nothing.
        if (again.isNotEmpty()) Column(
            Modifier.then(slideInRight).fillMaxWidth()
                .shadow(tv.own.owntv.ui.theme.StageElevation.CARD, RoundedCornerShape(20.mpx), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)).clip(RoundedCornerShape(20.mpx)).background(PanelFill).padding(24.mpx),
            verticalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            Text(stringResource(R.string.glcn_pi_airs_again), style = stageText(18, 800), color = accentInk, maxLines = 2)
            when {
                else -> again.take(REPEATS_SHOWN).forEach { (a, episode) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                        Text(dayFmt.format(java.util.Date(a.startMs)), style = stageText(19, 700), color = Ink, modifier = Modifier.width(110.mpx), maxLines = 1)
                        Text(timeFmt.format(java.util.Date(a.startMs)), style = stageText(19, 500), color = Soft, maxLines = 1)
                        episode?.let { Text(it, style = stageText(17, 500), color = Dim, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        // glcn: a film's cast, in the room between the repeats and "Up next" — two rows of four, one when the repeats
        // run long (so it never reaches "Up next"); not while the technical data take that corner.
        // glcn: TMDB's cast for a film; otherwise the names the guide gives after "Reparto:" (Wikipedia lends their photos).
        val cast = film?.cast.orEmpty().ifEmpty { guidePeople }
        if (cast.isNotEmpty() && !showTech) Column(
            Modifier.then(slideInRight).fillMaxWidth()
                .shadow(tv.own.owntv.ui.theme.StageElevation.CARD, RoundedCornerShape(20.mpx), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)).clip(RoundedCornerShape(20.mpx)).background(PanelFill).padding(24.mpx),
            verticalArrangement = Arrangement.spacedBy(12.mpx),
        ) {
            Text(stringResource(R.string.content_media_cast), style = stageText(18, 800), color = accentInk, maxLines = 1)
            val castRows = if (again.isNotEmpty()) 1 else 2
            cast.take(CAST_PER_ROW * castRows).chunked(CAST_PER_ROW).forEach { row ->
                CastFaces(row, face = 72.mpx, max = CAST_PER_ROW, ink = Ink)
            }
        }

        // glcn: what follows on the channel, in the same stack, right under the rest (no gap in the middle).
        if (next.size > NEXT_CARDS && !showTech) Column(
            Modifier.then(slideInRight).fillMaxWidth()
                .shadow(tv.own.owntv.ui.theme.StageElevation.CARD, RoundedCornerShape(20.mpx), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)).clip(RoundedCornerShape(20.mpx)).background(PanelFill).padding(24.mpx),
            verticalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            Text(stringResource(if (NEXT_CARDS == 0) R.string.glcn_pi_next else R.string.glcn_pi_later), style = stageText(18, 800), color = accentInk, maxLines = 1)
            val room = when {
                again.isNotEmpty() && cast.isNotEmpty() -> 3
                again.isNotEmpty() || cast.isNotEmpty() -> 4
                else -> 7
            }
            next.drop(NEXT_CARDS).take(room).forEach { n ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    Text(timeFmt.format(java.util.Date(n.startMs)), style = stageText(19, 700), color = Ink, modifier = Modifier.width(84.mpx), maxLines = 1)
                    Text(n.title, style = stageText(18, 500), color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        }

        // glcn: what can be done with it — always in the same place, low on the page above the hint, wherever the
        // synopsis ends; so ◀ ▶ through the channels never moves the buttons.
        InfoActions(
            cur, channel, showTech, onToggleTech = { showTech = !showTech }, onStartOver, onRecord, onDismiss, focus, pageFocus,
            onCast = if (pageCast.isNotEmpty()) ({ castOpen = true }) else null,
            onWatch = if (channel.id != startChannel.id && onTune != null) ({ onTune(channel) }) else null,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 96.mpx, bottom = INFO_ACTIONS_BOTTOM).then(slide),
        )
        if (channels.size > 1) Text(
            "◀ ▶  " + stringResource(R.string.glcn_pi_hint_channels) + "   ·   ▼  " + stringResource(R.string.glcn_pi_hint_options),
            style = stageText(15, 600), color = Dim, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 96.mpx, bottom = 20.mpx),
        )
        if (showTech) StreamInfoOverlay(player, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 96.mpx, bottom = 56.mpx))
    }
}

/** glcn: the page's buttons — from its start (catch-up), record, technical data — right under the synopsis. */
@Composable
private fun InfoActions(
    cur: EpgProgrammeEntity?,
    channel: ChannelEntity,
    showTech: Boolean,
    onToggleTech: () -> Unit,
    onStartOver: ((ChannelEntity, EpgProgrammeEntity) -> Unit)?,
    onRecord: ((ChannelEntity) -> Unit)?,
    onDismiss: () -> Unit,
    focus: FocusRequester,
    pageFocus: FocusRequester,
    /** Another channel's page: watch it. */
    onWatch: (() -> Unit)? = null,
    /** glcn: who is in it — the cast page. */
    onCast: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.onPreviewKeyEvent { e ->
            if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.DirectionUp) {
                runCatching { pageFocus.requestFocus() }; true
            } else false
        },
        horizontalArrangement = Arrangement.spacedBy(12.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // glcn: real buttons (glass, the first one tinted), and ▼ lands on the first of them.
        var first = true
        fun lead(): Modifier = if (first) { first = false; Modifier.focusRequester(focus) } else Modifier
        if (onWatch != null) StageButton(
            text = stringResource(R.string.content_key_watch), onClick = onWatch, icon = OwnTVIcon.PLAY, iconFilled = true,
            height = INFO_BUTTON_H, textSize = INFO_BUTTON_TEXT, modifier = lead(),
        )
        if (cur != null && onStartOver != null && channel.catchup) StageButton(
            text = stringResource(R.string.glcn_pi_start_over), onClick = { onStartOver(channel, cur) }, icon = OwnTVIcon.REWIND,
            height = INFO_BUTTON_H, textSize = INFO_BUTTON_TEXT, modifier = lead(),
        )
        if (cur != null && onRecord != null) StageButton(
            text = stringResource(R.string.recording_record), onClick = { onRecord(channel); onDismiss() }, icon = OwnTVIcon.REC,
            height = INFO_BUTTON_H, textSize = INFO_BUTTON_TEXT, modifier = lead(),
        )
        if (onCast != null) StageButton(
            text = stringResource(R.string.content_media_cast), onClick = onCast, icon = OwnTVIcon.PERSON,
            height = INFO_BUTTON_H, textSize = INFO_BUTTON_TEXT, modifier = lead(),
        )
        StageButton(
            text = stringResource(if (showTech) R.string.glcn_pi_hide_technical else R.string.glcn_pi_technical),
            onClick = onToggleTech,
            icon = OwnTVIcon.INFO,
            height = INFO_BUTTON_H, textSize = INFO_BUTTON_TEXT,
            modifier = lead(),
        )
    }
}

/** glcn: the next programmes as picture cards: their picture (the guide's, or TMDB's), the time and the title. */
@Composable
private fun NextCards(items: List<EpgProgrammeEntity>, timeFmt: java.text.DateFormat, modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.glcn_pi_next), style = stageText(18, 800), color = stageAccent.ink, maxLines = 1, modifier = Modifier.padding(bottom = 12.mpx))
        Row(horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
            items.forEachIndexed { i, p ->
                val w = if (i == 0) NEXT_CARD_FIRST_W else NEXT_CARD_W
                Column(Modifier.width(w)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .shadow(tv.own.owntv.ui.theme.StageElevation.CARD, RoundedCornerShape(14.mpx), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                            .clip(RoundedCornerShape(14.mpx)).background(PanelFill),
                    ) {
                        tv.own.owntv.ui.components.programmeArtOrTmdb(p)?.let { ProgrammeArt(url = it, modifier = Modifier.fillMaxSize()) }
                    }
                    Text(timeFmt.format(java.util.Date(p.startMs)), style = stageText(17, 800), color = Ink, maxLines = 1, modifier = Modifier.padding(top = 8.mpx))
                    Text(p.title, style = stageText(17, 500), color = Soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** glcn: how many of the next programmes show as cards, and their widths. */
// glcn: no picture cards under the buttons — they took the synopsis's room and its first lines; what follows is the
// right-hand list, headed "Up next".
private const val NEXT_CARDS = 0
private val NEXT_CARD_FIRST_W = 330.mpx
private val NEXT_CARD_W = 250.mpx

/** glcn: walking the channels — the old page leaving, the new one coming in, how far they slide, a held key's pace. */
private const val SWAP_OUT_MS = 140
private const val SWAP_IN_MS = 300
private val SWAP_SHIFT = 90.mpx
private const val QUICK_SWAP_MS = 350L
/** glcn: how long a page change waits for the next page's picture and details before sliding in anyway. */
private const val SWAP_READY_MS = 350L
/** glcn: under a held key, how long a page change waits for its page (the neighbours are usually read ahead). */
private const val QUICK_READY_MS = 120L
/** glcn: faces read ahead with a page, and for how long at most. */
private const val CAST_WARM = 8
private const val CAST_WARM_MS = 250L
/** glcn: after a page has slid in, how long its picture, shade and tint still settle (counted as part of the swap). */
private const val SWAP_SETTLE_MS = 700L
private fun nowMsForSwap() = System.currentTimeMillis()
/** glcn: how long a page rests before the channels either side are read ahead. */
private const val PREFETCH_DELAY_MS = 250L

/** glcn: the synopsis' lines, now that the cast is on the right. */
private const val SYNOPSIS_LINES = 9
/** glcn: the most a long synopsis may take, down to the buttons; the room left above them, and its line height. */
private const val SYNOPSIS_LINES_MAX = 18
private val SYNOPSIS_CLEARANCE = 30.mpx
private const val SYNOPSIS_LINE_HEIGHT = 1.4f
/** glcn: fewer, with the next programmes as cards under the buttons (the synopsis drifts to show the rest). */
private const val SYNOPSIS_LINES_WITH_CARDS = 5
private val SYNOPSIS_W = 860.mpx
/** glcn: the cast panel on the right: four a row, two rows unless the repeats above take more than this many lines. */
private const val CAST_PER_ROW = 4
/** glcn: the live window up on the right (the shell places the picture in the same box, see [InfoStage]). */
private val WINDOW_TOP = 150.mpx
private val WINDOW_W = 420.mpx
private val WINDOW_H = 236.mpx
/** glcn: repeats listed under the window, so the column never reaches "Up next". */
private const val REPEATS_SHOWN = 3

/** glcn: the page's buttons, as the guide's. */
private val INFO_BUTTON_H = 56.mpx
/** glcn: the buttons' fixed place, from the foot of the page (the hint line sits under them). */
private val INFO_ACTIONS_BOTTOM = 76.mpx
private const val INFO_BUTTON_TEXT = 19
/** glcn: the shade until the picture has been read — the old fixed one. */
private const val VEIL_UNREAD = 0.82f

/** glcn: how many programmes "Up next" lists. */
private const val NEXT_MAX = 6

/**
 * glcn: the programme info screen's live window — while it is open, the shell draws the channel's picture in this box
 * (up on the right) instead of full screen, and the screen leaves a hole in its backdrop there.
 */
private const val PANEL_IN_MS = 420
private val PANEL_SHIFT = 120.mpx

object InfoStage {
    var liveWindow by mutableStateOf(false)
    /** glcn: how far the picture has shrunk into the window (0 full screen → 1 in the window), set by the shell. */
    var progress by mutableStateOf(0f)

    /** glcn: the frame round the window as laid out, in window coordinates; null until it is. */
    var frame by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    /**
     * The window in a [w] × [h] box whose top-left corner is at [origin] in the window: the frame as it was
     * really laid out when known, else as the screen lays it out at 1920 × 1080 (end 96, top 150, 420 × 236).
     */
    fun windowRect(w: Float, h: Float, origin: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero): androidx.compose.ui.geometry.Rect {
        // glcn: the zap deck's squeeze-back takes the same road for the picture, to its own place.
        if (!liveWindow && ZapStage.liveWindow) return ZapStage.rect(origin) ?: androidx.compose.ui.geometry.Rect(0f, 0f, w, h)
        frame?.let { return it.translate(-origin.x, -origin.y) }
        val sx = w / 1920f
        val sy = h / 1080f
        val left = (1920f - 96f - 420f) * sx
        val top = 150f * sy
        return androidx.compose.ui.geometry.Rect(left, top, left + 420f * sx, top + 236f * sy)
    }
}

/** glcn: the page's backdrop — the film's wide picture, else TMDB's for the programme, else the guide's own. */
private fun pageArt(
    film: tv.own.owntv.core.metadata.MovieDetails?,
    lent: ProgrammeArtResolver.Info?,
    cur: EpgProgrammeEntity?,
): String? = film?.backdropPath?.let { tv.own.owntv.core.metadata.MetadataImages.backdrop(it, "w1280") }
    ?: lent?.backdropUrl ?: cur?.imageUrl?.takeIf { it.isNotBlank() }

/** glcn: one page of the programme screen, read in one go — see ProgrammeInfoScreen's loadPage. */
private data class InfoPage(
    val channelId: Long,
    val rows: List<EpgProgrammeEntity>,
    val again: List<Pair<EpgProgrammeEntity, String?>> = emptyList(),
    val synopsis: String? = null,
    val lent: ProgrammeArtResolver.Info? = null,
    val titleLogo: String? = null,
    val film: tv.own.owntv.core.metadata.MovieDetails? = null,
    val complete: Boolean = false,
    /** Still being read (shown as an empty page, not as "no guide"). */
    val loading: Boolean = false,
)

/**
 * glcn: when [cur] airs again this week. A daily show changes its title every night ("El hormiguero T21 · <guests>"),
 * so a show is also found by its own name — the title without the episode, the season and what follows " · ". Never
 * for sport (a league's name is the same for every match) nor for a name too short to tell shows apart.
 */
private fun airsAgain(cur: EpgProgrammeEntity, week: List<EpgProgrammeEntity>): List<Pair<EpgProgrammeEntity, String?>> {
    val exact = ProgrammeArtResolver.fold(cur.title)
    val show = ProgrammeArtResolver.fold(ProgrammeArtResolver.clean(cur.title))
    val byShow = show.length >= 4 && show != exact && !tv.own.owntv.core.live.SportsNow.isSport(cur)
    return week
        .filter { it.startMs >= cur.stopMs }
        .mapNotNull { p ->
            val t = ProgrammeArtResolver.fold(p.title)
            when {
                t == exact -> p to null
                byShow && !tv.own.owntv.core.live.SportsNow.isSport(p) &&
                    ProgrammeArtResolver.fold(ProgrammeArtResolver.clean(p.title)) == show ->
                    p to p.title.substringAfter(" · ", "").trim().takeIf { it.isNotBlank() }
                else -> null
            }
        }
        .distinctBy { it.first.startMs }
        .take(7)
}
