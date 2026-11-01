package tv.own.owntv.glcn

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
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

private val Ink = Color(0xFFF2F6F5)
private val Soft = Color(0xFFC9D4D2)
private val Dim = Color(0xFF93A09E)

/**
 * glcn: the programme on air, on a screen of its own over the picture — its fanart behind, the channel's
 * logo, what it is (genres, year, age, rating), its synopsis, what follows, and when it airs again this
 * week. Back closes it. The stream's technical data hide behind a button here, off the player bar.
 */
@Composable
fun ProgrammeInfoScreen(
    channel: ChannelEntity,
    player: PlaybackEngine,
    loadSchedule: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    loadWeek: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** glcn: watch the programme on air from its start (catch-up channels only). */
    onStartOver: ((EpgProgrammeEntity) -> Unit)? = null,
    /** glcn: record the channel from now. */
    onRecord: (() -> Unit)? = null,
) {
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

    var showTech by remember { mutableStateOf(false) }
    // glcn: the channel goes on in a small window up on the right (the shell moves the picture there).
    androidx.compose.runtime.DisposableEffect(Unit) {
        InfoStage.liveWindow = true
        onDispose { InfoStage.liveWindow = false }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(modifier.fillMaxSize()) {
        // glcn: the backdrop, with a window cut in it up on the right where the channel goes on playing.
        val windowRadius = 18.mpx
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val r = InfoStage.windowRect(size.width, size.height)
                    drawRoundRect(
                        Color.Black, topLeft = androidx.compose.ui.geometry.Offset(r.left, r.top), size = r.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(windowRadius.toPx()),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                    )
                },
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF06090A)))
            val art = film?.backdropPath?.let { tv.own.owntv.core.metadata.MetadataImages.backdrop(it, "w1280") }
                ?: lent?.backdropUrl ?: cur?.imageUrl?.takeIf { it.isNotBlank() }
            if (art != null) ProgrammeArt(url = art, modifier = Modifier.fillMaxSize())
            // The picture stays behind, never under the words: dark from the left and from the foot.
            // A deeper shade on the left, where the synopsis now runs long, so it reads over bright pictures too.
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF5050809), 0.62f to Color(0xD0050809), 1f to Color(0x4D050809))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xE6050809))))
        }
        // A fine frame round the window, and the word "Live" on it.
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 96.mpx, top = WINDOW_TOP).width(WINDOW_W).height(WINDOW_H)
                .staggerIn(300, fromRight = true)
                .border(1.5.mpx, Color.White.copy(alpha = 0.22f), RoundedCornerShape(windowRadius)),
        ) {
            Text(
                stringResource(R.string.glcn_pi_on_air).uppercase(), style = stageText(13, 800), color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(10.mpx)
                    .background(Color(0xCC0A1012), RoundedCornerShape(6.mpx)).padding(horizontal = 7.mpx, vertical = 3.mpx),
            )
        }

        Column(Modifier.padding(start = 96.mpx, top = 80.mpx, end = 96.mpx).width(1240.mpx)) {
            Row(Modifier.staggerIn(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                val logo = channel.displayLogoUrl?.takeIf { it.isNotBlank() }
                if (logo != null) ChannelLogoTile(logoUrl = logo, modifier = Modifier.size(132.mpx, 80.mpx), fill = Color.Transparent, surfaceLuminance = 0.02f) { }
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
                        modifier = Modifier.staggerIn(80).width(640.mpx).height(150.mpx), alignment = Alignment.CenterStart,
                        onFailed = { logoFailed = true }, onDarkScreen = true,
                    )
                } else {
                    Text(cur.title, style = stageText(58f, 800), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.staggerIn(80))
                }
                val remaining = ((cur.stopMs - nowMs) / 60_000L).toInt()
                Text(
                    if (remaining in 1..600) stringResource(R.string.glcn_pi_ends_left, timeFmt.format(java.util.Date(cur.stopMs)), remaining)
                    else stringResource(R.string.content_live_time_range, timeFmt.format(java.util.Date(cur.startMs)), timeFmt.format(java.util.Date(cur.stopMs))),
                    style = stageText(24, 600), color = Soft, modifier = Modifier.staggerIn(160).padding(top = 10.mpx), maxLines = 1,
                )
                val span = (cur.stopMs - cur.startMs).coerceAtLeast(1L)
                val f = ((nowMs - cur.startMs).toFloat() / span).coerceIn(0f, 1f)
                Box(Modifier.staggerIn(200).padding(top = 14.mpx).width(560.mpx).height(5.mpx).clip(RoundedCornerShape(3.mpx)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(f).height(5.mpx).background(accent))
                }
                val facts = (
                    (film?.genres?.take(3)?.takeIf { it.isNotEmpty() } ?: cur.details.categoryList.take(3)) + listOfNotNull(
                        (film?.year ?: cur.year ?: lent?.year)?.toString(),
                        (film?.rating?.takeIf { it > 0 } ?: lent?.rating)?.let { "★ " + ProgrammeArtResolver.format(it) },
                    )
                    ).filter { it.isNotBlank() }.joinToString("  ·  ")
                Row(Modifier.staggerIn(250).padding(top = 16.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    // glcn: the age rating as a badge of its own ("ES 7"), not lost in the line.
                    cur.rating?.takeIf { it.isNotBlank() }?.let { age ->
                        Text(
                            age, style = stageText(16, 800), color = Ink, maxLines = 1,
                            modifier = Modifier.border(1.5.mpx, Ink.copy(alpha = 0.6f), RoundedCornerShape(6.mpx)).padding(horizontal = 8.mpx, vertical = 2.mpx),
                        )
                    }
                    if (facts.isNotEmpty()) Text(facts, style = stageText(20, 600), color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // A film: TMDB's synopsis (in your language) before the guide's, which is often a list of credits.
                ((film?.overview?.takeIf { it.isNotBlank() }) ?: ownSynopsis ?: lent?.overview)?.let {
                    // glcn: as wide as the room up to the panels on the right, and a line more, so most synopses read whole
                    // without drifting.
                    // glcn: the cast sits on the right now, so the synopsis has the whole column down to the button.
                    DriftingSynopsis(it, modifier = Modifier.staggerIn(300).padding(top = 16.mpx).width(1120.mpx), style = stageText(21, 400), color = Soft, lines = SYNOPSIS_LINES, fade = true)
                }
            }
        }

        // Right: when it airs again this week, and under it a film's cast.
        if (cur != null) Column(
            Modifier.align(Alignment.TopEnd).padding(end = 96.mpx, top = WINDOW_TOP + WINDOW_H + 16.mpx).width(WINDOW_W),
            verticalArrangement = Arrangement.spacedBy(16.mpx),
        ) {
        // glcn: only when it does air again — "no repeats this week" was a panel saying nothing.
        if (again.isNotEmpty()) Column(
            Modifier.staggerIn(340, fromRight = true).fillMaxWidth()
                .clip(RoundedCornerShape(20.mpx)).background(Color(0x990A1012)).padding(24.mpx),
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
            Modifier.staggerIn(380, fromRight = true).fillMaxWidth()
                .clip(RoundedCornerShape(20.mpx)).background(Color(0x990A1012)).padding(24.mpx),
            verticalArrangement = Arrangement.spacedBy(12.mpx),
        ) {
            Text(stringResource(R.string.content_media_cast), style = stageText(18, 800), color = accent, maxLines = 1)
            val castRows = if (again.isNotEmpty()) 1 else 2
            cast.take(CAST_PER_ROW * castRows).chunked(CAST_PER_ROW).forEach { row ->
                tv.own.owntv.features.shell.components.VodCast(row, CAST_PER_ROW, gap = 12.mpx)
            }
        }
        }

        // glcn: what follows on the channel, the rest of the evening, under the repeats.
        if (cur != null && next.isNotEmpty() && !showTech) Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 96.mpx, bottom = 56.mpx).staggerIn(400, fromRight = true).width(420.mpx)
                .clip(RoundedCornerShape(20.mpx)).background(Color(0x990A1012)).padding(24.mpx),
            verticalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            Text(stringResource(R.string.glcn_pi_next), style = stageText(18, 800), color = accent, maxLines = 1)
            next.forEach { n ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    Text(timeFmt.format(java.util.Date(n.startMs)), style = stageText(19, 700), color = Ink, modifier = Modifier.width(84.mpx), maxLines = 1)
                    Text(n.title, style = stageText(18, 500), color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // The stream's technical data, kept off the player bar: one button here shows or hides them.
        Row(Modifier.align(Alignment.BottomStart).padding(start = 96.mpx, bottom = 56.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
            // glcn: what can be done with the programme on air — from its start (catch-up), record.
            if (cur != null && onStartOver != null && channel.catchup) StageTool(
                text = stringResource(R.string.glcn_pi_start_over), onClick = { onStartOver(cur) }, icon = OwnTVIcon.REWIND,
            )
            if (cur != null && onRecord != null) StageTool(
                text = stringResource(R.string.recording_record), onClick = { onRecord(); onDismiss() }, icon = OwnTVIcon.REC,
            )
            StageTool(
                text = stringResource(if (showTech) R.string.glcn_pi_hide_technical else R.string.glcn_pi_technical),
                onClick = { showTech = !showTech },
                icon = OwnTVIcon.INFO,
                modifier = Modifier.focusRequester(focus),
            )
        }
        if (showTech) StreamInfoOverlay(player, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 96.mpx, bottom = 56.mpx))
    }
}

/** glcn: the synopsis' lines, now that the cast is on the right. */
private const val SYNOPSIS_LINES = 10
/** glcn: the cast panel on the right: four a row, two rows unless the repeats above take more than this many lines. */
private const val CAST_PER_ROW = 4
/** glcn: the live window up on the right (the shell places the picture in the same box, see [InfoStage]). */
private val WINDOW_TOP = 150.mpx
private val WINDOW_W = 420.mpx
private val WINDOW_H = 236.mpx
/** glcn: repeats listed under the window, so the column never reaches "Up next". */
private const val REPEATS_SHOWN = 3

/** glcn: how many programmes "Up next" lists. */
private const val NEXT_MAX = 6

/**
 * glcn: the programme info screen's live window — while it is open, the shell draws the channel's picture in this box
 * (up on the right) instead of full screen, and the screen leaves a hole in its backdrop there.
 */
object InfoStage {
    var liveWindow by mutableStateOf(false)

    /** The window in a [w] × [h] screen, as the screen lays it out at 1920 × 1080 (end 96, top 150, 420 × 236). */
    fun windowRect(w: Float, h: Float): androidx.compose.ui.geometry.Rect {
        val sx = w / 1920f
        val sy = h / 1080f
        val left = (1920f - 96f - 420f) * sx
        val top = 150f * sy
        return androidx.compose.ui.geometry.Rect(left, top, left + 420f * sx, top + 236f * sy)
    }
}
