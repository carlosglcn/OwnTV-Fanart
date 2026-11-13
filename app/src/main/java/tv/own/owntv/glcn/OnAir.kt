package tv.own.owntv.glcn

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R

/**
 * glcn: the word on a programme's picture — "Live" only for a broadcast that really is live (a match, a "DIRECTO"
 * programme: the same rule as the Live TV stage), "On air" for anything else being shown now.
 */
@Composable
fun onAirLabel(title: String?): String =
    if (title != null && tv.own.owntv.core.live.SportsNow.displayTitle(title) != title) stringResource(R.string.player_live)
    else stringResource(R.string.glcn_on_air_badge)
