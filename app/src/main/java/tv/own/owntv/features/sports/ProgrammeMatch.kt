package tv.own.owntv.features.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.CompetitionNames
import tv.own.owntv.core.sports.SportsRepository
import tv.own.owntv.core.sports.TeamNames
import tv.own.owntv.core.sports.WatchMatcher
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.isRace
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the match a guide programme is showing ("Chequia – España"), found among the sports board's matches the way
 * the Sports screen finds channels for a match (both sides, or the competition, named in the title). Null when the
 * board has none that the title confirms, or for a race.
 */
@Composable
fun rememberProgrammeMatch(title: String?, startMs: Long, stopMs: Long): Match? {
    val repo: SportsRepository = org.koin.compose.koinInject()
    val board by repo.board.collectAsState()
    return remember(board, title, startMs) {
        if (title.isNullOrBlank()) return@remember null
        val tokens = TeamNames.tokens(title).toSet()
        val words = CompetitionNames.words(title)
        (board.today.values.flatten() + board.tomorrow.values.flatten())
            .asSequence()
            .filter { !it.sport.isRace && it.competitors.size >= 2 }
            .filter { it.startMs in (startMs - MATCH_EARLY_MS)..stopMs }
            .filter { WatchMatcher.hit(WatchMatcher.Keys(it), tokens, words) == WatchMatcher.Hit.CONFIRMED }
            .minByOrNull { kotlin.math.abs(it.startMs - startMs) }
    }
}

/**
 * glcn: the match, told big on the programme's page: both crests with their names, the score between them and, while
 * it is on, the minute. Spoiler-free hides the score as the Sports screens do.
 */
@Composable
fun ProgrammeMatchStrip(m: Match, modifier: Modifier = Modifier) {
    val settings: tv.own.owntv.core.settings.SettingsRepository = org.koin.compose.koinInject()
    val config by settings.activeSportsConfig.collectAsState(null)
    val spoiler = config?.spoilerFree == true
    val home = m.home ?: return
    val away = m.away ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.mpx)) {
        if (m.isLive) LiveMark(stringResource(R.string.player_live) + stringResource(R.string.content_epg_bits_separator) + clockText(m), 17f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
            Crest(home.team, CREST)
            Text(teamLabel(home.team), style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = NAME_MAX))
            val score = when {
                m.state == MatchState.PRE -> null
                spoiler -> "– : –"
                else -> "${scoreText(m.sport, home.score)} – ${scoreText(m.sport, away.score)}"
            }
            score?.let { Text(it, style = stageText(40, 800), color = StageColors.Text, maxLines = 1, modifier = Modifier.padding(horizontal = 6.mpx)) }
            Text(teamLabel(away.team), style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = NAME_MAX))
            Crest(away.team, CREST)
        }
    }
}

private const val MATCH_EARLY_MS = 3 * 60 * 60_000L
private const val CREST = 64
private val NAME_MAX = 300.mpx
