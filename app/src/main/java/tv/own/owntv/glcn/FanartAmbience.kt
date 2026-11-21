package tv.own.owntv.glcn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

/**
 * glcn: Settings and More keep the app's look — behind them, the colours of the film or series watched last as a soft
 * glow (its most vivid colour from one corner, a neighbouring hue from the other), not the picture itself: a picture
 * behind the tiles took their contrast and drew the eye. Nothing watched yet, or a picture without colour: the page
 * as before.
 */
@Composable
fun Modifier.fanartAmbience(): Modifier {
    val url by produceState<String?>(null) { value = runCatching { lastBackdrop() }.getOrNull() }
    val tone by ArtTone.of(url)
    val c = tone?.vivid
    val light = tv.own.owntv.ui.theme.LocalStagePalette.current.isLight
    val shown by androidx.compose.animation.core.animateFloatAsState(if (c != null) 1f else 0f, androidx.compose.animation.core.tween(AMBIENCE_FADE_MS), label = "ambience")
    val main = c ?: return this
    val other = remember(main) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(main.toArgb(), hsv)
        hsv[0] = (hsv[0] + AMBIENCE_HUE_SHIFT) % 360f
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val strength = if (light) AMBIENCE_ALPHA_LIGHT else AMBIENCE_ALPHA_DARK
    return this.drawBehind {
        val a = strength * shown
        drawRect(Brush.radialGradient(listOf(main.copy(alpha = a), Color.Transparent), center = Offset(size.width * 0.88f, size.height * 0.08f), radius = size.maxDimension * 0.75f))
        drawRect(Brush.radialGradient(listOf(other.copy(alpha = a * 0.6f), Color.Transparent), center = Offset(size.width * 0.08f, size.height * 0.98f), radius = size.maxDimension * 0.6f))
    }
}

/** The last film's picture, or the last episode's series'. */
private suspend fun lastBackdrop(): String? {
    val koin = GlobalContext.get()
    val pid = koin.get<SettingsRepository>().activeProfileId.first()
    if (pid < 0) return null
    val history = koin.get<HistoryDao>()
    val movie = history.observeMostRecentOfType(pid, MediaType.MOVIE).first()
    val episode = history.observeMostRecentOfType(pid, MediaType.EPISODE).first()
    val movieUrl = movie?.let { koin.get<MovieDao>().getById(it.itemId)?.backdropUrl?.takeIf(String::isNotBlank) }
    val showUrl = episode?.let { e ->
        koin.get<SeriesDao>().getEpisodeById(e.itemId)?.seriesId?.let { koin.get<SeriesDao>().getSeriesById(it)?.backdropUrl?.takeIf(String::isNotBlank) }
    }
    return when {
        movieUrl != null && showUrl != null -> if ((movie?.watchedAt ?: 0) >= (episode?.watchedAt ?: 0)) movieUrl else showUrl
        else -> movieUrl ?: showUrl
    }
}

private const val AMBIENCE_ALPHA_DARK = 0.30f
private const val AMBIENCE_ALPHA_LIGHT = 0.18f
private const val AMBIENCE_HUE_SHIFT = 40f
private const val AMBIENCE_FADE_MS = 600
