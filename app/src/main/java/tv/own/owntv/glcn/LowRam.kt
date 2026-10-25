package tv.own.owntv.glcn

import android.app.ActivityManager
import android.content.Context

/**
 * glcn: "light mode" for boxes with little memory (about 2 GB or less). Nothing to configure — the
 * app simply asks less of the device: no live preview that opens a stream on every step, no
 * auto-drifting text, no image cross-fades, a smaller picture cache. Detected once, at start, from two
 * cheap system numbers.
 */
object LowRam {
    @Volatile var on: Boolean = false
        private set

    fun detect(context: Context) {
        on = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            am.isLowRamDevice || mem.totalMem in 1 until LOW_RAM_BYTES
        }.getOrDefault(false)
    }

    private const val LOW_RAM_BYTES = 2_600_000_000L
}
