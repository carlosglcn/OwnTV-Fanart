package tv.own.owntv.glcn

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window

/**
 * glcn: how smoothly the app draws, per screen, into the playback report — so the slow spots are known by measurement,
 * not guessed. Android hands over each drawn frame's timing ([FrameMetrics]) on a background thread; here they are only
 * counted (no allocation per frame). Once a minute [MemoryWatch] asks for a line: frames drawn on each screen, how many
 * missed the 60 fps beat, and the worst one. Nothing is written while nothing is drawn (a film playing, the box asleep).
 */
object FrameWatch {
    /** What is on screen now (set by the shell): "HOME", "LIVE_TV", "player"… */
    @Volatile var screen: String = "start"
    /** What else is going on that can cost frames (set while it happens). */
    @Volatile var trailerOn = false

    private class Tally { var frames = 0; var slow = 0; var janky = 0; var worstMs = 0; var worstWithTrailer = false }
    private val tallies = HashMap<String, Tally>()
    private val lock = Any()
    private var thread: HandlerThread? = null

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> count(metrics, screen) }
    private val popupListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> count(metrics, "$screen·popup") }

    private fun count(metrics: FrameMetrics, label: String) {
        val ms = (metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000L).toInt()
        // The first frame after a pause (a new window, back from sleep) is not a stutter you see.
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return
        synchronized(lock) {
            val t = tallies.getOrPut(label) { Tally() }
            t.frames++
            if (ms > SLOW_MS) t.slow++
            if (ms > JANK_MS) t.janky++
            if (ms > t.worstMs) { t.worstMs = ms; t.worstWithTrailer = trailerOn }
        }
    }

    fun attach(activity: Activity) = attach(activity.window, popup = false)

    fun detach(activity: Activity) = detach(activity.window, popup = false)

    /** glcn: a popup window (a title's page, a menu) draws its own frames — counted as "<screen>·popup". */
    @Synchronized fun attach(window: Window, popup: Boolean) {
        val th = thread ?: HandlerThread("glcn-frames", android.os.Process.THREAD_PRIORITY_BACKGROUND).also { it.start(); thread = it }
        if (runCatching { window.addOnFrameMetricsAvailableListener(if (popup) popupListener else listener, Handler(th.looper)) }.isSuccess && popup) popups++
    }

    @Synchronized fun detach(window: Window, popup: Boolean) {
        if (runCatching { window.removeOnFrameMetricsAvailableListener(if (popup) popupListener else listener) }.isSuccess && popup) popups = (popups - 1).coerceAtLeast(0)
    }

    @Volatile private var popups = 0

    /** The minute's line ("" when nothing was drawn); the counters start again. */
    fun drain(): String {
        val snapshot = synchronized(lock) { tallies.toMap().also { tallies.clear() } }
        return snapshot.entries.filter { it.value.frames >= MIN_FRAMES }.sortedByDescending { it.value.frames }.joinToString(" · ") { (name, t) ->
            val pct = t.slow * 1000 / t.frames / 10.0
            "$name ${t.frames}f ${pct}% slow" + (if (t.janky > 0) ", ${t.janky} >${JANK_MS}ms" else "") +
                ", worst ${t.worstMs}ms" + (if (t.worstWithTrailer) " (trailer)" else "")
        }
    }

    /** A frame longer than this missed the 60 fps beat by a visible margin. */
    private const val SLOW_MS = 25
    /** A frame longer than this is a stutter anyone sees. */
    private const val JANK_MS = 100
    private const val MIN_FRAMES = 30
}
