package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tv.own.owntv.core.sync.EmbyGroups

/** glcn: the Emby / Jellyfin grouping chosen in Settings › Fanart, kept on the device and handed to core's sync. */
object EmbyGroupsPref {
    var mode by mutableStateOf(EmbyGroups.Mode.GENRE)
        private set

    fun load(context: Context) {
        mode = runCatching { EmbyGroups.Mode.valueOf(prefs(context).getString(KEY, null) ?: "") }.getOrDefault(EmbyGroups.Mode.GENRE)
        EmbyGroups.mode = mode
    }

    /** Genre → platform → films and series → genre. */
    fun cycle(context: Context) {
        val all = EmbyGroups.Mode.entries
        mode = all[(all.indexOf(mode) + 1) % all.size]
        EmbyGroups.mode = mode
        prefs(context).edit().putString(KEY, mode.name).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private const val PREFS = "glcn_emby_groups"
    private const val KEY = "mode"
}
