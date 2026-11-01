package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * glcn: features still being tried (today: the Apps row on TV Home), hidden from everyone until switched on on this
 * device with a key sequence in Settings — ▲ ▼ four times. The same sequence switches them off again.
 */
object Labs {
    var on by mutableStateOf(false)
        private set
    private var loaded = false
    private var step = 0

    /** Open the app when the TV starts (see BootStart) — only counts while [on]. */
    var autostart by mutableStateOf(false)
        private set

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        on = prefs(context).getBoolean(KEY, false)
        autostart = prefs(context).getBoolean(KEY_AUTOSTART, false)
    }

    fun setAutostart(context: Context, value: Boolean) {
        ensure(context)
        autostart = value
        prefs(context).edit().putBoolean(KEY_AUTOSTART, value).apply()
    }

    /** Fed every key in Settings; never consumes one. */
    fun observe(context: Context, e: KeyEvent) {
        if (e.type != KeyEventType.KeyDown) return
        step = when (e.key) {
            SEQUENCE[step] -> step + 1
            SEQUENCE[0] -> 1
            else -> 0
        }
        if (step == SEQUENCE.size) {
            step = 0
            ensure(context)
            on = !on
            prefs(context).edit().putBoolean(KEY, on).apply()
            android.widget.Toast.makeText(
                context,
                context.getString(if (on) tv.own.owntv.R.string.glcn_labs_on else tv.own.owntv.R.string.glcn_labs_off),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // Up and down only: ◀ in a list goes to the menu (and the menu opens other sections), so a sequence with sideways
    // steps left Settings half way through and was never seen whole.
    private val SEQUENCE = listOf(
        Key.DirectionUp, Key.DirectionDown, Key.DirectionUp, Key.DirectionDown,
        Key.DirectionUp, Key.DirectionDown, Key.DirectionUp, Key.DirectionDown,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private const val PREFS = "glcn_labs"
    private const val KEY = "on"
    private const val KEY_AUTOSTART = "autostart"
}
