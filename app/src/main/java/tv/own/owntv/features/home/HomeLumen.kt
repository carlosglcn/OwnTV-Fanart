@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package tv.own.owntv.features.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.home.TrendingHomeItem
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.features.sports.SportsViewModel
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.stage.StageStill
import tv.own.owntv.ui.stage.StageTab
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/** One card of the Lumen dock: what it shows, what is written big above it when focused, what OK does. */
private class LumenItem(
    val key: String,
    val title: String,
    val line: String?,
    val plot: String?,
    val art: String?,
    val backdrop: String?,
    val progress: Float?,
    val landscape: Boolean,
    /** A match has no picture: its two crests are the art, on the card and large over the title. */
    val crests: List<tv.own.owntv.core.sports.model.Team?>? = null,
    /** A channel's programme on air: its synopsis is not in the guide slice, so it is read when the card is focused. */
    val programmeId: Long? = null,
    /** glcn: a channel's logo — whole and centred on the closed card (over its programme's picture, dimmed and softened). */
    val logo: String? = null,
    /** glcn: the film or series behind the card — its TMDB logo and trailer for the title above, "Details" opens it. */
    val movie: MovieEntity? = null,
    val series: SeriesEntity? = null,
    /** glcn: the main button above the dock ("Play", "Resume", "Watch") and what it does. */
    val playLabel: Int? = null,
    val onPlay: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

private class LumenTab(val row: HomeRow, val items: List<LumenItem>)

/**
 * glcn: Home as Lumen draws it — the focused title large over its picture, and under it a glass dock with
 * tabs (the rows the user has switched on) and one row of cards. It is used only while a Lumen style is
 * chosen; the classic Home is untouched. Same callbacks, same focus hand-offs with the shell.
 */
@Composable
fun LumenHome(
    vm: HomeViewModel,
    onPlayMovie: (movieId: Long, positionMs: Long) -> Unit,
    onPlayEpisode: (seriesId: Long, episodeId: Long, positionMs: Long) -> Unit,
    onPlayChannel: (channelId: Long, zapChannels: List<ChannelEntity>) -> Unit,
    onActivateTrending: (TrendingHomeItem, onUnavailable: () -> Unit) -> Unit,
    onOpenMovie: (MovieEntity) -> Unit,
    onOpenSeries: (SeriesEntity) -> Unit,
    onOpenMatch: (Match) -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean,
    onRestored: () -> Unit,
    firstRowFocusRequester: FocusRequester?,
    onEntryHook: (((() -> Boolean)?) -> Unit)?,
    contentStart: Dp,
    modifier: Modifier = Modifier,
    onHideRow: (HomeRow) -> Unit = {},
    sportsVm: SportsViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val order = state.config.visibleOrder
    val matches by sportsVm.homeRow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = LocalStagePalette.current
    val accent = stageAccent

    val tabs = remember(state, matches) {
        val now = System.currentTimeMillis()
        val timeFmt = android.text.format.DateFormat.getTimeFormat(context)
        fun channelItem(ch: ChannelEntity, guide: tv.own.owntv.core.home.GuideSliceState, all: List<ChannelEntity>): LumenItem {
            val p = guide.programmes[ch.id]?.firstOrNull { now >= it.startMs && now < it.stopMs }
            val total = p?.let { (it.stopMs - it.startMs).coerceAtLeast(1L) }
            return LumenItem(
                key = "c" + ch.id, title = p?.title ?: ch.name,
                line = if (p != null) listOf(timeFmt.format(java.util.Date(p.startMs)) + " – " + timeFmt.format(java.util.Date(p.stopMs)), ch.name).joinToString("  ·  ") else ch.name,
                plot = p?.description,
                programmeId = p?.id,
                art = p?.imageUrl?.takeIf { it.isNotBlank() }, backdrop = p?.imageUrl?.takeIf { it.isNotBlank() },
                progress = p?.let { (now - it.startMs).toFloat() / total!! }, landscape = true,
                logo = ch.displayLogoUrl?.takeIf { it.isNotBlank() } ?: "",
                playLabel = R.string.glcn_dock_watch, onPlay = { onPlayChannel(ch.id, all) },
                onClick = { onPlayChannel(ch.id, all) },
            )
        }
        fun movieItem(m: MovieEntity) = LumenItem(
            key = "m" + m.id, title = m.name, line = listOfNotNull(m.year?.toString(), m.rating?.takeIf { it > 0 }?.let(::ratingOf)).joinToString("  ").ifBlank { null },
            plot = m.plot, art = m.posterUrl, backdrop = m.backdropUrl, progress = null, landscape = false,
            movie = m, playLabel = R.string.content_play, onPlay = { onPlayMovie(m.id, 0L) }, onClick = { onOpenMovie(m) },
        )
        fun seriesItem(s: SeriesEntity) = LumenItem(
            key = "s" + s.id, title = s.name, line = listOfNotNull(s.year?.toString(), s.rating?.takeIf { it > 0 }?.let(::ratingOf)).joinToString("  ").ifBlank { null },
            plot = s.plot, art = s.posterUrl, backdrop = s.backdropUrl, progress = null, landscape = false,
            series = s, onClick = { onOpenSeries(s) },
        )
        buildList {
            val keep = state.continueMovies.map { c ->
                LumenItem("p" + c.stableKey, c.title, c.subtitle, c.plot, c.posterUrl, c.backdropUrl, c.durationMs.takeIf { it > 0 }?.let { c.positionMs.toFloat() / it }, false,
                    playLabel = R.string.glcn_dock_resume, onPlay = { onPlayMovie(c.sourceItemId, c.positionMs) }) { onPlayMovie(c.sourceItemId, c.positionMs) }
            } + state.continueSeries.map { c ->
                LumenItem("p" + c.stableKey, c.title, c.subtitle, c.plot, c.posterUrl, c.backdropUrl, c.durationMs.takeIf { it > 0 }?.let { c.positionMs.toFloat() / it }, false,
                    playLabel = R.string.glcn_dock_resume, onPlay = { onPlayEpisode(0L, c.targetItemId, c.positionMs) }) { onPlayEpisode(0L, c.targetItemId, c.positionMs) }
            }
            if (keep.isNotEmpty() && order.any { it == HomeRow.HERO || it == HomeRow.CONTINUE_MOVIES || it == HomeRow.CONTINUE_SERIES }) add(LumenTab(HomeRow.HERO, keep))
            if (state.recentLive.isNotEmpty() && HomeRow.RECENT_CHANNELS in order) add(LumenTab(HomeRow.RECENT_CHANNELS, state.recentLive.map { channelItem(it, state.recentGuide, state.recentLive) }))
            if (state.favoriteLive.isNotEmpty() && HomeRow.FAVORITE_CHANNELS in order) add(LumenTab(HomeRow.FAVORITE_CHANNELS, state.favoriteLive.map { channelItem(it, state.favoriteGuide, state.favoriteLive) }))
            if (matches.isNotEmpty() && HomeRow.LIVE_SPORTS in order) add(
                LumenTab(
                    HomeRow.LIVE_SPORTS,
                    matches.map { m -> LumenItem("x" + m.key, MatchNotifications.pair(context, m), m.leagueName, null, null, null, null, true, listOf(m.home?.team, m.away?.team).takeIf { m.competitors.size >= 2 }) { onOpenMatch(m) } },
                ),
            )
            if (state.trendingItems.isNotEmpty() && HomeRow.TRENDING in order) add(
                LumenTab(
                    HomeRow.TRENDING,
                    state.trendingItems.map { t ->
                        val (name, poster, back, plot, year) = when (t) {
                            is TrendingHomeItem.Movie -> TrendingFacts(t.movie.name, t.movie.posterUrl, t.movie.backdropUrl, t.movie.plot, t.movie.year)
                            is TrendingHomeItem.Series -> TrendingFacts(t.series.name, t.series.posterUrl, t.series.backdropUrl, t.series.plot, t.series.year)
                        }
                        LumenItem("t" + t.stableKey, name, year?.toString(), plot, poster, back, null, false,
                            movie = (t as? TrendingHomeItem.Movie)?.movie, series = (t as? TrendingHomeItem.Series)?.series) { onActivateTrending(t) {} }
                    },
                ),
            )
            if (state.favoriteMovies.isNotEmpty() && HomeRow.FAVORITE_MOVIES in order) add(LumenTab(HomeRow.FAVORITE_MOVIES, state.favoriteMovies.map(::movieItem)))
            if (state.favoriteSeries.isNotEmpty() && HomeRow.FAVORITE_SERIES in order) add(LumenTab(HomeRow.FAVORITE_SERIES, state.favoriteSeries.map(::seriesItem)))
            if (state.recentMovies.isNotEmpty() && HomeRow.RECENT_MOVIES in order) add(LumenTab(HomeRow.RECENT_MOVIES, state.recentMovies.map(::movieItem)))
            if (state.recentSeries.isNotEmpty() && HomeRow.RECENT_SERIES in order) add(LumenTab(HomeRow.RECENT_SERIES, state.recentSeries.map(::seriesItem)))
        }
    }

    var selected by remember { mutableIntStateOf(0) }
    val tab = tabs.getOrNull(selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)))
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val shown = tab?.items?.firstOrNull { it.key == focusedKey } ?: tab?.items?.firstOrNull()
    val entry = firstRowFocusRequester ?: remember { FocusRequester() }
    val epgDao: tv.own.owntv.core.database.dao.EpgDao = org.koin.compose.koinInject()
    val synopses = remember { androidx.compose.runtime.mutableStateMapOf<Long, String>() }
    val wantedProgramme = shown?.programmeId
    LaunchedEffect(wantedProgramme) {
        val id = wantedProgramme ?: return@LaunchedEffect
        if (id in synopses) return@LaunchedEffect
        delay(150) // a held key crosses many cards: only the one it rests on is read
        val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { epgDao.programmeDescription(id) }.getOrNull() }
        if (!text.isNullOrBlank()) synopses[id] = text
    }
    val selectedTab = remember { FocusRequester() }
    // glcn: after ▼ moved to the next tab, its first card takes the focus once it is laid out.
    var pendingEntry by remember { mutableStateOf(false) }
    LaunchedEffect(pendingEntry, selected) {
        if (!pendingEntry) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        runCatching { entry.requestFocus() }
        pendingEntry = false
    }
    var menuRow by remember { mutableStateOf<HomeRow?>(null) }
    // glcn: TMDB for the film or series the focus rests on — its title logo and trailer.
    val metaRepo: tv.own.owntv.core.metadata.MetadataRepository = org.koin.compose.koinInject()
    val metas = remember { androidx.compose.runtime.mutableStateMapOf<String, tv.own.owntv.core.database.entity.MetadataCacheEntity>() }
    val logoFailed = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    LaunchedEffect(shown?.key) {
        val it0 = shown ?: return@LaunchedEffect
        if (it0.key in metas || (it0.movie == null && it0.series == null)) return@LaunchedEffect
        delay(220)
        val meta = runCatching { it0.movie?.let { metaRepo.resolveMovie(it) } ?: it0.series?.let { metaRepo.resolveSeries(it) } }.getOrNull()
        if (meta != null) metas[it0.key] = meta
    }
    var trailer by remember { mutableStateOf<Pair<String, String>?>(null) }
    trailer?.let { (key, title) -> tv.own.owntv.ui.components.TrailerPlayerScreen(videoKey = key, title = title) { trailer = null } }

    DisposableEffect(onEntryHook, entry) {
        onEntryHook?.invoke { runCatching { entry.requestFocus() }.isSuccess }
        onDispose { onEntryHook?.invoke(null) }
    }
    LaunchedEffect(restoreFocus, tabs.isNotEmpty()) {
        if (restoreFocus && tabs.isNotEmpty()) {
            delay(80)
            runCatching { entry.requestFocus() }
            onRestored()
        }
    }
    if (state.isLoading) return

    // glcn: resting — after a few seconds the picture becomes the title's trailer (silent; not in light mode), and
    // after twenty the words and the dock fade away, leaving the picture alone; the next key brings them back.
    val lastKeyAt = remember { longArrayOf(android.os.SystemClock.uptimeMillis()) }
    var resting by remember { mutableStateOf(false) }
    var ambientKey by remember { mutableStateOf<String?>(null) }
    val shownTrailer by androidx.compose.runtime.rememberUpdatedState(shown?.let { metas[it.key]?.trailerKey })
    val animsOn by androidx.compose.runtime.rememberUpdatedState(tv.own.owntv.ui.theme.animationsOn)
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val quiet = android.os.SystemClock.uptimeMillis() - lastKeyAt[0]
            if (!resting && quiet >= DOCK_REST_MS) resting = true
            if (ambientKey == null && quiet >= DOCK_TRAILER_MS && !tv.own.owntv.glcn.LowRam.on && animsOn) {
                ambientKey = shownTrailer
            }
        }
    }
    LaunchedEffect(shown?.key) { ambientKey = null }
    val chrome by androidx.compose.animation.core.animateFloatAsState(if (resting) 0f else 1f, androidx.compose.animation.core.tween(if (resting) 1200 else 250), label = "dockRest")

    Box(
        modifier.fillMaxSize().onPreviewKeyEvent { e ->
            lastKeyAt[0] = android.os.SystemClock.uptimeMillis()
            if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) ambientKey = null
            // The first key after resting only wakes the page.
            if (resting) { if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) resting = false; true } else false
        },
    ) {
        // The focused card's wide picture, settled before it swaps; the page's own colour washes it out.
        var settled by remember { mutableStateOf<String?>(null) }
        val wanted = shown?.backdrop ?: shown?.art?.takeIf { shown.landscape.not() }
        // A card with no wide picture keeps the backdrop that is there, so the screen never flashes empty.
        LaunchedEffect(wanted, shown?.crests == null) {
            if (wanted != null) { delay(150); settled = wanted } else if (shown?.crests != null) settled = null
        }
        Crossfade(settled, modifier = Modifier.fillMaxSize(), label = "lumen-backdrop") { url ->
            if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(1f))
        }
        ambientKey?.let { key -> androidx.compose.runtime.key(key) { tv.own.owntv.ui.components.AmbientTrailer(key, Modifier.fillMaxSize()) { ambientKey = null } } }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(palette.page.copy(alpha = 0.78f), palette.page.copy(alpha = 0.34f), palette.page.copy(alpha = 0f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to palette.page.copy(alpha = 0.95f))))

        shown?.crests?.let { pair ->
            Row(Modifier.align(Alignment.TopEnd).padding(end = 140.mpx, top = 170.mpx), horizontalArrangement = Arrangement.spacedBy(48.mpx)) {
                pair.forEach { tv.own.owntv.features.sports.Crest(it, 230) }
            }
        }
        menuRow?.let { row ->
            KeepWatchingMenu(
                title = row.displayTitle(), onRemove = { onHideRow(row) }, onDismiss = { menuRow = null },
                label = stringResource(R.string.settings_home_hide_row),
            )
        }
        if (tab == null) return@Box
        Column(Modifier.padding(start = contentStart + 24.mpx, top = 120.mpx).width(1240.mpx).graphicsLayer { alpha = chrome }) {
            Text(tab.row.displayTitle(), style = stageText(19, 700), color = accent.accent, maxLines = 1)
            Spacer(Modifier.height(14.mpx))
            // glcn: the words change with a short cross-fade, not a cut, as the focus moves.
            // glcn: the old words fade out quickly, the new ones fade in while rising a few pixels — one soft handover.
            androidx.compose.animation.AnimatedContent(
                targetState = shown?.key,
                transitionSpec = {
                    if (tv.own.owntv.glcn.LowRam.on) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                    else (
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320, delayMillis = 90, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(380, delayMillis = 60, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))) { it / 16 }
                        ) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(110)) using
                        androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() }
                },
                label = "lumen-words",
            ) { k ->
                val it0 = tab.items.firstOrNull { x -> x.key == k }
                Column {
                    // glcn: the title's own lettering (TMDB logo) when it has one; the name in words otherwise.
                    val logoUrl = k?.takeIf { it !in logoFailed }?.let { metas[it]?.logoPath }?.let { tv.own.owntv.core.metadata.MetadataImages.logo(it) }
                    if (logoUrl != null) tv.own.owntv.ui.stage.StageTitleArt(
                        url = logoUrl, contentDescription = it0?.title,
                        modifier = Modifier.height(150.mpx).widthIn(max = 760.mpx),
                        onFailed = { k?.let { logoFailed.add(it) } },
                    ) else Text(it0?.title.orEmpty(), style = stageText(64f, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    it0?.line?.let { Text(it, style = stageText(23, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.mpx)) }
                    // glcn: how far along — a film or episode left half-way, or the programme on air.
                    it0?.progress?.takeIf { it > 0f }?.let { tv.own.owntv.ui.stage.StageProgress(it.coerceIn(0f, 1f), Modifier.padding(top = 14.mpx).width(440.mpx)) }
                    (it0?.plot ?: it0?.programmeId?.let { synopses[it] })?.takeIf { it.isNotBlank() }?.let { tv.own.owntv.features.shell.components.DriftingSynopsis(it, modifier = Modifier.padding(top = 16.mpx).width(1040.mpx), style = stageText(20, 400), color = StageColors.Body, lines = if (tv.own.owntv.glcn.DockPrefs.doubleRow) 2 else 3, fade = true) }
                }
            }
            // glcn: what to do with it without opening it — ▲ from the tabs reaches these.
            shown?.let { it0 ->
                val trailerKey = metas[it0.key]?.trailerKey?.let { tv.own.owntv.core.metadata.TrailerKeys.split(it).firstOrNull() }
                val details = it0.movie != null || it0.series != null
                if (it0.onPlay != null || details || trailerKey != null) Row(Modifier.padding(top = 18.mpx).focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    it0.onPlay?.let { play ->
                        tv.own.owntv.ui.stage.StageButton(stringResource(it0.playLabel ?: R.string.content_play), onClick = play, icon = OwnTVIcon.PLAY, iconFilled = true, height = 52.mpx, textSize = 19)
                    }
                    if (details) tv.own.owntv.ui.stage.StageButton(stringResource(R.string.glcn_dock_details), onClick = it0.onClick, icon = OwnTVIcon.INFO, height = 52.mpx, textSize = 19)
                    trailerKey?.let { key ->
                        tv.own.owntv.ui.stage.StageButton(stringResource(R.string.glcn_dock_trailer), onClick = { trailer = key to it0.title }, icon = OwnTVIcon.VIDEO, height = 52.mpx, textSize = 19)
                    }
                }
            }
        }

        // glcn: no box — a gradient from clear to the page's own colour, so the dock grows out of the picture.
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(560.mpx).graphicsLayer { alpha = chrome }
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to palette.page.copy(alpha = 0.7f), 1f to palette.page.copy(alpha = 0.96f))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer { alpha = chrome }
                .padding(start = contentStart, end = 56.mpx, bottom = 28.mpx)
                .fillMaxWidth()
                .height(if (tv.own.owntv.glcn.DockPrefs.doubleRow) 548.mpx else 452.mpx)
                .padding(top = 20.mpx),
        ) {
            LazyRow(Modifier.padding(horizontal = 24.mpx).focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
                itemsIndexed(tabs, key = { _, t -> t.row.name }) { i, t ->
                    StageTab(
                        label = t.row.displayTitle(), count = t.items.size.toString(), selected = i == selected,
                        onClick = { selected = i },
                        onLongClick = { menuRow = t.row },
                        modifier = Modifier.then(if (i == selected) Modifier.focusRequester(selectedTab) else Modifier).onFocusChanged { if (it.hasFocus) { selected = i; onChildFocused() } },
                    )
                }
            }
            // glcn: ONE number (pos) drives the whole row — which card is the wide one, and how far the row has
            // glided. The wide card hands over to the next by dissolving (its picture fades into the next card's
            // as that one widens) while the others slide left, all in a single continuous motion. A spring that
            // is simply re-aimed on every key press keeps its speed, so a held key flows instead of stuttering.
            val items = tab.items
            val focusedAt = items.indexOfFirst { it.key == focusedKey }.coerceAtLeast(0)
            val pos = remember { androidx.compose.animation.core.Animatable(0f) }
            val lastTab = remember { mutableIntStateOf(selected) }
            // 0 → 2.04: card i is in once it passes 0.13·i, each fading for one unit — a card every ~58 ms.
            val rowAlpha = remember { androidx.compose.animation.core.Animatable(if (tv.own.owntv.glcn.LowRam.on) 2.04f else 0f) }
            LaunchedEffect(selected) {
                if (tv.own.owntv.glcn.LowRam.on) return@LaunchedEffect
                rowAlpha.snapTo(0f)
                rowAlpha.animateTo(2.04f, androidx.compose.animation.core.tween(920, easing = androidx.compose.animation.core.LinearEasing))
            }
            LaunchedEffect(selected, focusedAt) {
                if (lastTab.intValue != selected || tv.own.owntv.glcn.LowRam.on) {
                    lastTab.intValue = selected
                    pos.snapTo(focusedAt.toFloat())
                } else {
                    pos.animateTo(focusedAt.toFloat(), androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 140f))
                }
            }
            // glcn: a little smaller with the double row, so the next tab's cards fit under it.
            val ks = if (tv.own.owntv.glcn.DockPrefs.doubleRow) DOCK_DOUBLE_SCALE else 1f
            val narrow = remember(items, ks) { items.map { x -> ((if (x.landscape) 250 else 187) * ks).toInt() } }
            // glcn: every card opens when it has focus, also one with no wide picture (the last ones of a row were often
            // such, and stayed narrow): its poster then stands whole in the middle of the open card, on a tint.
            val openable = remember(items) { items.map { x -> x.crests == null } }
            fun wideness(i: Int, t: Float): Float = if (openable.getOrElse(i) { false }) (1f - kotlin.math.abs(i - t)).coerceIn(0f, 1f) else 0f
            val k by remember { androidx.compose.runtime.derivedStateOf { kotlin.math.floor(pos.value).toInt() } }
            val from = (minOf(k, focusedAt) - 1).coerceAtLeast(0)
            val to = (maxOf(k, focusedAt) + 9).coerceAtMost(items.lastIndex)
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides NoBringIntoView) {
            androidx.compose.ui.layout.Layout(
                modifier = Modifier.padding(top = 18.mpx, start = 30.mpx).fillMaxWidth().focusRestorer().focusGroup(),
                content = {
                    for (i in from..to) {
                        val item = items[i]
                        androidx.compose.runtime.key(item.key) {
                            val m = Modifier
                                .then(if (i == 0) Modifier.focusRequester(entry) else Modifier)
                                .focusProperties { up = selectedTab }
                                // glcn: ▼ with the double row: the tab underneath becomes the row.
                                .onPreviewKeyEvent { e ->
                                    if (tv.own.owntv.glcn.DockPrefs.doubleRow && e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                                        androidx.compose.ui.input.key.Key.DirectionDown == e.key && selected < tabs.lastIndex
                                    ) { selected += 1; focusedKey = null; pendingEntry = true; true } else false
                                }
                                .onFocusChanged { if (it.hasFocus) { focusedKey = item.key; onChildFocused() } }
                            val wideArt = item.backdrop ?: item.art.takeIf { item.landscape }
                            val near = kotlin.math.abs(i - focusedAt) <= 1 || kotlin.math.abs(i - k) <= 1
                            // The width is set by the row's layout; the numbers here are only the widest it can be.
                            StageStill(
                                title = item.title, line = item.line, onClick = item.onClick, width = 500.mpx * ks, height = 281.mpx * ks,
                                titleSize = 19, lineSize = 15, modifier = m, progress = item.progress,
                                onLongClick = { menuRow = tab.row }, lift = false,
                            ) {
                                if (item.crests != null) Row(
                                    Modifier.fillMaxSize().background(accent.accent.copy(alpha = 0.14f)),
                                    horizontalArrangement = Arrangement.spacedBy(20.mpx, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) { item.crests.forEach { tv.own.owntv.features.sports.Crest(it, 84) } }
                                else if (item.logo != null) {
                                    // glcn: a channel. Closed: its programme's picture dimmed (no GPU blur: it would be redrawn on every frame of the glide), the channel's logo whole in
                                    // the middle (no picture: the logo on a tint). Opening: the picture comes up sharp as the logo fades.
                                    val pic = item.art
                                    if (pic != null) AsyncImage(
                                        model = pic, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    ) else Box(Modifier.fillMaxSize().background(accent.accent.copy(alpha = 0.16f)))
                                    if (pic != null) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - wideness(i, pos.value) }.background(Color.Black.copy(alpha = 0.55f)))
                                    if (near && pic != null) AsyncImage(
                                        model = pic, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = wideness(i, pos.value) },
                                    )
                                    if (item.logo.isNotEmpty()) AsyncImage(
                                        model = item.logo, contentDescription = null, contentScale = ContentScale.Fit,
                                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.62f).fillMaxHeight(0.42f)
                                            .graphicsLayer { alpha = if (pic == null) 1f else 1f - wideness(i, pos.value) },
                                    ) else if (pic == null) Text(
                                        item.title.take(2).uppercase(), style = stageText(40, 800), color = StageColors.Text,
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                }
                                else {
                                    if (item.art != null && wideArt == null && !item.landscape) {
                                        Box(Modifier.fillMaxSize().background(accent.accent.copy(alpha = 0.14f)))
                                        AsyncImage(model = item.art, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                    } else if (item.art != null) AsyncImage(model = item.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else Box(Modifier.fillMaxSize().background(accent.accent.copy(alpha = 0.18f)))
                                    // The wide picture dissolves in over the closed one as the card opens (drawn, never recomposed).
                                    if (near && wideArt != null && wideArt != item.art) AsyncImage(
                                        model = wideArt, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = wideness(i, pos.value) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { measurables, constraints ->
                val t = pos.value
                val kk = kotlin.math.floor(t).toInt()
                val frac = t - kk
                val gap = 12.mpx.roundToPx()
                val wide = (500.mpx * ks).roundToPx()
                val ws = IntArray(measurables.size) { j ->
                    val i = from + j
                    val n = narrow[i].mpx.roundToPx()
                    n + ((wide - n) * wideness(i, t)).toInt()
                }
                val left = IntArray(measurables.size)
                for (j in 1 until measurables.size) left[j] = left[j - 1] + ws[j - 1] + gap
                fun at(i: Int) = left[(i - from).coerceIn(0, measurables.size - 1)]
                val scroll = at(kk) + (at(kk + 1) - at(kk)) * frac
                val placeables = measurables.mapIndexed { j, m ->
                    m.measure(androidx.compose.ui.unit.Constraints(minWidth = ws[j], maxWidth = ws[j], minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                }
                val top = 8.mpx.roundToPx()
                val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 1700.mpx.roundToPx()
                layout(width, (placeables.maxOfOrNull { it.height } ?: 0) + top * 2) {
                    placeables.forEachIndexed { j, p ->
                        val i = from + j
                        val x = (left[j] - scroll).toInt()
                        // A card that has glided past the left edge dissolves away instead of being cut.
                        val a = if (x >= 0) 1f else (1f + x / (0.7f * ws[j])).coerceIn(0f, 1f)
                        p.placeWithLayer(x, top, zIndex = if (i == focusedAt) 1f else 0f) {
                            // Each card arrives a beat after the one before it: fading in while rising a little.
                            val e = androidx.compose.animation.core.FastOutSlowInEasing.transform((rowAlpha.value - 0.13f * minOf(i, 8)).coerceIn(0f, 1f))
                            alpha = a * e
                            translationY = (1f - e) * 24.mpx.toPx()
                        }
                    }
                }
            }
            }
            // glcn: the next tab, peeking under the row — its name and its first cards, small; ▼ makes it the row.
            if (tv.own.owntv.glcn.DockPrefs.doubleRow) tabs.getOrNull(selected + 1)?.let { next ->
                Row(
                    Modifier.padding(start = 30.mpx, top = 6.mpx).graphicsLayer { alpha = 0.72f },
                    horizontalArrangement = Arrangement.spacedBy(10.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(next.row.displayTitle(), style = stageText(17, 700), color = StageColors.Muted, maxLines = 1, modifier = Modifier.width(220.mpx))
                    next.items.take(DOCK_PEEK_CARDS).forEach { n ->
                        val pic = n.backdrop ?: n.art
                        val w = if (n.landscape || n.backdrop != null) 160.mpx else 66.mpx
                        Box(Modifier.size(w, 92.mpx).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.mpx)).background(accent.accent.copy(alpha = 0.12f))) {
                            if (pic != null) AsyncImage(model = pic, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else n.logo?.takeIf { l -> l.isNotEmpty() }?.let { l -> AsyncImage(model = l, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.mpx)) }
                        }
                    }
                }
            }
        }
    }
}

/** glcn: the double row's cards are this much of their usual size, and this many of the next tab peek under them. */
private const val DOCK_DOUBLE_SCALE = 0.84f
private const val DOCK_PEEK_CARDS = 8

private val NoBringIntoView = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

private data class TrendingFacts(val name: String, val poster: String?, val backdrop: String?, val plot: String?, val year: Int?)

private fun ratingOf(r: Double): String = String.format(java.util.Locale.getDefault(), "%.1f", r)

/** glcn: quiet this long and the title's trailer replaces its picture; this long and the words and dock fade away. */
private const val DOCK_TRAILER_MS = 3_000L
private const val DOCK_REST_MS = 20_000L
