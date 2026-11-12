package tv.own.owntv.player

import tv.own.owntv.ui.theme.hudShade
import tv.own.owntv.ui.theme.hudInk
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter

/**
 * The player's clock, centred at the top of the HUD.
 *
 * Sits in the middle rather than a corner because that band is empty in every mode — so the guide card
 * on the right keeps exactly the position it has always had, and on movies and episodes nothing moves
 * at all. A corner clock would have pushed the guide down on live channels for no gain.
 *
 * One panel, one or two columns. Normally it holds only **Current time**. When [watchingMs] is non-null
 * an archive is on screen, and **Programme time** joins it on the left: the recording's own date and
 * time, advancing as it plays. Both are needed at once — a lone real clock reads 10:00 over a picture
 * from yesterday afternoon, which is worse than showing no clock at all.
 *
 * Both columns are labelled, including when only one is present. The label is what stops a lone
 * "13:00" from being read as a broken device clock, and it costs one small line.
 */
@Composable
internal fun PlayerClock(watchingMs: Long?, modifier: Modifier = Modifier) {
    val formatTime = rememberSystemTimeFormatter()
    val formatDate = rememberBestDateFormatter("EEEdMMM")
    // Minute precision is all that is displayed, so a 10 s tick keeps it honest without waking the
    // frame loop pointlessly. The HUD is only on screen in bursts anyway.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); nowMs = System.currentTimeMillis() } }

    // glcn: one slim pill along the top centre — the time, the date and the weather, and only while they apply the
    // sleep timer, a recording and the next reminder. No "Current time" label: the pill reads as a clock by itself.
    val ink = hudInk
    val dim = hudInk.copy(alpha = 0.62f)
    val settings: tv.own.owntv.core.settings.SettingsRepository = org.koin.compose.koinInject()
    val weatherRepo: tv.own.owntv.core.weather.WeatherRepository = org.koin.compose.koinInject()
    val weatherOn by settings.weatherEnabled.collectAsState(false)
    val weatherLoc by settings.weatherLocation.collectAsState("")
    val fahrenheit by settings.weatherFahrenheit.collectAsState(false)
    val weather by produceState<tv.own.owntv.core.weather.WeatherInfo?>(null, weatherOn, weatherLoc) {
        value = if (weatherOn) runCatching { weatherRepo.get(weatherLoc) }.getOrNull() else null
    }
    val sleep: tv.own.owntv.player.SleepTimer = org.koin.compose.koinInject()
    val sleepLeft by sleep.remainingMs.collectAsState()
    val recordings: tv.own.owntv.core.recording.RecordingActivityTracker = org.koin.compose.koinInject()
    val recording by recordings.active.collectAsState()
    val reminderManager: tv.own.owntv.core.reminder.ReminderManager = org.koin.compose.koinInject()
    val profileId by settings.activeProfileId.collectAsState(-1L)
    val reminders by remember(profileId) {
        if (profileId >= 0) reminderManager.observe(profileId) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(emptyList())
    val soon = reminders.filter { it.startMs > nowMs && it.startMs - nowMs <= REMINDER_AHEAD_MS }.minByOrNull { it.startMs }

    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(hudShade(0.42f))
            .border(1.dp, hudInk.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (watchingMs != null) {
            // An archive on screen: the recording's own time first, in the accent, then the real one.
            val a = tv.own.owntv.ui.theme.stageAccent.ink
            Text(formatTime(watchingMs), style = tv.own.owntv.ui.theme.stageText(26, 600), color = a, maxLines = 1)
            Text(formatDate(watchingMs), style = tv.own.owntv.ui.theme.stageText(17, 500), color = a.copy(alpha = 0.75f), maxLines = 1)
            Dot(dim)
        }
        Text(formatTime(nowMs), style = tv.own.owntv.ui.theme.stageText(26, 600), color = ink, maxLines = 1)
        Text(formatDate(nowMs), style = tv.own.owntv.ui.theme.stageText(17, 500), color = dim, maxLines = 1)
        weather?.let { w ->
            Dot(dim)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tv.own.owntv.features.shell.components.WeatherConditionIcon(w, Modifier.size(20.dp))
                val deg = if (fahrenheit) (w.temperatureC * 9 / 5 + 32).toInt() else w.temperatureC.toInt()
                Text("$deg°", style = tv.own.owntv.ui.theme.stageText(18, 600), color = ink, maxLines = 1)
                if (w.city.isNotBlank()) Text(w.city, style = tv.own.owntv.ui.theme.stageText(17, 500), color = dim, maxLines = 1)
            }
        }
        sleepLeft?.let { left ->
            Dot(dim)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.BEDTIME, ink, Modifier.size(18.dp))
                Text(stringResource(R.string.glcn_pill_minutes, ((left + 59_999) / 60_000).toInt()), style = tv.own.owntv.ui.theme.stageText(17, 600), color = ink, maxLines = 1)
            }
        }
        if (recording.isNotEmpty()) {
            Dot(dim)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFF3B30)))
                Text(stringResource(R.string.glcn_pill_recording), style = tv.own.owntv.ui.theme.stageText(17, 600), color = ink, maxLines = 1)
            }
        }
        soon?.let { r ->
            Dot(dim)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.BELL, tv.own.owntv.ui.theme.stageAccent.ink, Modifier.size(18.dp))
                Text(
                    stringResource(R.string.glcn_pill_reminder, ((r.startMs - nowMs + 59_999) / 60_000).toInt(), r.title, tv.own.owntv.core.live.ProviderTags.parse(r.channelName).name),
                    style = tv.own.owntv.ui.theme.stageText(17, 600), color = ink, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 520.dp),
                )
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
}

/** glcn: a reminder shows in the pill from this long before its programme starts. */
private const val REMINDER_AHEAD_MS = 15 * 60_000L
