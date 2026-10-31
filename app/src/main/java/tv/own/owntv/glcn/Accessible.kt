package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: accessible mode, per profile — for someone who sees less well or finds the app busy (an older relative):
 * text a quarter larger, a thick white ring round whatever has focus, and calm motion (no zooming, sweeping, leaning
 * or drifting). Everything else works as always. Settings › Fanart › Accessible mode, for the profile in use.
 */
object Accessible {
    var on by mutableStateOf(false)
        private set
    private var profileId = -1L

    fun load(context: Context, profile: Long) {
        profileId = profile
        on = profile >= 0 && prefs(context).getBoolean(key(profile), false)
    }

    fun set(context: Context, value: Boolean) {
        if (profileId < 0) return
        on = value
        prefs(context).edit().putBoolean(key(profileId), value).apply()
    }

    /** The text size factor on top of the person's own. */
    val fontFactor: Float get() = if (on) FONT_FACTOR else 1f

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(profile: Long) = "on_$profile"

    private const val PREFS = "glcn_accessible"
    private const val FONT_FACTOR = 1.25f
}
