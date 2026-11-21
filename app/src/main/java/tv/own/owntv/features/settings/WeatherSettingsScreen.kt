package tv.own.owntv.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.settings.ClockColors
import tv.own.owntv.core.settings.ClockPart
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.features.shell.components.LocalClockWeather
import tv.own.owntv.features.shell.components.StageClock
import tv.own.owntv.features.shell.components.clockColor
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.labelRes
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.primary
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * Settings › Appearance › Date, time & weather: the top bar's weather (show, location, °C/°F)
 * and the colours of the clock's three parts, each picked in the Accent colour picker with a live preview
 * of the clock in the panel.
 */
@Composable
fun WeatherSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val enabled by vm.weatherEnabled.collectAsStateWithLifecycle()
    val location by vm.weatherLocation.collectAsStateWithLifecycle()
    val fahrenheit by vm.weatherFahrenheit.collectAsStateWithLifecycle()
    val colors by vm.clockColors.collectAsStateWithLifecycle()

    var showLocation by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<ClockPart?>(null) }
    val rowsFocus = remember { FocusRequester() }
    val locationFocus = remember { FocusRequester() }
    val colorFocus = remember { ClockPart.entries.associateWith { FocusRequester() } }
    var returnTo by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(Unit) { runCatching { rowsFocus.requestFocus() } }
    // A closed dialog hands focus back to the row that opened it.
    LaunchedEffect(showLocation, picking) {
        if (!showLocation && picking == null) {
            returnTo?.let { kotlinx.coroutines.delay(80); runCatching { it.requestFocus() } }
            returnTo = null
        }
    }

    val back = stringResource(R.string.common_back)
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_appearance)),
        title = stringResource(R.string.settings_title_date_time_weather),
        count = pluralStringResource(R.plurals.settings_setting_count, 6, 6),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
    ) {
        val showTitle = stringResource(R.string.settings_show_weather)
        val showValue = SettingValue.Switch(enabled)
        StageSettingRow(
            icon = OwnTVIcon.SUN,
            title = showTitle,
            desc = stringResource(R.string.settings_line_show_weather),
            value = showValue,
            onClick = { vm.setWeatherEnabled(!enabled) },
            help = settingHelp(null, showTitle, stringResource(R.string.settings_show_weather_description), showValue, pinnable = false),
        )
        val locationTitle = stringResource(R.string.settings_custom_location)
        val locationValue = SettingValue.Opens(location.ifBlank { stringResource(R.string.settings_auto) })
        StageSettingRow(
            icon = OwnTVIcon.LANGUAGE,
            title = locationTitle,
            desc = stringResource(R.string.settings_line_custom_location),
            value = locationValue,
            onClick = { returnTo = locationFocus; showLocation = true },
            modifier = Modifier.focusRequester(locationFocus),
            help = settingHelp(null, locationTitle, stringResource(R.string.settings_custom_location_description), locationValue, pinnable = false),
        )
        val unitTitle = stringResource(R.string.settings_temperature_unit)
        val unitValue = SettingValue.Segmented(
            listOf(stringResource(R.string.settings_degree_celsius), stringResource(R.string.settings_degree_fahrenheit)),
            if (fahrenheit) 1 else 0,
        )
        StageSettingRow(
            icon = OwnTVIcon.SUN,
            title = unitTitle,
            desc = stringResource(R.string.settings_line_temperature_unit),
            value = unitValue,
            onClick = { vm.setWeatherFahrenheit(!fahrenheit) },
            onStep = { step -> vm.setWeatherFahrenheit(step > 0) },
            help = settingHelp(null, unitTitle, stringResource(R.string.settings_temperature_description), unitValue, choices = unitValue.options, pinnable = false),
        )

        StageSettingsHeading(stringResource(R.string.settings_clock_colors), ClockPart.entries.size)
        val panelText = stringResource(R.string.settings_clock_color_help)
        val previewWord = stringResource(R.string.settings_glass_live_preview)
        val separator = dotSeparator()
        val hints = listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.settings_key_pick_color),
            back to stringResource(R.string.settings_group_appearance),
        )
        ClockPart.entries.forEach { part ->
            val title = stringResource(part.titleRes)
            val stored = colors.of(part)
            val swatch = clockColor(stored, part.default)
            val label = clockColorLabel(part, stored)
            StageSettingRow(
                icon = part.icon,
                title = title,
                desc = stringResource(part.lineRes),
                value = SettingValue.Custom {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.mpx).background(swatch, RoundedCornerShape(9.mpx)))
                        Text(label, style = stageText(18, 700), color = stageAccent.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        OwnTVIcon(OwnTVIcon.CHEVRON, StageColors.Muted, Modifier.size(20.mpx))
                    }
                },
                onClick = { returnTo = colorFocus.getValue(part); picking = part },
                modifier = Modifier.focusRequester(colorFocus.getValue(part)),
                help = SettingHelp(
                    title = title + separator + previewWord,
                    text = panelText,
                    hints = hints,
                    extra = { ClockPreview(colors) },
                ),
            )
        }
    }

    if (showLocation) {
        TextInputDialog(
            title = stringResource(R.string.settings_custom_location),
            initial = location,
            label = stringResource(R.string.settings_city_latlon),
            hint = stringResource(R.string.settings_location_hint),
            onConfirm = { vm.setWeatherLocation(it); showLocation = false },
            onDismiss = { showLocation = false },
        )
    }
    picking?.let { part ->
        val start = remember(part) { colors.of(part) }
        val text = StageColors.Text
        val muted = StageColors.Muted
        StageColorPopup(
            eyebrow = stringResource(R.string.settings_title_date_time_weather),
            title = stringResource(part.titleRes),
            presets = listOf(
                ColorChoice(text, stringResource(R.string.settings_clock_color_white)) { vm.setClockColor(part, text.hex()) },
                ColorChoice(muted, stringResource(R.string.settings_clock_color_grey)) { vm.setClockColor(part, muted.hex()) },
                ColorChoice(stageAccent.accent, stringResource(R.string.settings_clock_color_accent)) { vm.setClockColor(part, ClockColors.ACCENT) },
            ) + AccentColor.entries.map { ac ->
                val c = ac.primary(true)
                ColorChoice(c, stringResource(ac.labelRes)) { vm.setClockColor(part, c.hex()) }
            },
            start = clockColor(start, part.default),
            current = clockColor(colors.of(part), part.default),
            onLive = { vm.setClockColor(part, it) },
            onCancel = { vm.setClockColor(part, start) },
            onDone = { hex -> if (hex != null) vm.setClockColor(part, hex) },
            onDismiss = { picking = null },
        ) { ClockPreview(colors) }
    }
}

