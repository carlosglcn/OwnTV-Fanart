package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: how see-through the panels over the video are — the player's sheets, the zapping deck and its cards, the
 * player menu. Five levels; "Normal" (2) is how they always looked. Each panel keeps its own base share and this
 * scales it, with a floor so text never becomes unreadable over a bright picture.
 */
object PlayerGlass {
    const val LEVELS = 5
    const val NORMAL = 2
    private val FACTORS = floatArrayOf(0.55f, 0.75f, 1f, 1.2f, 1.6f)
    private const val FLOOR = 0.35f

    var level by mutableIntStateOf(NORMAL)
        private set
    private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        level = prefs(context).getInt(KEY, NORMAL).coerceIn(0, LEVELS - 1)
    }

    fun set(context: Context, value: Int) {
        level = value.coerceIn(0, LEVELS - 1)
        prefs(context).edit().putInt(KEY, level).apply()
    }

    /** A panel's [base] share (e.g. 0.78) at the chosen level. */
    fun share(base: Float): Float = if (level == NORMAL) base else (base * FACTORS[level]).coerceIn(FLOOR, 1f)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("glcn_player_glass", Context.MODE_PRIVATE)
    private const val KEY = "level"
}
