package tv.own.owntv.glcn

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * glcn: holding Back. Watched at the window, before Compose: in the full-screen player focus can sit nowhere (or in the
 * picture's own view), and then Compose never saw the key — the release reached the activity as a plain Back and the
 * player went to its window instead of closing. The shell says when a hold means something ([armed]) and what it does
 * ([onHold]); a hold that fired swallows the rest of its key, so no Back runs after it.
 *
 * Three ways to see the hold, as remotes differ: the timer while it is down, the key's own repeats, and — for a remote
 * that sends no repeats and whose timer was late — the length of the press when it is let go.
 */
object BackHold {
    @Volatile var armed: () -> Boolean = { false }
    @Volatile var onHold: () -> Unit = {}
    /** A line for the playback report about a long Back that did not close the player — to see why on the device. */
    @Volatile var note: (String) -> Unit = {}
    /**
     * glcn: the channel's little window is open (Back just sent it there). A second Back straight after closes it
     * for good — for remotes that cannot hold Back: a TV's own remote over HDMI sends each press already let go.
     */
    @Volatile var windowOpen: () -> Boolean = { false }
    private var lastUpAt = 0L

    private val main = Handler(Looper.getMainLooper())
    private var fired = false
    private var pending = false
    private var armedAtDown = false
    private var repeats = 0
    private val fire = Runnable { pending = false; act("timer") }

    private fun act(how: String) { fired = true; note("Back hold closed the player ($how)"); onHold() }

    /** True when the event is taken (the rest of a hold that already acted). */
    fun onKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    fired = false
                    repeats = 0
                    armedAtDown = armed()
                    if (armedAtDown) { pending = true; main.postDelayed(fire, HOLD_MS) }
                } else {
                    repeats++
                    if (!fired && event.eventTime - event.downTime >= HOLD_MS && (armedAtDown || armed())) {
                        // The key's own repeats say it is still held: act now if the timer has not.
                        main.removeCallbacks(fire); pending = false; act("repeats")
                    }
                }
                return fired
            }
            KeyEvent.ACTION_UP -> {
                if (pending) { main.removeCallbacks(fire); pending = false }
                val held = event.eventTime - event.downTime
                if (!fired && held >= HOLD_MS && (armedAtDown || armed())) {
                    // A long press seen only at its end: still a hold — close, and take the release.
                    act("release")
                }
                if (!fired && held >= NOTE_MS) note("Back held $held ms; armed at press=$armedAtDown, now=${armed()}; repeats=$repeats")
                if (!fired) {
                    if (event.eventTime - lastUpAt < DOUBLE_MS && windowOpen()) act("double")
                    lastUpAt = if (fired) 0L else event.eventTime
                }
                val taken = fired
                fired = false
                return taken
            }
        }
        return false
    }

    private const val HOLD_MS = 800L
    private const val NOTE_MS = 500L
    private const val DOUBLE_MS = 700L
}
