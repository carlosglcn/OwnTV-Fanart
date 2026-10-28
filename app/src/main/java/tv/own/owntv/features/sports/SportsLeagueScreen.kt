package tv.own.owntv.features.sports

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageTab
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/** The league page's tabs. Top scorers is not offered: the feeds carry none. */
private enum class LeagueTab { TABLE, FIXTURES, RESULTS }

/**
 * A league: its name and Following, the tabs, the table on the left and this matchday's
 * matches on the right ("Frankfurt 2–1 Köln · 63'"). OK on a match opens its page, where its channels are found.
 */
@Composable
fun SportsLeagueScreen(vm: SportsViewModel, onOpenMatch: (Match) -> Unit, onBack: () -> Unit, contentStart: Dp, modifier: Modifier = Modifier, active: Boolean = true) {
    androidx.activity.compose.BackHandler(enabled = active) { onBack() }
    val page by vm.leaguePage.collectAsStateWithLifecycle()
    val state = page ?: return
    val league = SportsCatalog.league(state.leagueKey)
    var tab by remember { mutableStateOf(LeagueTab.TABLE) }
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { tabFocus.requestFocus() } }
    val sep = stringResource(R.string.content_epg_bits_separator)
    val myTeams = state.config.teams.map { it.id }.toSet()

    Column(modifier.fillMaxSize().padding(start = contentStart, end = 60.mpx, top = 39.mpx)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(21.mpx)) {
            Column {
                Text(league?.name ?: state.leagueKey, style = stageText(48, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(0.mpx)) {
                    Text(stringResource(R.string.sports_title), style = stageText(21, 600), color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    league?.sport?.let { Text(sep + sportLabel(it), style = stageText(21, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            val following = vm.follows(state.config, state.leagueKey)
            SportsChip(stringResource(if (following) R.string.sports_following else R.string.sports_follow), on = following, icon = OwnTVIcon.STAR) {
                vm.toggleLeague(state.leagueKey)
            }
        }
        Row(Modifier.padding(top = 24.mpx).focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
            LeagueTab.entries.forEach { t ->
                StageTab(
                    label = stringResource(
                        when (t) {
                            LeagueTab.TABLE -> R.string.sports_tab_table
                            LeagueTab.FIXTURES -> R.string.sports_tab_fixtures
                            LeagueTab.RESULTS -> R.string.sports_tab_results
                        },
                    ),
                    count = "", selected = tab == t, onClick = { tab = t },
                    modifier = if (t == LeagueTab.TABLE) Modifier.focusRequester(tabFocus) else Modifier,
                )
            }
        }
        Row(Modifier.fillMaxSize().padding(top = 21.mpx), horizontalArrangement = Arrangement.spacedBy(42.mpx)) {
            val matches = when (tab) {
                LeagueTab.TABLE -> state.matches
                LeagueTab.FIXTURES -> state.matches.filter { it.state == MatchState.PRE }
                LeagueTab.RESULTS -> state.matches.filter { it.state == MatchState.POST }
            }
            val players = state.ranking?.groups?.singleOrNull()?.takeIf { it.kind == tv.own.owntv.core.sports.model.RankingKind.PLAYERS }
            if (tab == LeagueTab.TABLE && players != null && state.table == null) {
                // glcn: a tennis tour — the whole ranking, a row each, with photos (lazily: 150 rows).
                LazyColumn(Modifier.width(960.mpx).fillMaxSize(), contentPadding = PaddingValues(top = 9.mpx, bottom = 60.mpx)) {
                    items(players.rows, key = { it.team.id }) { r -> PlayerRankLine(r) }
                }
            } else if (tab == LeagueTab.TABLE && state.ranking != null && state.table == null) {
                LazyColumn(Modifier.width(960.mpx).fillMaxSize(), contentPadding = PaddingValues(bottom = 60.mpx)) {
                    item { RankingColumns(state.ranking) }
                }
            } else if (tab == LeagueTab.TABLE) {
                LazyColumn(Modifier.width(960.mpx).fillMaxSize(), contentPadding = PaddingValues(bottom = 60.mpx)) {
                    item { StandingsHeader() }
                    state.table?.groups?.forEach { g ->
                        if ((state.table.groups.size) > 1) item { Text(g.name, style = stageText(19, 800), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.mpx)) }
                        items(g.rows) { r -> StandingsLine(r, highlight = r.team.id in myTeams) }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sports_this_matchday), style = stageText(25.5f, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 18.mpx, bottom = 9.mpx))
                LazyColumn(Modifier.fillMaxSize().focusGroup(), contentPadding = PaddingValues(bottom = 60.mpx)) {
                    items(matches, key = { it.key }) { m -> LeagueMatchRow(m, state.config.spoilerFree) { onOpenMatch(m) } }
                }
            }
        }
    }
}

/** `.lrow` of "This matchday": LIVE or the day, "Frankfurt 2–1 Köln", then the minute or kick-off. */
@Composable
private fun LeagueMatchRow(m: Match, spoilerFree: Boolean, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    StageRow(onClick = onClick, height = 84.mpx, modifier = Modifier.fillMaxWidth(), horizontalPadding = 18.mpx) { _ ->
        Box(Modifier.width(72.mpx)) {
            if (m.isLive) LiveMark("") else Text(dayOf(m.startMs, locale) ?: stringResource(R.string.content_epg_today), style = stageText(19, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f)) {
            val title = if (m.state == MatchState.PRE || spoilerFree) MatchNotifications.pair(context, m) else MatchNotifications.scoreLine(context, m)
            Text(title, style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = if (m.isLive) clockText(m) else if (m.state == MatchState.PRE) timeText(m.startMs) else m.statusText
            Text(time, style = stageText(18, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
