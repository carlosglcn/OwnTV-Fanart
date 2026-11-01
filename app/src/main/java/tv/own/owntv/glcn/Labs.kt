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
 * device with a key sequence in Settings — ▲ ▲ ▼ ▼ ◀ ▶ ◀ ▶. The same sequence switches them off again.
 */
object Labs {
    var on by mutableStateOf(false)
        private set
    private var loaded = false
    private var step = 0

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        on = prefs(context).getBoolean(KEY, false)
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

    private val SEQUENCE = listOf(
        Key.DirectionUp, Key.DirectionUp, Key.DirectionDown, Key.DirectionDown,
        Key.DirectionLeft, Key.DirectionRight, Key.DirectionLeft, Key.DirectionRight,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private const val PREFS = "glcn_labs"
    private const val KEY = "on"
}
