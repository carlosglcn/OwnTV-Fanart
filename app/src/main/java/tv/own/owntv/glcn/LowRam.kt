package tv.own.owntv.glcn

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: "light mode" for boxes with little memory (about 2 GB or less). The app simply asks less of the
 * device: the live preview waits for OK, no auto-drifting text, no image cross-fades, a smaller picture
 * cache. Detected once at start from two cheap system numbers; the person can force it on or off.
 */
object LowRam {
    private var detected = false
    private var forced by mutableStateOf<Boolean?>(null)

    /** Light mode is in effect: forced by the person, else what the device looks like. */
    val on: Boolean get() = forced ?: detected

    fun detect(context: Context) {
        detected = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            am.isLowRamDevice || mem.totalMem in 1 until LOW_RAM_BYTES
        }.getOrDefault(false)
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

    const val PREFS = "glcn_lowram"
    private const val FORCED = "forced"
    private const val OK_PREVIEW = "preview_on_ok"
    private const val LOW_RAM_BYTES = 2_600_000_000L
}
