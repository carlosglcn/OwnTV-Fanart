package tv.own.owntv.glcn

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import coil3.SingletonImageLoader
import org.koin.core.context.GlobalContext
import tv.own.owntv.player.LiveDiagnosticsLog
import tv.own.owntv.player.MemoryBreakdown

/**
 * glcn: keeps an eye on memory and on the app's main thread, at no cost you can feel — one low-priority thread that
 * sleeps almost all the time, the same after three hours of a 4K film or five of a channel.
 *
 * - **Memory diary** (into the playback report): once a minute the memory is read, off the main thread; a line is
 *   written only when it has grown or shrunk noticeably, or every [QUIET_LINE_MS] while it holds — with what was going
 *   on (player, trailer, picture cache, syncing). A long evening adds a few dozen lines, never more.
 * - **Stall watch**: if the main thread does not answer for [STALL_MS], what it was doing is written down once (its
 *   stack). A hang is then known by name, and the near-hangs that come before a freeze show up too. Only while the
 *   app is on screen.
 * - **Brake**: past a threshold set by the device's memory, what can be loaded again is let go — the pictures kept
 *   in memory, the trailer player, the chapter pictures being read, picture prefetch — never what is playing, and
 *   light mode is never switched on. At most once every [BRAKE_GAP_MS].
 */
object MemoryWatch {
    /** True for a while after a brake: optional work (prefetch, chapter frames) holds back. */
    @Volatile var tight = false
        private set

    @Volatile private var started = false
    @Volatile private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var lastPong = SystemClock.uptimeMillis()
    private val pong = Runnable { lastPong = SystemClock.uptimeMillis() }

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        appContext = app
        val totalMb = runCatching {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem / (1024 * 1024)
        }.getOrDefault(2048L)
        // About a sixth of the device's memory, between 300 and 700 MB (SHIELD, 3 GB: ~490 MB).
        val brakeMb = (totalMb / 6).coerceIn(300L, 700L).toInt()
        Thread({ loop(app, brakeMb) }, "glcn-watch").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    private fun loop(app: Context, brakeMb: Int) {
        var nextSample = 0L
        var lastLoggedMb = 0
        var lastLineAt = 0L
        var lastBrakeAt = -BRAKE_GAP_MS
        var stallLogged = false
        while (true) {
            runCatching { Thread.sleep(TICK_MS) }
            val now = SystemClock.uptimeMillis()

            // Stall watch: ask the main thread to answer; if the last answer is too old, say what it is doing.
            if (AppForeground.visible) {
                val silent = now - lastPong
                if (silent >= STALL_MS && !stallLogged) {
                    stallLogged = true
                    val stack = Looper.getMainLooper().thread.stackTrace.take(STACK_LINES).joinToString(" ← ") {
                        "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                    }
                    LiveDiagnosticsLog.event("main thread stalled ${silent}ms · $stack")
                } else if (silent < STALL_MS && stallLogged) {
                    stallLogged = false
                    LiveDiagnosticsLog.event("main thread answering again")
                }
                main.post(pong)
            } else {
                lastPong = now
                stallLogged = false
            }

            // Memory diary and brake, once a minute.
            if (now < nextSample) continue
            nextSample = now + SAMPLE_MS
            val m = MemoryBreakdown.read() ?: continue
            // glcn: the whole box too — the trailer's web page and other apps use memory this process's figure never
            // shows; when the box runs short the app suffers even with a small figure of its own.
            val sys = runCatching {
                val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            }.getOrNull()
            val freeMb = sys?.let { (it.availMem / (1024 * 1024)).toInt() } ?: -1
            val boxShort = sys?.let { it.lowMemory || it.availMem < it.threshold * 3 / 2 } == true
            val mb = m.totalPssMb
            val moved = boxShort || lastLoggedMb == 0 || kotlin.math.abs(mb - lastLoggedMb) >= maxOf(MIN_STEP_MB, lastLoggedMb * STEP_PCT / 100)
            if (moved || now - lastLineAt >= QUIET_LINE_MS) {
                LiveDiagnosticsLog.event("memory · ${m.line()} · box free $freeMb MB${if (boxShort) " (short)" else ""} · ${context()}")
                lastLoggedMb = mb
                lastLineAt = now
            }
            if ((mb >= brakeMb || boxShort) && now - lastBrakeAt >= BRAKE_GAP_MS) {
                lastBrakeAt = now
                brake(app, mb, brakeMb, boxShort)
            } else if (tight && !boxShort && mb < brakeMb * 85 / 100) {
                tight = false
            }
        }
    }

    /** What was going on, in a few words. */
    private fun context(): String = buildList {
        add(if (tv.own.owntv.features.home.CinemaTrailer.blocked) "playing" else "browsing")
        if (tv.own.owntv.ui.components.TrailerPool.active) add("trailer")
        runCatching {
            val cache = appContext?.let { SingletonImageLoader.get(it).memoryCache }
            if (cache != null) add("pictures ${cache.size / (1024 * 1024)}/${cache.maxSize / (1024 * 1024)} MB")
        }
        runCatching {
            GlobalContext.getOrNull()?.getOrNull<tv.own.owntv.core.sync.SyncActivityTracker>()?.active?.value?.values
                ?.joinToString(", ") { it.sourceName }?.takeIf { it.isNotBlank() }?.let { add("syncing $it") }
        }
        if (!AppForeground.visible) add("in background")
    }.joinToString(" · ")

    private fun brake(app: Context, mb: Int, brakeMb: Int, boxShort: Boolean) {
        tight = true
        main.post {
            tv.own.owntv.ui.components.TrailerPool.free()
            runCatching { SingletonImageLoader.get(app).memoryCache?.clear() }
        }
        LiveDiagnosticsLog.event("memory brake at $mb MB (limit $brakeMb)${if (boxShort) ", box short" else ""}: pictures in memory, trailer and prefetch let go")
    }

    private const val TICK_MS = 1_000L
    private const val STALL_MS = 3_000L
    private const val STACK_LINES = 12
    private const val SAMPLE_MS = 60_000L
    private const val QUIET_LINE_MS = 10 * 60_000L
    private const val MIN_STEP_MB = 40
    private const val STEP_PCT = 15
    private const val BRAKE_GAP_MS = 3 * 60_000L
}
