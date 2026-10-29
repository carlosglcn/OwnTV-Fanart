package tv.own.owntv.ui.components

import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * glcn: a title's trailer playing silently behind the page (the Dock's and Cinema's backdrop), with no controls and
 * no focus. It shows only once it really plays — fading in over the picture — and goes when it ends or errors
 * ([onDone]). Never in light mode: the caller does not compose it then.
 */
@Composable
fun AmbientTrailer(
    stored: String,
    modifier: Modifier = Modifier,
    /** glcn: false while it is only being got ready (the player loads and cues the video unseen); true plays it. */
    start: Boolean = true,
    onPlaying: () -> Unit = {},
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val keys = remember(stored) { tv.own.owntv.core.metadata.TrailerKeys.split(stored).ifEmpty { listOf(stored) } }
    var playing by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (playing) 1f else 0f, tv.own.owntv.ui.theme.ownTvTween(900), label = "ambientTrailer")
    val view = remember {
        runCatching {
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        }.getOrNull()
    }
    if (view == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }; return }
    val options = remember(context) {
        IFramePlayerOptions.Builder(context).controls(0).fullscreen(0).rel(0).ivLoadPolicy(3).ccLoadPolicy(0).build()
    }
    // glcn: the player once ready, and whether it has been told to play — so a trailer got ready early starts at once.
    val ready = remember { arrayOfNulls<YouTubePlayer>(1) }
    val started = remember { booleanArrayOf(false) }
    val startNow by androidx.compose.runtime.rememberUpdatedState(start)
    androidx.compose.runtime.LaunchedEffect(start) {
        if (start && !started[0]) ready[0]?.let { started[0] = true; it.play() }
    }
    DisposableEffect(stored) {
        var index = 0
        val listener = object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                youTubePlayer.mute()
                ready[0] = youTubePlayer
                if (startNow) { started[0] = true; youTubePlayer.loadVideo(keys[index], 0f) } else youTubePlayer.cueVideo(keys[index], 0f)
            }
            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                when (state) {
                    PlayerConstants.PlayerState.PLAYING -> { playing = true; onPlaying() }
                    PlayerConstants.PlayerState.ENDED -> { playing = false; onDone() }
                    else -> Unit
                }
            }
            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                if (index < keys.lastIndex) {
                    index++
                    if (started[0]) youTubePlayer.loadVideo(keys[index], 0f) else youTubePlayer.cueVideo(keys[index], 0f)
                } else { playing = false; onDone() }
            }
        }
        runCatching { view.initialize(listener, options) }.onFailure { onDone() }
        onDispose { runCatching { view.release() } }
    }
    AndroidView(factory = { view }, modifier = modifier.graphicsLayer { this.alpha = alpha })
}
