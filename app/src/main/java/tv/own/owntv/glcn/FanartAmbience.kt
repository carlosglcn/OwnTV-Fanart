package tv.own.owntv.glcn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

/**
 * glcn: Settings and More keep the app's look — behind them, the picture of the film or series watched last, faint
 * and soft (fetched small and drawn large, so it blurs for free), instead of a bare page. Nothing watched yet: the
 * page as before.
 */
@Composable
fun Modifier.fanartAmbience(): Modifier {
    val url by produceState<String?>(null) { value = runCatching { lastBackdrop() }.getOrNull() }
    val u = url ?: return this
    val context = LocalContext.current
    val request = remember(u) { ImageRequest.Builder(context).data(u).size(AMBIENCE_W, AMBIENCE_H).build() }
    val painter = rememberAsyncImagePainter(request)
    val alpha = if (tv.own.owntv.ui.theme.LocalStagePalette.current.isLight) AMBIENCE_ALPHA_LIGHT else AMBIENCE_ALPHA_DARK
    return this.paint(painter, contentScale = ContentScale.Crop, alpha = alpha)
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

private const val AMBIENCE_W = 384
private const val AMBIENCE_H = 216
private const val AMBIENCE_ALPHA_DARK = 0.20f
private const val AMBIENCE_ALPHA_LIGHT = 0.12f
