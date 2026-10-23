package tv.own.owntv.features.sports

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.features.home.tracked
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * Home's "Live sports" row: the hub's match cards for followed matches that are
 * live or kick off within the hour. OK (and the Play key) opens the match page, where its channels are found.
 */
@Composable
fun LiveSportsRow(
    cards: List<Match>,
    config: SportsConfig,
    start: Dp,
    onOpenMatch: (Match) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = System.currentTimeMillis()
    Column(modifier) {
        Row(
            Modifier.padding(start = start, bottom = 10.mpx),
            horizontalArrangement = Arrangement.spacedBy(15.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.sports_title), style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // glcn: the Home row rule — back on the first card it rests at the start, not flush against the edge.
        tv.own.owntv.features.home.HomeLazyRow(
            modifier = Modifier.focusRestorer().focusGroup(),
            contentPadding = PaddingValues(start = start, end = 64.mpx, top = 6.mpx, bottom = 6.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
        ) {
            itemsIndexed(cards, key = { _, m -> m.key }) { i, m ->
                MatchCard(
                    m, featured = false, config = config, now = now, reminded = false,
                    onClick = { onOpenMatch(m) },
                    onPlay = { onOpenMatch(m) },
                    onHold = { onOpenMatch(m) },
                    modifier = Modifier
                        .then(if (i == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                )
            }
        }
    }
}