/** The panel's live preview: the top bar's clock as it looks with these colours. */
@Composable
private fun ClockPreview(colors: ClockColors) {
    val (weather, fahrenheit) = LocalClockWeather.current
    Box(
        Modifier
            .padding(top = 20.mpx)
            .fillMaxWidth()
            .background(StageColors.Ink.copy(alpha = 0.04f), RoundedCornerShape(18.mpx))
            .padding(20.mpx),
        contentAlignment = Alignment.CenterEnd,
    ) { StageClock(weather, fahrenheit, colors) }
}

private val ClockPart.titleRes: Int get() = when (this) {
    ClockPart.TIME -> R.string.settings_clock_time_color
    ClockPart.DATE -> R.string.settings_clock_date_color
    ClockPart.WEATHER -> R.string.settings_clock_weather_color
}

private val ClockPart.lineRes: Int get() = when (this) {
    ClockPart.TIME -> R.string.settings_line_clock_time
    ClockPart.DATE -> R.string.settings_line_clock_date
    ClockPart.WEATHER -> R.string.settings_line_clock_weather
}

private val ClockPart.icon: OwnTVIcon get() = when (this) {
    ClockPart.TIME -> OwnTVIcon.CLOCK
    ClockPart.DATE -> OwnTVIcon.CALENDAR
    ClockPart.WEATHER -> OwnTVIcon.SUN
}

/** The shipped colour of each part: white time, grey date and weather. */
private val ClockPart.default: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = if (this == ClockPart.TIME) StageColors.Text else StageColors.Muted

private fun Color.hex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

/** The row's value: White / Grey / Accent, a preset's name, or the hex code. */
@Composable
private fun clockColorLabel(part: ClockPart, stored: String): String {
    val shown = if (stored.isBlank()) part.default.hex() else stored
    return when {
        shown == ClockColors.ACCENT -> stringResource(R.string.settings_clock_color_accent)
        shown.equals(StageColors.Text.hex(), ignoreCase = true) -> stringResource(R.string.settings_clock_color_white)
        shown.equals(StageColors.Muted.hex(), ignoreCase = true) -> stringResource(R.string.settings_clock_color_grey)
        else -> AccentColor.entries.firstOrNull { it.primary(true).hex().equals(shown, ignoreCase = true) }
            ?.let { stringResource(it.labelRes) } ?: shown.uppercase()
    }
}
