package tv.own.owntv.glcn

import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.focusable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
private val Soft: Color @androidx.compose.runtime.Composable get() = if (light) Color(0xFF2E3B3E) else Color(0xFFC9D4D2)
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
) {
    // glcn: the channel whose page is shown — the one playing at first; the window keeps playing that one throughout.
    var channel by remember { mutableStateOf(startChannel) }
    val swap = remember { androidx.compose.animation.core.Animatable(0f) }
    val swapScope = androidx.compose.runtime.rememberCoroutineScope()
    var lastSwapAt by remember { mutableStateOf(0L) }
    val swapAnimated = DeviceClass.richMotion && tv.own.owntv.ui.theme.animationsOn
    fun go(step: Int) {
        if (channels.size < 2) return
        val at = channels.indexOfFirst { it.id == channel.id }.takeIf { it >= 0 } ?: return
        val next = channels[(at + step + channels.size) % channels.size]
        val now = android.os.SystemClock.uptimeMillis()
        // A held key gets shorter moves, so the pages keep up with it.
        val quick = now - lastSwapAt < QUICK_SWAP_MS
        lastSwapAt = now
        val calm = swapAnimated
        swapScope.launch {
            if (!calm) { channel = next; return@launch }
            swap.animateTo(-step.toFloat(), androidx.compose.animation.core.tween(if (quick) 70 else SWAP_OUT_MS, easing = androidx.compose.animation.core.FastOutLinearInEasing))
            channel = next
            swap.snapTo(step.toFloat())
            swap.animateTo(0f, androidx.compose.animation.core.tween(if (quick) 140 else SWAP_IN_MS, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
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
    val resolver: ProgrammeArtResolver = org.koin.compose.koinInject()
    val timeFmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val dayFmt = remember { java.text.SimpleDateFormat("EEE d", java.util.Locale.getDefault()) }
    val nowMs = remember { System.currentTimeMillis() }

    // What is on, and what follows: from the cache the zap lists already filled, so it is there at once.
    val schedule by produceState<List<EpgProgrammeEntity>?>(null, channel.id) {
        value = runCatching { loadSchedule(channel) }.getOrDefault(emptyList())
    }
    val rows = schedule.orEmpty()
    // glcn: with overlapping guides, the one that started last — as the banner, the lists and Home pick it.
    val cur = rows.filter { nowMs in it.startMs until it.stopMs }.maxByOrNull { it.startMs }
    // glcn: what follows — the rest of the evening, not two lines.
    val next = if (cur == null) emptyList() else rows.filter { it.startMs >= cur.stopMs }.take(NEXT_MAX)

    // The week, read after the screen is up; the repeats are the same title again later.
    val week by produceState<List<EpgProgrammeEntity>?>(null, channel.id) {
        value = runCatching { loadWeek(channel) }.getOrDefault(emptyList())
    }
    // glcn: a daily show changes its title every night ("El hormiguero T21 · <guests>"), so a show is also
    // found by its own name — the title without the episode, the season and what follows " · ". Never for
    // sport (a league's name is the same for every match) nor for a name too short to tell shows apart:
    // those still need the very same title.
    val again = remember(cur?.title, week) {
        if (cur == null) return@remember emptyList<Pair<EpgProgrammeEntity, String?>>()
        val exact = ProgrammeArtResolver.fold(cur.title)
        val show = ProgrammeArtResolver.fold(ProgrammeArtResolver.clean(cur.title))
        val byShow = show.length >= 4 && show != exact && !tv.own.owntv.core.live.SportsNow.isSport(cur)
        week.orEmpty()
            .filter { it.startMs >= cur.stopMs }
            .mapNotNull { p ->
                val t = ProgrammeArtResolver.fold(p.title)
                when {
                    t == exact -> p to null
                    byShow && !tv.own.owntv.core.live.SportsNow.isSport(p) &&
                        ProgrammeArtResolver.fold(ProgrammeArtResolver.clean(p.title)) == show ->
                        // Another night of the show: say which one (its guests, its episode).
                        p to p.title.substringAfter(" · ", "").trim().takeIf { it.isNotBlank() }
                    else -> null
                }
            }
            .distinctBy { it.first.startMs }
            .take(7)
    }

    // The zap cache keeps programmes without their synopsis (it is heavy); this one is read on demand.
    val epgDao: tv.own.owntv.core.database.dao.EpgDao = org.koin.compose.koinInject()
    val ownSynopsis by produceState<String?>(null, cur?.id) {
        val c = cur ?: return@produceState
        value = c.description?.takeIf { it.isNotBlank() }
            ?: runCatching { epgDao.programmeDescription(c.id) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    // TMDB lends the wide picture, the rating and a synopsis when the guide says little.
    val lent by produceState<ProgrammeArtResolver.Info?>(null, cur?.title, cur?.categories) {
        val c = cur ?: return@produceState
        value = runCatching { resolver.detailsFor(c.title, c.categories) }.getOrNull()
    }

    // A film's own lettering, when TMDB has it, stands where its name would.
    val titleLogo by produceState<String?>(null, cur?.title, cur?.categories) {
        val c = cur ?: return@produceState
        value = runCatching { resolver.logoFor(c.title, c.categories) }.getOrNull()
    }
    // glcn: a film on the channel, in full (TMDB): lettering, year, genres, rating, synopsis, cast and the wide picture.
    // Warmed when the channel is tuned, so it is usually here at once.
    val film by produceState<tv.own.owntv.core.metadata.MovieDetails?>(null, cur?.title, cur?.categories) {
        val c = cur ?: return@produceState
        value = runCatching { resolver.filmFor(c.title, c.categories) }.getOrNull()
    }
    val filmLogo = film?.logoPath?.let { tv.own.owntv.core.metadata.MetadataImages.logo(it) }
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
            runCatching { resolver.filmFor(onAir.title, onAir.categories) }
            runCatching { resolver.detailsFor(onAir.title, onAir.categories) }
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
        Box(
            Modifier.fillMaxSize()
                .onGloballyPositioned { holeOrigin = it.positionInWindow() }
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                // glcn: the backdrop comes up as the picture shrinks, and the window follows the picture all the way —
                // the TV seems to fold itself into its place on the right instead of jumping there.
                .graphicsLayer { alpha = (InfoStage.progress * 1.4f).coerceAtMost(1f) }
                .drawWithContent {
                    drawContent()
                    val p = InfoStage.progress
                    val r = InfoStage.windowRect(size.width, size.height, holeOrigin)
                    val left = r.left * p
                    val top = r.top * p
                    val w = size.width + (r.width - size.width) * p
                    val h = size.height + (r.height - size.height) * p
                    drawRoundRect(
                        Color.Black, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(windowRadius.toPx() * p),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                    )
                },
        ) {
            Box(Modifier.fillMaxSize().background(Page))
            val art = film?.backdropPath?.let { tv.own.owntv.core.metadata.MetadataImages.backdrop(it, "w1280") }
                ?: lent?.backdropUrl ?: cur?.imageUrl?.takeIf { it.isNotBlank() }
            androidx.compose.animation.Crossfade(targetState = art, animationSpec = androidx.compose.animation.core.tween(SWAP_IN_MS), label = "infoArt") { url ->
                if (url != null) ProgrammeArt(url = url, modifier = Modifier.fillMaxSize())
            }
            // The picture stays behind, never under the words: dark from the left and from the foot.
            // A deeper shade on the left, where the synopsis now runs long, so it reads over bright pictures too.
            val pg = Page
            // glcn: as much shade as this picture needs for the words over it to read, and no more — a pale picture
            // under the light theme (or a dark one under the dark theme) stays seen instead of washed out.
            val tone by ArtTone.of(art)
            val need by androidx.compose.animation.core.animateFloatAsState(tone?.veil(light) ?: VEIL_UNREAD, androidx.compose.animation.core.tween(400), label = "infoVeil")
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to pg.copy(alpha = (need + 0.12f).coerceAtMost(0.96f)), 0.55f to pg.copy(alpha = need),
                        0.75f to pg.copy(alpha = need * 0.55f), 1f to pg.copy(alpha = need * 0.3f),
                    ),
                ),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to pg.copy(alpha = 0.9f))))
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
                Text(stringResource(R.string.glcn_live_badge), style = stageText(13, 800), color = Color.White, maxLines = 1)
            }
        }

        Column(Modifier.padding(start = 96.mpx, top = 80.mpx, end = 96.mpx).width(1240.mpx).then(slide)) {
            Row(Modifier.then(slideInLeft), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                val logo = channel.displayLogoUrl?.takeIf { it.isNotBlank() }
                if (logo != null) ChannelLogoTile(logoUrl = logo, modifier = Modifier.size(132.mpx, 80.mpx), fill = Color.Transparent, surfaceLuminance = if (light) 0.95f else 0.02f) { }
                Column {
                    Text(stringResource(R.string.glcn_pi_on_air).uppercase(), style = stageText(15, 800), color = accent, maxLines = 1)
                    Text(
                        tv.own.owntv.core.live.ProviderTags.parse(channel.name).name,
                        style = stageText(22, 700), color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(26.mpx))
            if (cur == null) {
                Text(
                    if (schedule == null) "" else stringResource(R.string.glcn_pi_no_guide),
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
                    Text(cur.title, style = stageText(58f, 800), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.then(slideInLeft))
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
                    DriftingSynopsis(it, modifier = Modifier.then(slideInLeft).padding(top = 18.mpx).width(SYNOPSIS_W), style = stageText(21, 400), color = Soft, lines = SYNOPSIS_LINES, fade = true)
                }
            }
            // glcn: what can be done with it, right under the synopsis (no longer down at the foot past an empty page).
            InfoActions(
                cur, channel, showTech, onToggleTech = { showTech = !showTech }, onStartOver, onRecord, onDismiss, focus, pageFocus,
                onWatch = if (channel.id != startChannel.id && onTune != null) ({ onTune(channel) }) else null,
            )
        }

        // Right: when it airs again this week, and under it a film's cast.
        if (cur != null) Column(
            Modifier.align(Alignment.TopEnd).padding(end = 96.mpx, top = WINDOW_TOP + WINDOW_H + 16.mpx).width(WINDOW_W).then(slide),
            verticalArrangement = Arrangement.spacedBy(16.mpx),
        ) {
        // glcn: only when it does air again — "no repeats this week" was a panel saying nothing.
        if (again.isNotEmpty()) Column(
            Modifier.then(slideInRight).fillMaxWidth()
                .clip(RoundedCornerShape(20.mpx)).background(PanelFill).padding(24.mpx),
            verticalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            Text(stringResource(R.string.glcn_pi_airs_again), style = stageText(18, 800), color = accent, maxLines = 2)
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
        val cast = film?.cast.orEmpty()
        if (cast.isNotEmpty() && !showTech) Column(
            Modifier.then(slideInRight).fillMaxWidth()
                .clip(RoundedCornerShape(20.mpx)).background(PanelFill).padding(24.mpx),
            verticalArrangement = Arrangement.spacedBy(12.mpx),
        ) {
            Text(stringResource(R.string.content_media_cast), style = stageText(18, 800), color = accent, maxLines = 1)
            val castRows = if (again.isNotEmpty()) 1 else 2
            cast.take(CAST_PER_ROW * castRows).chunked(CAST_PER_ROW).forEach { row ->
                tv.own.owntv.features.shell.components.VodCast(row, CAST_PER_ROW, gap = 12.mpx)
            }
        }

        // glcn: what follows on the channel, in the same stack, right under the rest (no gap in the middle).
        if (next.isNotEmpty() && !showTech) Column(
            Modifier.then(slideInRight).fillMaxWidth()
                .clip(RoundedCornerShape(20.mpx)).background(PanelFill).padding(24.mpx),
            verticalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            Text(stringResource(R.string.glcn_pi_next), style = stageText(18, 800), color = accent, maxLines = 1)
            val room = when {
                again.isNotEmpty() && cast.isNotEmpty() -> 3
                again.isNotEmpty() || cast.isNotEmpty() -> 4
                else -> 7
            }
            next.take(room).forEach { n ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    Text(timeFmt.format(java.util.Date(n.startMs)), style = stageText(19, 700), color = Ink, modifier = Modifier.width(84.mpx), maxLines = 1)
                    Text(n.title, style = stageText(18, 500), color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        }

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
) {
    Row(
        Modifier.padding(top = 30.mpx).onPreviewKeyEvent { e ->
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
            tinted = true, height = INFO_BUTTON_H, textSize = INFO_BUTTON_TEXT, modifier = lead(),
        )
        if (cur != null && onStartOver != null && channel.catchup) StageButton(
            text = stringResource(R.string.glcn_pi_start_over), onClick = { onStartOver(channel, cur) }, icon = OwnTVIcon.REWIND,
            tinted = onWatch == null, height = INFO_BUTTON_H, textSize = INFO_BUTTON_TEXT, modifier = lead(),
        )
        if (cur != null && onRecord != null) StageButton(
            text = stringResource(R.string.recording_record), onClick = { onRecord(channel); onDismiss() }, icon = OwnTVIcon.REC,
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

/** glcn: walking the channels — the old page leaving, the new one coming in, how far they slide, a held key's pace. */
private const val SWAP_OUT_MS = 140
private const val SWAP_IN_MS = 300
private val SWAP_SHIFT = 90.mpx
private const val QUICK_SWAP_MS = 350L
/** glcn: how long a page rests before the channels either side are read ahead. */
private const val PREFETCH_DELAY_MS = 250L

/** glcn: the synopsis' lines, now that the cast is on the right. */
private const val SYNOPSIS_LINES = 9
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
