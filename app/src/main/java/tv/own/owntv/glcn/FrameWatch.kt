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

    private class Tally {
        var frames = 0; var slow = 0; var janky = 0; var worstMs = 0; var worstWithTrailer = false
        // Where the slow frames' time went: building the screen (app), drawing it (gpu), or waiting for the app's turn.
        var appNs = 0L; var gpuNs = 0L; var waitNs = 0L
    }
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
            if (ms > SLOW_MS) {
                t.slow++
                t.appNs += metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) + metrics.getMetric(FrameMetrics.ANIMATION_DURATION) +
                    metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) + metrics.getMetric(FrameMetrics.DRAW_DURATION)
                t.gpuNs += metrics.getMetric(FrameMetrics.SYNC_DURATION) + metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) +
                    metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
                t.waitNs += metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
            }
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

    // glcn: what keeps the app's own thread busy between frames. Half of a slow frame's time turned out to be spent
    // waiting for that thread ("wait"), so while the fluency meter is on every piece of work it runs that takes long
    // is timed and tallied by who ran it (names read back with the build's mapping); the minute's worst are written.
    private class Busy { var ms = 0L; var count = 0; var worst = 0L }
    private val busy = HashMap<String, Busy>()
    private var workStart = 0L
    private var workWho: String? = null
    private val workLog = android.util.Printer { line ->
        if (line.startsWith(WORK_BEGIN)) { workStart = android.os.SystemClock.uptimeMillis(); workWho = line }
        else {
            val who = workWho ?: return@Printer
            workWho = null
            val ms = android.os.SystemClock.uptimeMillis() - workStart
            if (ms >= BUSY_MS) synchronized(lock) {
                val b = busy.getOrPut(whoOf(who) + WORK_ON + screen) { Busy() }
                b.ms += ms; b.count++; if (ms > b.worst) b.worst = ms
            }
        }
    }

    /** ">>>>> Dispatching to Handler (a.b.C) {1f2} x.y.Z@3e4: 0" → "C/Z" (the handler, and the task it ran). */
    private fun whoOf(line: String): String {
        val handler = line.substringAfter('(', String()).substringBefore(')').substringAfterLast('.')
        val task = line.substringAfter('}', String()).trim().substringBefore('@').substringBefore(':').trim().substringAfterLast('.')
        return if (task.isEmpty() || task == WORK_NONE) handler else handler + WORK_SEP + task
    }

    /** On while the fluency meter shows: Android builds a line of text per piece of work for this, so not otherwise. */
    fun watchMainThread(on: Boolean) {
        runCatching { android.os.Looper.getMainLooper().setMessageLogging(if (on) workLog else null) }
        if (!on) synchronized(lock) { busy.clear() }
    }

    /** The minute's longest-running kinds of work ("" when there were none). */
    fun drainBusy(): String {
        val snapshot = synchronized(lock) { busy.toMap().also { busy.clear() } }
        return snapshot.entries.sortedByDescending { it.value.ms }.take(BUSY_TOP).joinToString(" · ") { (who, b) -> "$who ${b.ms}ms/${b.count} worst ${b.worst}ms" }
    }

    /** The minute's line ("" when nothing was drawn); the counters start again. */
    fun drain(): String {
        val snapshot = synchronized(lock) { tallies.toMap().also { tallies.clear() } }
        return snapshot.entries.filter { it.value.frames >= MIN_FRAMES }.sortedByDescending { it.value.frames }.joinToString(" · ") { (name, t) ->
            val pct = t.slow * 1000 / t.frames / 10.0
            "$name ${t.frames}f ${pct}% slow" + (if (t.janky > 0) ", ${t.janky} >${JANK_MS}ms" else "") +
                ", worst ${t.worstMs}ms" + (if (t.worstWithTrailer) " (trailer)" else "") + split(t)
        }
    }

    /** glcn: of the slow frames' time, the share spent building the screen, drawing it and waiting — where to look. */
    private fun split(t: Tally): String {
        val all = t.appNs + t.gpuNs + t.waitNs
        if (t.slow < MIN_SLOW || all <= 0L) return String()
        return ", app ${t.appNs * 100 / all}% gpu ${t.gpuNs * 100 / all}% wait ${t.waitNs * 100 / all}%"
    }

    private const val WORK_BEGIN = ">>>>>"
    private const val WORK_NONE = "null"
    private const val WORK_SEP = "/"
    private const val WORK_ON = " @"
    /** Work on the app's thread shorter than this is not what makes a frame late. */
    private const val BUSY_MS = 20L
    private const val BUSY_TOP = 8
    private const val MIN_SLOW = 5
    /** A frame longer than this missed the 60 fps beat by a visible margin. */
    private const val SLOW_MS = 25
    /** A frame longer than this is a stutter anyone sees. */
    private const val JANK_MS = 100
    private const val MIN_FRAMES = 30
}
