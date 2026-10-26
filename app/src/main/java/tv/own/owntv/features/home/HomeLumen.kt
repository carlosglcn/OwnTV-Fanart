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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
                art = p?.imageUrl?.takeIf { it.isNotBlank() } ?: ch.displayLogoUrl, backdrop = p?.imageUrl?.takeIf { it.isNotBlank() },
                progress = p?.let { (now - it.startMs).toFloat() / total!! }, landscape = true,
                onClick = { onPlayChannel(ch.id, all) },
            )
        }
        fun movieItem(m: MovieEntity) = LumenItem(
            key = "m" + m.id, title = m.name, line = listOfNotNull(m.year?.toString(), m.rating?.takeIf { it > 0 }?.let(::ratingOf)).joinToString("  ").ifBlank { null },
            plot = m.plot, art = m.posterUrl, backdrop = m.backdropUrl, progress = null, landscape = false, onClick = { onOpenMovie(m) },
        )
        fun seriesItem(s: SeriesEntity) = LumenItem(
            key = "s" + s.id, title = s.name, line = listOfNotNull(s.year?.toString(), s.rating?.takeIf { it > 0 }?.let(::ratingOf)).joinToString("  ").ifBlank { null },
            plot = s.plot, art = s.posterUrl, backdrop = s.backdropUrl, progress = null, landscape = false, onClick = { onOpenSeries(s) },
        )
        buildList {
            val keep = state.continueMovies.map { c ->
                LumenItem("p" + c.stableKey, c.title, c.subtitle, null, c.posterUrl, null, c.durationMs.takeIf { it > 0 }?.let { c.positionMs.toFloat() / it }, false) { onPlayMovie(c.sourceItemId, c.positionMs) }
            } + state.continueSeries.map { c ->
                LumenItem("p" + c.stableKey, c.title, c.subtitle, null, c.posterUrl, null, c.durationMs.takeIf { it > 0 }?.let { c.positionMs.toFloat() / it }, false) { onPlayEpisode(0L, c.targetItemId, c.positionMs) }
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
                        LumenItem("t" + t.stableKey, name, year?.toString(), plot, poster, back, null, false) { onActivateTrending(t) {} }
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
    var menuRow by remember { mutableStateOf<HomeRow?>(null) }

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

    Box(modifier.fillMaxSize()) {
        // The focused card's wide picture, settled before it swaps; the page's own colour washes it out.
        var settled by remember { mutableStateOf<String?>(null) }
        val wanted = shown?.backdrop ?: shown?.art?.takeIf { shown.landscape.not() }
        // A card with no wide picture keeps the backdrop that is there, so the screen never flashes empty.
        LaunchedEffect(wanted, shown?.crests == null) {
            if (wanted != null) { delay(250); settled = wanted } else if (shown?.crests != null) settled = null
        }
        Crossfade(settled, modifier = Modifier.fillMaxSize(), label = "lumen-backdrop") { url ->
            if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(1f))
        }
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
        Column(Modifier.padding(start = contentStart + 24.mpx, top = 120.mpx).width(900.mpx)) {
            Text(tab.row.displayTitle(), style = stageText(19, 700), color = accent.accent, maxLines = 1)
            Spacer(Modifier.height(14.mpx))
            Text(shown?.title.orEmpty(), style = stageText(64f, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            shown?.line?.let { Text(it, style = stageText(23, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.mpx)) }
            (shown?.plot ?: shown?.programmeId?.let { synopses[it] })?.takeIf { it.isNotBlank() }?.let { tv.own.owntv.features.shell.components.DriftingSynopsis(it, modifier = Modifier.padding(top = 16.mpx).width(760.mpx), style = stageText(20, 400), color = StageColors.Body) }
        }

        // glcn: no box — a gradient from clear to the page's own colour, so the dock grows out of the picture.
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(560.mpx)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to palette.page.copy(alpha = 0.7f), 1f to palette.page.copy(alpha = 0.96f))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = contentStart, end = 56.mpx, bottom = 28.mpx)
                .fillMaxWidth()
                .height(452.mpx)
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
            val listState = rememberLazyListState()
            LaunchedEffect(selected) { listState.scrollToItem(0) }
            // glcn: the card with the focus is always the first one, wide, at the left; the rest glide left to
            // meet it. The scroll follows the focus (tracking the cards as they change size), and the TV's
            // own "bring into view" is switched off so the two never fight.
            val focusedAt = tab.items.indexOfFirst { it.key == focusedKey }.coerceAtLeast(0)
            LaunchedEffect(selected, focusedAt) {
                if (tv.own.owntv.glcn.LowRam.on) listState.scrollToItem(focusedAt) else listState.animateScrollToItem(focusedAt)
            }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides NoBringIntoView) {
            LazyRow(
                state = listState,
                modifier = Modifier.padding(top = 18.mpx).focusRestorer().focusGroup(),
                // Room at the end, so the last cards can also come to the left edge.
                contentPadding = PaddingValues(start = 30.mpx, end = 900.mpx, top = 8.mpx, bottom = 8.mpx),
                horizontalArrangement = Arrangement.spacedBy(12.mpx),
            ) {
                itemsIndexed(tab.items, key = { _, it -> it.key }) { index, item ->
                    val m = Modifier
                        .then(if (index == 0) Modifier.focusRequester(entry) else Modifier)
                        .focusProperties { up = selectedTab }
                        .onFocusChanged { if (it.hasFocus) { focusedKey = item.key; onChildFocused() } }
                    // glcn: the card the focus rests on opens wide, with its landscape picture; the others stay
                    // narrow. One composable per card, so the focus is never lost while it opens.
                    val wideArt = item.backdrop ?: item.art.takeIf { item.landscape }
                    val canOpen = item.crests == null && wideArt != null
                    val open = canOpen && item.key == (tab.items.firstOrNull { it.key == focusedKey } ?: tab.items.firstOrNull())?.key
                    // One height for every card; only the width changes.
                    val tw = if (open) 500.mpx else if (item.landscape) 250.mpx else 187.mpx
                    val low = tv.own.owntv.glcn.LowRam.on
                    val spec = if (low) androidx.compose.animation.core.snap<Dp>() else androidx.compose.animation.core.tween<Dp>(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    val w by androidx.compose.animation.core.animateDpAsState(tw, spec, label = "dock-card-w")
                    val h = 281.mpx
                    val wideA by androidx.compose.animation.core.animateFloatAsState(
                        if (open) 1f else 0f,
                        if (low) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        label = "dock-card-wide",
                    )
                    StageStill(
                        title = item.title, line = if (open || item.landscape) item.line else null, onClick = item.onClick, width = w, height = h,
                        titleSize = if (open) 21 else 17, lineSize = if (open) 16 else 14, modifier = m, progress = item.progress,
                        onLongClick = { menuRow = tab.row },
                    ) {
                        if (item.crests != null) Row(
                            Modifier.fillMaxSize().background(accent.accent.copy(alpha = 0.14f)),
                            horizontalArrangement = Arrangement.spacedBy(20.mpx, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { item.crests.forEach { tv.own.owntv.features.sports.Crest(it, 84) } }
                        else {
                            // The closed picture stays underneath; the wide one fades in over it as the card opens.
                            if (item.art != null) AsyncImage(model = item.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Box(Modifier.fillMaxSize().background(accent.accent.copy(alpha = 0.18f)))
                            if (wideArt != null && wideArt != item.art && (open || wideA > 0.01f)) AsyncImage(model = wideArt, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(wideA))
                        }
                    }
                }
            }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private val NoBringIntoView = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

private data class TrendingFacts(val name: String, val poster: String?, val backdrop: String?, val plot: String?, val year: Int?)

private fun ratingOf(r: Double): String = String.format(java.util.Locale.getDefault(), "%.1f", r)
