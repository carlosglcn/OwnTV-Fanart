package tv.own.owntv.ui.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * glcn: a title's trailer playing silently behind the page (the Dock's and Cinema's backdrop, a title's page), with no
 * controls and no focus. It shows only once it really plays — fading in over the picture — and goes when it ends or
 * errors ([onDone]). Never in light mode: the caller does not compose it then.
 *
 * The player itself is [TrailerPool]'s one, kept ready: starting YouTube's player (a web view) is what made a trailer
 * wait two or three seconds, and doing it per title is what made a row stutter. Here a title only asks it for a video.
 */
@Composable
fun AmbientTrailer(
    stored: String,
    modifier: Modifier = Modifier,
    /** glcn: false while it is only being got ready (the player cues the video unseen); true plays it. */
    start: Boolean = true,
    /** glcn: silent unless the viewer asked for sound (Play/Pause while it plays). */
    muted: Boolean = true,
    /** glcn: where to begin — a trailer carried over from Home to the title's page goes on from where it was. */
    startAt: Float = 0f,
    /** glcn: how far it has played, for that carry-over. */
    onSecond: (Float) -> Unit = {},
    onPlaying: () -> Unit = {},
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val keys = remember(stored) { tv.own.owntv.core.metadata.TrailerKeys.split(stored).ifEmpty { listOf(stored) } }
    var playing by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (playing) 1f else 0f, tv.own.owntv.ui.theme.ownTvTween(TRAILER_FADE_MS), label = "ambientTrailer")
    val view = remember { TrailerPool.view(context) }
    if (view == null) { LaunchedEffect(Unit) { onDone() }; return }
    val secondNow by rememberUpdatedState(onSecond)
    val playingNow by rememberUpdatedState(onPlaying)
    val doneNow by rememberUpdatedState(onDone)
    val owner = remember { Any() }
    DisposableEffect(stored) {
        TrailerPool.claim(
            TrailerPool.Request(
                owner = owner, keys = keys, startAt = startAt, start = start, muted = muted,
                onSecond = { secondNow(it) },
                onPlaying = { playing = true; playingNow() },
                onDone = { playing = false; doneNow() },
            ),
        )
        onDispose { playing = false; TrailerPool.release(owner) }
    }
    LaunchedEffect(start) { if (start) TrailerPool.play(owner) }
    LaunchedEffect(muted) { TrailerPool.setMuted(owner, muted) }
    AndroidView(
        factory = { (view.parent as? ViewGroup)?.removeView(view); view },
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
    )
}

/**
 * glcn: the one trailer player, kept ready while Home, Films or Series are on screen. Created once (on the
 * application's context, so it outlives no screen), it is lent to whichever page shows a trailer; a new request
 * simply cues another video. It is let go — its memory with it — when something plays, when Android asks for memory,
 * when the app goes to the background, in light mode, and after a minute with no page wanting it.
 */
object TrailerPool {
    class Request(
        val owner: Any,
        val keys: List<String>,
        val startAt: Float,
        var start: Boolean,
        var muted: Boolean,
        val onSecond: (Float) -> Unit,
        val onPlaying: () -> Unit,
        val onDone: () -> Unit,
    ) {
        var index = 0
        var started = false
    }

    private val main = Handler(Looper.getMainLooper())
    private var view: YouTubePlayerView? = null
    private var player: YouTubePlayer? = null
    private var request: Request? = null
    private val idleFree = Runnable { if (request == null) free() }

    /** Get the player ready ahead of the first trailer (a page that may show one calls this). */
    fun warm(context: Context) {
        if (!allowed()) return
        view(context)
        if (request == null) { main.removeCallbacks(idleFree); main.postDelayed(idleFree, IDLE_FREE_MS) }
    }

    fun view(context: Context): YouTubePlayerView? {
        if (!allowed()) return null
        view?.let { return it }
        val app = context.applicationContext
        val created = runCatching {
            YouTubePlayerView(app).apply {
                enableAutomaticInitialization = false
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        }.getOrNull() ?: return null
        val options = IFramePlayerOptions.Builder(app).controls(0).fullscreen(0).rel(0).ivLoadPolicy(3).ccLoadPolicy(0).build()
        if (runCatching { created.initialize(listener, options) }.isFailure) { runCatching { created.release() }; return null }
        view = created
        return created
    }

    fun claim(r: Request) {
        main.removeCallbacks(idleFree)
        request?.let { old -> if (old.owner !== r.owner) runCatching { player?.pause() } }
        request = r
        player?.let { load(it, r) }
    }

    fun play(owner: Any) {
        val r = request?.takeIf { it.owner === owner } ?: return
        r.start = true
        val p = player ?: return
        if (!r.started) { r.started = true; p.play() }
    }

    fun setMuted(owner: Any, muted: Boolean) {
        val r = request?.takeIf { it.owner === owner } ?: return
        r.muted = muted
        player?.let { applyMute(it, muted) }
    }

    fun release(owner: Any) {
        if (request?.owner !== owner) return
        request = null
        runCatching { player?.pause() }
        main.removeCallbacks(idleFree)
        main.postDelayed(idleFree, IDLE_FREE_MS)
    }

    /** Let the player go now, its memory with it; the next trailer makes a new one. */
    fun free() {
        main.removeCallbacks(idleFree)
        val v = view ?: return
        val r = request
        request = null
        view = null
        player = null
        (v.parent as? ViewGroup)?.removeView(v)
        runCatching { v.release() }
        r?.onDone?.invoke()
    }

    private fun allowed(): Boolean = !tv.own.owntv.glcn.LowRam.on

    private fun applyMute(p: YouTubePlayer, muted: Boolean) {
        if (muted) p.mute() else { p.unMute(); p.setVolume(100) }
    }

    private fun load(p: YouTubePlayer, r: Request) {
        applyMute(p, r.muted)
        val key = r.keys.getOrNull(r.index) ?: return
        if (r.start) { r.started = true; p.loadVideo(key, r.startAt) } else p.cueVideo(key, r.startAt)
    }

    private val listener = object : AbstractYouTubePlayerListener() {
        override fun onReady(youTubePlayer: YouTubePlayer) {
            player = youTubePlayer
            request?.let { load(youTubePlayer, it) }
        }
        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) { request?.onSecond?.invoke(second) }
        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
            val r = request ?: return
            when (state) {
                PlayerConstants.PlayerState.PLAYING -> if (r.started) r.onPlaying()
                PlayerConstants.PlayerState.ENDED -> { request = null; r.onDone() }
                else -> Unit
            }
        }
        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
            val r = request ?: return
            if (r.index < r.keys.lastIndex) {
                r.index++
                val key = r.keys[r.index]
                if (r.started) youTubePlayer.loadVideo(key, 0f) else youTubePlayer.cueVideo(key, 0f)
            } else { request = null; r.onDone() }
        }
    }

    private const val IDLE_FREE_MS = 60_000L
}

/** glcn: how fast a trailer comes in over the picture once it plays. */
private const val TRAILER_FADE_MS = 450
