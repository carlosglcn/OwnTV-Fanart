package tv.own.owntv.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.RaceSession
import tv.own.owntv.core.sports.model.Ranking
import tv.own.owntv.core.sports.model.RankingGroup
import tv.own.owntv.core.sports.model.RankingKind
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** glcn: a racing session's name in the user's language ("Libres 1", "Clasificación", "Carrera"). */
@Composable
internal fun raceSessionLabel(m: Match): String? = RaceSession.of(m.subtitle)?.let {
    stringResource(
        when (it) {
            RaceSession.PRACTICE_1 -> R.string.glcn_race_practice_1
            RaceSession.PRACTICE_2 -> R.string.glcn_race_practice_2
            RaceSession.PRACTICE_3 -> R.string.glcn_race_practice_3
            RaceSession.PRACTICE -> R.string.glcn_race_practice
            RaceSession.WARM_UP -> R.string.glcn_race_warm_up
            RaceSession.SPRINT_QUALIFYING -> R.string.glcn_race_sprint_qualifying
            RaceSession.SPRINT -> R.string.glcn_race_sprint
            RaceSession.QUALIFYING -> R.string.glcn_race_qualifying
            RaceSession.RACE -> R.string.glcn_race_race
        },
    )
}

/**
 * glcn: the inside of the score header for a racing session. A session has no home and away, so it names
 * the session and the Grand Prix, then the time and circuit, and lists the first three below.
 */
@Composable
internal fun RaceHeaderContent(m: Match, hidden: Boolean) {
    val sep = stringResource(R.string.content_epg_bits_separator)
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(raceSessionLabel(m) ?: m.subtitle.orEmpty(), style = stageText(51f, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(m.title, style = stageText(24f, 600), color = StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
        Spacer(Modifier.height(14.mpx))
        if (m.isLive) LiveMark(stringResource(R.string.player_live) + sep + clockText(m))
        else Text(
            if (m.state == MatchState.PRE) listOfNotNull(dayOf(m.startMs, locale) ?: stringResource(R.string.content_epg_today), timeText(m.startMs)).joinToString(sep) else m.statusText,
            style = stageText(24f, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        m.venue?.let { Text(it, style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx)) }
        val grid = m.state == MatchState.PRE
        if (m.competitors.isNotEmpty() && (!grid || RaceSession.of(m.subtitle) == RaceSession.RACE) && !(hidden && !grid)) {
            if (grid) Text(stringResource(R.string.glcn_race_grid), style = stageText(16, 700), color = StageColors.Dim, modifier = Modifier.padding(top = 18.mpx, bottom = 4.mpx))
            else Spacer(Modifier.height(18.mpx))
            m.competitors.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth(0.7f).padding(vertical = 4.mpx), horizontalArrangement = Arrangement.spacedBy(16.mpx), verticalAlignment = Alignment.CenterVertically) {
                    Text((i + 1).toString(), style = stageText(20, 800), color = StageColors.Muted, modifier = Modifier.width(30.mpx))
                    Crest(c.team, 39)
                    Text(c.team.name, style = stageText(21, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    c.team.country?.let { Text(it, style = stageText(16, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun rankLabel(kind: RankingKind): String = stringResource(
    when (kind) {
        RankingKind.DRIVERS -> R.string.glcn_rank_drivers
        RankingKind.CONSTRUCTORS -> R.string.glcn_rank_constructors
        RankingKind.PLAYERS -> R.string.glcn_rank_players
    },
)

/** glcn: a racing series' (or tennis tour's) ranking: drivers and constructors side by side, the first ten of each. */
@Composable
internal fun RankingColumns(ranking: Ranking, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 9.mpx), horizontalArrangement = Arrangement.spacedBy(30.mpx)) {
        ranking.groups.forEach { g -> RankingColumn(g, Modifier.weight(1f)) }
    }
}

@Composable
private fun RankingColumn(g: RankingGroup, modifier: Modifier) {
    Column(modifier) {
        Text(rankLabel(g.kind), style = stageText(19, 800), color = StageColors.Muted, modifier = Modifier.padding(bottom = 8.mpx))
        g.rows.take(10).forEach { r ->
            // Focusable so Down scrolls the page and the last rows come into view (plain text never took focus).
            var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().onFocusChanged { on = it.isFocused }.focusable()
                .background(if (on) StageColors.Ink.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(8.mpx))
                .padding(vertical = 5.mpx, horizontal = 6.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                Text(r.rank.toString(), style = stageText(19, 700), color = StageColors.Muted, modifier = Modifier.width(33.mpx))
                Text(if (r.team.isAthlete) r.team.name else r.team.shortName, style = stageText(20, 600), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(r.points.toString(), style = stageText(20, 800), color = StageColors.Text, textAlign = TextAlign.End)
            }
        }
    }
}
