package tv.own.owntv.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import tv.own.owntv.ui.stage.stageGlass
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.MatchAlert
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * The line under the channel name while the info bar is open: "● LIVE **Frankfurt 2–1 Köln** · 63' · ESPN".
 * It asks for the score only while it is drawn, so a closed bar costs no request. Spoiler-free shows
 * the two teams without the score.
 */
@Composable
fun MatchScoreLine(playback: SportsPlayback, match: Match, spoilerFree: Boolean) {
    val m by remember(match.key) { playback.score(match) }.collectAsStateWithLifecycle(match)
    val context = LocalContext.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    Row(
        Modifier.padding(top = 6.mpx),
        horizontalArrangement = Arrangement.spacedBy(10.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (m.isLive) LiveMark(stringResource(R.string.player_live), 15f)
        // Before kick-off: no score yet, and the day and time in the user's own words, not ESPN's US text.
        val pre = m.state == tv.own.owntv.core.sports.model.MatchState.PRE
        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        val today = stringResource(R.string.content_epg_today)
        Text(
            if (spoilerFree || pre) MatchNotifications.pair(context, m) else MatchNotifications.scoreLine(context, m),
            style = stageText(19, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val status = when {
            m.isLive -> clockText(m)
            pre -> (dayOf(m.startMs, locale) ?: today) + sep + timeText(m.startMs)
            else -> m.statusText
        }
        Text(
            listOfNotNull(status, ESPN).joinToString(sep, prefix = sep),
            style = stageText(19, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The score provider's name as the credit shows it; a brand, not translated. */
private const val ESPN = "ESPN"

/**
 * The other channels showing the match, as a side panel over the picture — the backup when a stream stalls.
 * glcn: a sheet of the stage glass that slides in from the right (see-through, readable in light and dark), with the
 * match at its head — both crests, the teams, the competition — then the channels; the playing one is marked.
 * OK switches, Back closes.
 */
@Composable
fun OtherChannelsPanel(session: SportsSession, playingId: Long?, onSwitch: (SportsSession, Int) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    val enterSpec = tv.own.owntv.ui.theme.ownTvTween<Float>(300)
    LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }
    val shade = tv.own.owntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    val m = session.match
    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to shade.copy(alpha = 0.7f))),
        ) {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .padding(end = 48.mpx, top = 40.mpx, bottom = 40.mpx)
                    .width(620.mpx).fillMaxHeight()
                    .graphicsLayer { translationX = (1f - enter.value) * 80.mpx.toPx() },
            ) {
                Box(Modifier.matchParentSize().graphicsLayer { alpha = 0.88f }.stageGlass(tv.own.owntv.ui.theme.StageRadii.Sheet, overContent = true))
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 26.mpx, vertical = 26.mpx).trapAllFocusExit().focusGroup(),
                ) {
                    Text(
                        stringResource(R.string.sports_other_channels_title).uppercase(locale),
                        style = stageText(15, 700, 0.12.em), color = tv.own.owntv.ui.theme.stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    // The match: both crests and the teams, the competition under them.
                    Row(
                        Modifier.padding(top = 14.mpx).fillMaxWidth()
                            .background(StageColors.Ink.copy(alpha = 0.05f), RoundedCornerShape(16.mpx))
                            .padding(horizontal = 16.mpx, vertical = 14.mpx),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.mpx),
                    ) {
                        if (m.home != null && m.away != null) {
                            Crest(m.home!!.team, 52)
                            Crest(m.away!!.team, 52)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(MatchNotifications.pair(context, m), style = stageText(23, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(m.leagueName, style = stageText(15, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val langs = session.options.mapNotNull { it.facets.language }.distinct().take(3).map { languageName(it) }
                    if (langs.isNotEmpty()) {
                        Row(Modifier.padding(top = 12.mpx)) { StageTag(langs.joinToString(", ")) }
                    }
                    LazyColumn(Modifier.weight(1f).padding(top = 12.mpx)) {
                        items(session.options.size) { i ->
                            val o = session.options.getOrNull(i) ?: return@items
                            val playing = o.channel.id == playingId
                            ChannelRow(
                                o, m,
                                onClick = { if (!playing) onSwitch(session, i) else onDismiss() },
                                onHold = null,
                                playing = playing,
                                modifier = Modifier
                                    .then(if (i == (session.options.indexOfFirst { it.channel.id != playingId }.takeIf { it >= 0 } ?: 0)) Modifier.focusRequester(first) else Modifier)
                                    .then(if (playing) Modifier.alpha(0.7f) else Modifier),
                            )
                        }
                    }
                    StageKeyHints(
                        listOf(
                            stringResource(R.string.common_ok) to stringResource(R.string.sports_switch_channel),
                            stringResource(R.string.common_back) to stringResource(R.string.content_close),
                        ),
                        Modifier.padding(top = 14.mpx),
                        textSize = 16,
                    )
                }
            }
        }
    }
}

/**
 * The goal toast: "⚽ Goal for a team you follow — Liverpool 1–0 Brentford · Salah 38'", top right over the picture,
 * for eight seconds. Never for the match being watched. OK switches to that match (the player handles the key).
 */
@Composable
fun GoalToast(alert: MatchAlert, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    val m = alert.match
    val who = listOfNotNull(alert.player, m.clock?.display?.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { null }
    Row(
        modifier
            .width(525.mpx)
            .background(Color(0xD906090B), RoundedCornerShape(21.mpx))
            .padding(horizontal = 24.mpx, vertical = 18.mpx),
        horizontalArrangement = Arrangement.spacedBy(18.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(GOAL_MARK, style = stageText(33, 400), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sports_goal_toast), style = stageText(20, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(MatchNotifications.scoreLine(context, m), who).joinToString(sep),
                style = stageText(18, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        StageTag(stringResource(R.string.common_ok) + sep + stringResource(R.string.sports_switch))
    }
}

private const val GOAL_MARK = "⚽"
