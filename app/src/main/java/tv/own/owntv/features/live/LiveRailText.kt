package tv.own.owntv.features.live

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.live.LiveKey

/** Built-in rail labels are presentation text; category names remain provider/profile data. */
@Composable
fun LiveRailItem.displayLabel(@StringRes allLabelRes: Int = R.string.content_category_all_channels): String = title ?: when (key) {
    LiveKey.Favorites -> stringResource(R.string.content_category_favorites)
    LiveKey.History -> stringResource(R.string.content_category_history)
    LiveKey.Catchup -> stringResource(R.string.content_catchup)
    LiveKey.All -> stringResource(allLabelRes)
    LiveKey.Continue -> stringResource(R.string.home_row_keep_watching)
    is LiveKey.Folder -> stringResource(allLabelRes)
    is LiveKey.Custom -> stringResource(allLabelRes)
}
