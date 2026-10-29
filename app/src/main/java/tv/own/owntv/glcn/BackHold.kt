package tv.own.owntv.glcn

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * glcn: holding Back. Watched at the window, before Compose: in the full-screen player focus can sit nowhere (or in the
 * picture's own view), and then Compose never saw the key — the release reached the activity as a plain Back and the
 * player went to its window instead of closing. The shell says when a hold means something ([armed]) and what it does
 * ([onHold]); a hold that fired swallows the rest of its key, so no Back runs after it.
 */
object BackHold {
    @Volatile var armed: () -> Boolean = { false }
    @Volatile var onHold: () -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private var fired = false
    private var pending = false
    private val fire = Runnable { pending = false; fired = true; onHold() }

    /** True when the event is taken (the rest of a hold that already acted). */
    fun onKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    fired = false
                    if (armed()) { pending = true; main.postDelayed(fire, HOLD_MS) }
                } else if (!fired && event.eventTime - event.downTime >= HOLD_MS && (pending || armed())) {
                    // The key's own repeats say it is still held: act now if the timer has not (a busy main thread).
                    main.removeCallbacks(fire); pending = false; fired = true; onHold()
                }
                return fired
            }
            KeyEvent.ACTION_UP -> {
                if (pending) { main.removeCallbacks(fire); pending = false }
                val taken = fired
                fired = false
                return taken
            }
        }
        return false
    }

    private const val HOLD_MS = 800L
}
