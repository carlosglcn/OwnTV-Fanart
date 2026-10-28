package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: "light mode" for boxes with little memory (about 2 GB or less). The app simply asks less of the
 * device: the live preview waits for OK, no auto-drifting text, no image cross-fades, a smaller picture
 * cache. Never switched on by itself: the person turns it on in Settings, or says yes once when the
 * system reports memory really running out.
 */
object LowRam {
    private var forced by mutableStateOf<Boolean?>(null)

    /** Light mode is in effect: forced by the person, else what the device looks like. */
    val on: Boolean get() = forced == true

    fun detect(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        forced = if (prefs.contains(FORCED)) prefs.getBoolean(FORCED, false) else null
        previewOnOk = prefs.getBoolean(OK_PREVIEW, false)
    }

    fun force(context: Context, value: Boolean) {
        forced = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(FORCED, value).apply()
    }

    /** Live preview starts with OK instead of on every step; a second OK goes full screen. */
    var previewOnOk by mutableStateOf(false)
        private set

    fun setPreviewOnOk(context: Context, value: Boolean) {
        previewOnOk = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(OK_PREVIEW, value).apply()
    }

    /** Why light mode is being suggested: the system ran short of memory, or the screen kept stuttering. */
    enum class Reason { MEMORY, SLOW }

    /** glcn: the reason to suggest light mode, once per reason; null when there is nothing to ask. */
    var reason by mutableStateOf<Reason?>(null)
        private set

    /** The question is there to ask (the prompt shows it when nothing full screen is playing). */
    val suggest: Boolean get() = reason != null

    /** glcn: true while a picture plays full screen — the question waits until the person is back in the menus. */
    var holdPrompt by mutableStateOf(false)

    fun onMemoryCritical(context: Context) = ask(context, Reason.MEMORY)

    /** glcn: the screen stuttered badly, again and again (see [JankWatch]). */
    fun onSustainedJank(context: Context) = ask(context, Reason.SLOW)

    private fun ask(context: Context, why: Reason) {
        if (on || reason != null) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(askedKey(why), false)) return
        reason = why
    }

    fun answer(context: Context, yes: Boolean) {
        val why = reason ?: return
        reason = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(askedKey(why), true).apply()
        if (yes) { force(context, true); setPreviewOnOk(context, true) }
    }

    private fun askedKey(why: Reason) = if (why == Reason.MEMORY) ASKED else ASKED_SLOW

    const val PREFS = "glcn_lowram"
    private const val FORCED = "forced"
    private const val OK_PREVIEW = "preview_on_ok"
    private const val ASKED = "asked"
    private const val ASKED_SLOW = "asked_slow"
}

/** glcn: asked once per reason — memory running out, or the screen stuttering — "Switch on light mode?" */
@androidx.compose.runtime.Composable
fun LowRamPrompt() {
    val why = LowRam.reason ?: return
    if (LowRam.holdPrompt) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val slow = why == LowRam.Reason.SLOW
    tv.own.owntv.ui.stage.StageConfirm(
        title = androidx.compose.ui.res.stringResource(if (slow) tv.own.owntv.R.string.glcn_lowram_slow_title else tv.own.owntv.R.string.glcn_lowram_prompt_title),
        body = androidx.compose.ui.res.stringResource(if (slow) tv.own.owntv.R.string.glcn_lowram_slow_body else tv.own.owntv.R.string.glcn_lowram_prompt_body),
        confirm = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.glcn_lowram_prompt_yes),
        cancel = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.glcn_lowram_prompt_no),
        onConfirm = { LowRam.answer(context, true) },
        onCancel = { LowRam.answer(context, false) },
    )
}

/**
 * glcn: watches how the screen is drawn while it moves (whole app, always on, almost free: the system hands over each
 * frame's timing). A stretch of at least [MIN_FRAMES] frames in [WINDOW_MS] with more than [SLOW_SHARE] of them late
 * is one strike; [STRIKES] strikes within [STRIKE_SPAN_MS], after the first [WARMUP_MS] (start-up and the first guide
 * read are heavy anywhere), and light mode is suggested — once.
 */
object JankWatch {
    private var installed = false
    private var windowStart = 0L
    private var frames = 0
    private var slow = 0
    private val strikes = ArrayDeque<Long>()

    fun install(activity: android.app.Activity) {
        if (installed) return
        installed = true
        val started = android.os.SystemClock.uptimeMillis()
        val thread = android.os.HandlerThread("jank-watch").also { it.start() }
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        runCatching {
            activity.window.addOnFrameMetricsAvailableListener({ _, metrics, _ ->
                val ms = metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION) / 1_000_000L
                val now = android.os.SystemClock.uptimeMillis()
                if (now - started < WARMUP_MS || LowRam.on) return@addOnFrameMetricsAvailableListener
                if (now - windowStart > WINDOW_MS) {
                    if (frames >= MIN_FRAMES && slow > frames * SLOW_SHARE) {
                        strikes.addLast(now)
                        while (strikes.isNotEmpty() && now - strikes.first() > STRIKE_SPAN_MS) strikes.removeFirst()
                        if (strikes.size >= STRIKES) {
                            strikes.clear()
                            main.post { LowRam.onSustainedJank(activity.applicationContext) }
                        }
                    }
                    windowStart = now; frames = 0; slow = 0
                }
                frames++
                if (ms > SLOW_FRAME_MS) slow++
            }, android.os.Handler(thread.looper))
        }
    }

    private const val WARMUP_MS = 3 * 60_000L
    private const val WINDOW_MS = 20_000L
    private const val MIN_FRAMES = 240
    private const val SLOW_SHARE = 0.5
    private const val SLOW_FRAME_MS = 34L
    private const val STRIKES = 3
    private const val STRIKE_SPAN_MS = 15 * 60_000L
}
