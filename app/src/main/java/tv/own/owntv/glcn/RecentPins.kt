package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: channels pinned to the front of Home's "Recent channels" (hold OK › Pin to the front). Kept by the channel's
 * stable customization key, so a playlist refresh that renumbers the channels keeps them pinned.
 */
object RecentPins {
    var keys by mutableStateOf<Set<String>>(emptySet())
        private set

    fun load(context: Context) {
        keys = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().toSet()
    }

    fun toggle(context: Context, key: String) {
        keys = if (key in keys) keys - key else keys + key
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, keys).apply()
    }

    private const val PREFS = "glcn_recent_pins"
    private const val KEY = "pinned"
}
