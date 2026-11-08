package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import tv.own.owntv.ui.theme.mpx

/**
 * glcn: the widths of the player's channel list (the zap deck) — groups, channels and the focused programme —
 * as a share of the screen, set in Settings → Layout → Panel widths. Whatever they leave is the video's.
 * The defaults are the deck as it always was (280 · 570 · 480 of 1920).
 */
object DeckWidths {
    data class Shares(val groups: Int, val channels: Int, val programme: Int) {
        val total: Int get() = groups + channels + programme
        val isValid: Boolean get() =
            groups in GROUPS_MIN..GROUPS_MAX && channels in CHANNELS_MIN..CHANNELS_MAX &&
                programme in PROGRAMME_MIN..PROGRAMME_MAX && total <= TOTAL_MAX
    }

    const val GROUPS_MIN = 10
    const val GROUPS_MAX = 25
    const val CHANNELS_MIN = 20
    const val CHANNELS_MAX = 45
    const val PROGRAMME_MIN = 15
    const val PROGRAMME_MAX = 50
    /** Some of the picture always stays in view beside the deck. */
    const val TOTAL_MAX = 92
    val DEFAULT = Shares(groups = 15, channels = 30, programme = 25)

    /** The groups column folded to its icons while the channels or the schedule have the cursor. */
    val FOLDED_GROUPS: Dp = 96.mpx

    var shares by mutableStateOf(DEFAULT)
        private set
    val custom: Boolean get() = shares != DEFAULT
    private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        val read = Shares(p.getInt(K_GROUPS, DEFAULT.groups), p.getInt(K_CHANNELS, DEFAULT.channels), p.getInt(K_PROGRAMME, DEFAULT.programme))
        shares = if (read.isValid) read else DEFAULT
    }

    fun set(context: Context, value: Shares) {
        val v = if (value.isValid) value else DEFAULT
        shares = v
        prefs(context).edit().putInt(K_GROUPS, v.groups).putInt(K_CHANNELS, v.channels).putInt(K_PROGRAMME, v.programme).apply()
    }

    /** A share of the 1920-wide design screen. */
    fun width(pct: Int): Dp = (pct * 19.2f).mpx

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("glcn_deck_widths", Context.MODE_PRIVATE)
    private const val K_GROUPS = "groups"
    private const val K_CHANNELS = "channels"
    private const val K_PROGRAMME = "programme"
}
