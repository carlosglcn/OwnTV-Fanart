package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * Settings › Layout › Browsing lists as a Stage page (six switches are not a popup): what
 * each section remembers — its last category, then its last item — one switch per section.
 */
@Composable
fun BrowsingListsSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val catLive by vm.rememberCategoryLive.collectAsStateWithLifecycle()
    val catMovies by vm.rememberCategoryMovies.collectAsStateWithLifecycle()
    val catSeries by vm.rememberCategorySeries.collectAsStateWithLifecycle()
    val itemLive by vm.rememberLastLive.collectAsStateWithLifecycle()
    val itemMovies by vm.rememberLastMovies.collectAsStateWithLifecycle()
    val itemSeries by vm.rememberLastSeries.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_layout)),
        title = stringResource(R.string.settings_browsing_lists),
        count = pluralStringResource(R.plurals.settings_setting_count, 7, 7),
        onBack = onBack,
        modifier = modifier,
    ) {
        val live = stringResource(R.string.settings_history_live)
        val movies = stringResource(R.string.settings_history_movies)
        val series = stringResource(R.string.settings_history_series)
        val cat = stringResource(R.string.settings_browsing_last_category)
        val catHelp = stringResource(R.string.settings_browsing_last_category_description)
        StageSettingsHeading(cat, 3, first = true)
        BrowsingSwitch(OwnTVIcon.LIVE_TV, live, cat, catHelp, catLive, Modifier.focusRequester(first)) { vm.setRememberCategoryLive(!catLive) }
        BrowsingSwitch(OwnTVIcon.MOVIES, movies, cat, catHelp, catMovies) { vm.setRememberCategoryMovies(!catMovies) }
        BrowsingSwitch(OwnTVIcon.SERIES, series, cat, catHelp, catSeries) { vm.setRememberCategorySeries(!catSeries) }
        val item = stringResource(R.string.settings_browsing_last_item)
        val itemHelp = stringResource(R.string.settings_browsing_last_item_description)
        StageSettingsHeading(item, 3)
        BrowsingSwitch(OwnTVIcon.LIVE_TV, live, item, itemHelp, itemLive) { vm.setRememberLastLive(!itemLive) }
        BrowsingSwitch(OwnTVIcon.MOVIES, movies, item, itemHelp, itemMovies) { vm.setRememberLastMovies(!itemMovies) }
        BrowsingSwitch(OwnTVIcon.SERIES, series, item, itemHelp, itemSeries) { vm.setRememberLastSeries(!itemSeries) }
        // glcn: choosing a film opens its page first (trailer, audio, subtitles) — or plays at once.
        val pageTitle = stringResource(R.string.glcn_title_page)
        val pageCtx = androidx.compose.ui.platform.LocalContext.current
        StageSettingsHeading(pageTitle, 1)
        BrowsingSwitch(OwnTVIcon.INFO, pageTitle, pageTitle, stringResource(R.string.glcn_title_page_description), tv.own.owntv.glcn.DockPrefs.titlePage) {
            tv.own.owntv.glcn.DockPrefs.setTitlePage(pageCtx, !tv.own.owntv.glcn.DockPrefs.titlePage)
        }
    }
}

@Composable
private fun BrowsingSwitch(icon: OwnTVIcon, title: String, part: String, help: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val value = SettingValue.Switch(on)
    StageSettingRow(
        icon = icon,
        title = title,
        desc = null,
        value = value,
        onClick = onClick,
        // The panel names the part too: "Remember last category · Live TV".
        help = settingHelp(null, stringResource(R.string.settings_breadcrumb_eyebrow, part, title), help, value, pinnable = false),
        modifier = modifier,
    )
}
