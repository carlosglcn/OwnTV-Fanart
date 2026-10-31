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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
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
    // glcn: the video filled to the window's edges — YouTube centres a 16:9 picture in whatever box it gets, and a
    // wider window showed it as a smaller rectangle with bars beside it, a visible cut inside the faded window.
    // Scaled up just enough to cover, the overflow clipped.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clipToBounds()) {
        val ratio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else VIDEO_RATIO
        val cover = maxOf(ratio / VIDEO_RATIO, VIDEO_RATIO / ratio).coerceIn(1f, 1.6f) * TRAILER_OVERSCAN
        AndroidView(
            factory = { (view.parent as? ViewGroup)?.removeView(view); view },
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha; scaleX = cover; scaleY = cover },
            onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
        )
    }
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
    /** The video the player holds now (cued or playing), and whether it is playing — a page taking over the same
     *  trailer keeps it going rather than loading it again (that reload was the slow step on opening a title). */
    private var loadedKey: String? = null
    private var lastSecond = 0f
    private var lastWindow: android.os.IBinder? = null
    private var isPlaying = false
    private val idleFree = Runnable { if (request == null) free() }
    /** glcn: another video is playing full screen (a film's extra): the trailer waits, paused, and goes on after. */
    private var holds = 0

    /** glcn: pause the trailer while something else plays ([on] = true), and let it go on once that ends. */
    fun hold(on: Boolean) {
        holds = (holds + if (on) 1 else -1).coerceAtLeast(0)
        val p = player ?: return
        if (holds > 0) runCatching { p.pause() }
        else request?.takeIf { it.started }?.let { runCatching { p.play() } }
    }

    /** Get the player ready ahead of the first trailer (a page that may show one calls this). */
    fun warm(context: Context) {
        // glcn: a modest box makes the player only when a trailer is really about to play (it is heavy).
        if (!allowed() || tv.own.owntv.glcn.DeviceClass.modest) return
        view(context)
        if (request == null) { main.removeCallbacks(idleFree); main.postDelayed(idleFree, IDLE_FREE_MS) }
    }

    fun view(context: Context): YouTubePlayerView? {
        if (!allowed()) return null
        view?.let { return it }
        // glcn: made on the activity (not a dialog's context, not the application's): a web view on the application's
        // context does not know the screen it draws on, and video played less smoothly. Let go when the activity goes.
        val app = activityOf(context) ?: context.applicationContext
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
        // glcn: moved into another window (a title's page opens in its own), the video went on playing but its picture
        // did not follow — heard and not seen. On such a move a playing trailer is loaded again at the second it was at.
        created.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {
                val token = v.windowToken
                val moved = lastWindow != null && token != null && token != lastWindow
                lastWindow = token
                val r = request ?: return
                val key = loadedKey ?: return
                if (moved && r.started) main.post { if (request === r) runCatching { player?.loadVideo(key, lastSecond) } }
            }
            override fun onViewDetachedFromWindow(v: android.view.View) {}
        })
        view = created
        return created
    }

    fun claim(r: Request) {
        main.removeCallbacks(idleFree)
        val p = player
        val key = r.keys.getOrNull(r.index)
        if (p != null && key != null && key == loadedKey) {
            // The same trailer, handed on: it goes on from where it is, no new load.
            request = r
            applyMute(p, r.muted)
            if (r.start) { r.started = true; if (isPlaying) r.onPlaying() else if (holds == 0) p.play() }
            return
        }
        request?.let { old -> if (old.owner !== r.owner) runCatching { player?.pause() } }
        request = r
        p?.let { load(it, r) }
    }

    fun play(owner: Any) {
        val r = request?.takeIf { it.owner === owner } ?: return
        r.start = true
        val p = player ?: return
        if (!r.started) { r.started = true; if (holds == 0) p.play() }
    }

    fun setMuted(owner: Any, muted: Boolean) {
        val r = request?.takeIf { it.owner === owner } ?: return
        r.muted = muted
        player?.let { applyMute(it, muted) }
    }

    fun release(owner: Any) {
        if (request?.owner !== owner) return
        request = null
        // Paused a moment later, not at once: a page taking the same trailer over claims it within a frame or two,
        // and a pause then a play in between was a visible hitch.
        main.removeCallbacks(pauseIdle)
        main.postDelayed(pauseIdle, HANDOVER_MS)
        main.removeCallbacks(idleFree)
        main.postDelayed(idleFree, IDLE_FREE_MS)
    }

    private val pauseIdle = Runnable { if (request == null) runCatching { player?.pause() } }

    /** Let the player go now, its memory with it; the next trailer makes a new one. */
    fun free() {
        main.removeCallbacks(idleFree)
        val v = view ?: return
        val r = request
        request = null
        view = null
        player = null
        loadedKey = null
        isPlaying = false
        lastWindow = null
        (v.parent as? ViewGroup)?.removeView(v)
        runCatching { v.release() }
        r?.onDone?.invoke()
    }

    private fun allowed(): Boolean = !tv.own.owntv.glcn.LowRam.on

    private fun activityOf(context: Context): android.app.Activity? {
        var c: Context? = context
        while (c is android.content.ContextWrapper) {
            if (c is android.app.Activity) return c
            c = c.baseContext
        }
        return null
    }

    private fun applyMute(p: YouTubePlayer, muted: Boolean) {
        if (muted) p.mute() else { p.unMute(); p.setVolume(100) }
    }

    private fun load(p: YouTubePlayer, r: Request) {
        applyMute(p, r.muted)
        val key = r.keys.getOrNull(r.index) ?: return
        loadedKey = key
        isPlaying = false
        if (r.start && holds == 0) { r.started = true; p.loadVideo(key, r.startAt) } else p.cueVideo(key, r.startAt)
    }

    private val listener = object : AbstractYouTubePlayerListener() {
        override fun onReady(youTubePlayer: YouTubePlayer) {
            player = youTubePlayer
            request?.let { load(youTubePlayer, it) }
        }
        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) { lastSecond = second; request?.onSecond?.invoke(second) }
        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
            isPlaying = state == PlayerConstants.PlayerState.PLAYING
            if (state == PlayerConstants.PlayerState.ENDED) loadedKey = null
            val r = request ?: return
            // glcn: moved to another page (another window), the web view pauses the video by itself — a trailer that is
            // still wanted plays on, so entering a title's page never stops it.
            if (state == PlayerConstants.PlayerState.PAUSED && r.started && holds == 0) {
                main.postDelayed({ if (request === r && !isPlaying && holds == 0) runCatching { player?.play() } }, RESUME_AFTER_MOVE_MS)
            }
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
                loadedKey = key
                if (r.started) youTubePlayer.loadVideo(key, 0f) else youTubePlayer.cueVideo(key, 0f)
            } else { request = null; r.onDone() }
        }
    }

    private const val IDLE_FREE_MS = 60_000L
    private const val HANDOVER_MS = 250L
    private const val RESUME_AFTER_MOVE_MS = 120L
}

/** glcn: a trailer's own shape, and a little more so a thin line at its edge never shows. */
private const val VIDEO_RATIO = 16f / 9f
private const val TRAILER_OVERSCAN = 1.03f

/** glcn: how fast a trailer comes in over the picture once it plays. */
private const val TRAILER_FADE_MS = 450
