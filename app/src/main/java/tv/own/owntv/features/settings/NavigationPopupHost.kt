package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Settings › Layout › Navigation, hosted outside [SettingsScreen] (whose body is at the JVM method-size limit). */
@Composable
internal fun NavigationPopupHost(settingsVm: SettingsViewModel, onDismiss: () -> Unit) {
    val navStyle by settingsVm.navStyle.collectAsStateWithLifecycle()
    val navSize by settingsVm.navSize.collectAsStateWithLifecycle()
    val navLength by settingsVm.navLength.collectAsStateWithLifecycle()
    val navWiden by settingsVm.navWiden.collectAsStateWithLifecycle()
    val navHideAfterMs by settingsVm.navHideAfterMs.collectAsStateWithLifecycle()
    val navMenuMode by settingsVm.navMenuMode.collectAsStateWithLifecycle()
    val navMenuHidden by settingsVm.navMenuHidden.collectAsStateWithLifecycle()
    val topSize by settingsVm.topSize.collectAsStateWithLifecycle()
    val topFloating by settingsVm.topFloating.collectAsStateWithLifecycle()
    val onTop = navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.TOP
    NavigationSettingsPopup(
        style = navStyle,
        onStyle = settingsVm::setNavStyle,
        // glcn: the top bar keeps its own size, so changing the rail's does not shrink it.
        size = if (onTop) topSize else navSize,
        onSize = if (onTop) settingsVm::setTopSize else settingsVm::setNavSize,
        topFloating = topFloating,
        onTopFloating = settingsVm::setTopFloating,
        length = navLength,
        onLength = settingsVm::setNavLength,
        widen = navWiden,
        onWiden = settingsVm::setNavWiden,
        hideAfterMs = navHideAfterMs,
        onHideAfterMs = settingsVm::setNavHideAfterMs,
        menuMode = navMenuMode,
        onMenuMode = settingsVm::setNavMenuMode,
        hiddenSections = navMenuHidden,
        onSectionHidden = settingsVm::setNavSectionHidden,
        onDismiss = onDismiss,
    )
}
