package tv.own.owntv.glcn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
    val cur = rows.firstOrNull { nowMs in it.startMs until it.stopMs }
    val next = if (cur == null) emptyList() else rows.filter { it.startMs >= cur.stopMs }.take(2)

    // The week, read after the screen is up; the repeats are the same title again later.
    val week by produceState<List<EpgProgrammeEntity>?>(null, channel.id) {
        value = runCatching { loadWeek(channel) }.getOrDefault(emptyList())
    }
    val again = remember(cur?.title, week) {
        val t = cur?.title?.trim()?.lowercase()
        if (cur == null || t == null) emptyList()
        else week.orEmpty().filter { it.startMs >= cur.stopMs && it.title.trim().lowercase() == t }.take(7)
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
    var logoFailed by remember(titleLogo) { mutableStateOf(false) }

    var showTech by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(modifier.fillMaxSize().background(Color(0xFF06090A))) {
        val art = lent?.backdropUrl ?: cur?.imageUrl?.takeIf { it.isNotBlank() }
        if (art != null) ProgrammeArt(url = art, modifier = Modifier.fillMaxSize())
        // The picture stays behind, never under the words: dark from the left and from the foot.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF2050809), 0.55f to Color(0xB3050809), 1f to Color(0x4D050809))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xE6050809))))

        Column(Modifier.padding(start = 96.mpx, top = 80.mpx, end = 96.mpx).width(1020.mpx)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                val logo = channel.displayLogoUrl?.takeIf { it.isNotBlank() }
                if (logo != null) ChannelLogoTile(logoUrl = logo, modifier = Modifier.size(132.mpx, 80.mpx), fill = Color.Transparent) { }
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
                if (!titleLogo.isNullOrBlank() && !logoFailed) {
                    tv.own.owntv.ui.stage.StageTitleArt(
                        url = titleLogo!!, contentDescription = cur.title,
                        modifier = Modifier.width(640.mpx).height(150.mpx), alignment = Alignment.CenterStart,
                        onFailed = { logoFailed = true },
                    )
                } else {
                    Text(cur.title, style = stageText(58f, 800), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val remaining = ((cur.stopMs - nowMs) / 60_000L).toInt()
                Text(
                    if (remaining in 1..600) stringResource(R.string.content_live_time_remaining, timeFmt.format(java.util.Date(cur.stopMs)), remaining)
                    else stringResource(R.string.content_live_time_range, timeFmt.format(java.util.Date(cur.startMs)), timeFmt.format(java.util.Date(cur.stopMs))),
                    style = stageText(24, 600), color = Soft, modifier = Modifier.padding(top = 10.mpx), maxLines = 1,
                )
                val span = (cur.stopMs - cur.startMs).coerceAtLeast(1L)
                val f = ((nowMs - cur.startMs).toFloat() / span).coerceIn(0f, 1f)
                Box(Modifier.padding(top = 14.mpx).width(560.mpx).height(5.mpx).clip(RoundedCornerShape(3.mpx)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(f).height(5.mpx).background(accent))
                }
                val facts = (
                    cur.details.categoryList.take(3) + listOfNotNull(
                        (cur.year ?: lent?.year)?.toString(),
                        cur.rating,
                        lent?.rating?.let { "★ " + ProgrammeArtResolver.format(it) },
                    )
                    ).filter { it.isNotBlank() }.joinToString("  ·  ")
                if (facts.isNotEmpty()) Text(facts, style = stageText(20, 600), color = Dim, modifier = Modifier.padding(top = 16.mpx), maxLines = 1, overflow = TextOverflow.Ellipsis)
                (cur.description?.takeIf { it.isNotBlank() } ?: lent?.overview)?.let {
                    DriftingSynopsis(it, modifier = Modifier.padding(top = 16.mpx).width(960.mpx), style = stageText(21, 400), color = Soft, lines = 6, fade = true)
                }
                if (next.isNotEmpty()) {
                    Text(stringResource(R.string.glcn_pi_next), style = stageText(16, 800), color = accent, modifier = Modifier.padding(top = 22.mpx))
                    next.forEach { n ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.mpx), modifier = Modifier.padding(top = 4.mpx)) {
                            Text(timeFmt.format(java.util.Date(n.startMs)), style = stageText(19, 700), color = Dim, modifier = Modifier.width(84.mpx), maxLines = 1)
                            Text(n.title, style = stageText(19, 500), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Right: when it airs again this week.
        if (cur != null) Column(
            Modifier.align(Alignment.TopEnd).padding(end = 96.mpx, top = 150.mpx).width(420.mpx)
                .clip(RoundedCornerShape(20.mpx)).background(Color(0x990A1012)).padding(24.mpx),
            verticalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            Text(stringResource(R.string.glcn_pi_airs_again), style = stageText(18, 800), color = accent, maxLines = 2)
            when {
                week == null -> Unit
                again.isEmpty() -> Text(stringResource(R.string.glcn_pi_no_repeats), style = stageText(17, 500), color = Dim)
                else -> again.forEach { a ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                        Text(dayFmt.format(java.util.Date(a.startMs)), style = stageText(19, 700), color = Ink, modifier = Modifier.width(110.mpx), maxLines = 1)
                        Text(timeFmt.format(java.util.Date(a.startMs)), style = stageText(19, 500), color = Soft, maxLines = 1)
                    }
                }
            }
        }

        // The stream's technical data, kept off the player bar: one button here shows or hides them.
        Row(Modifier.align(Alignment.BottomStart).padding(start = 96.mpx, bottom = 56.mpx)) {
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
