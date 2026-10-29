package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: "TV profiles" — a profile for someone who only wants to watch TV. It starts on the last channel, full screen,
 * and ▼ in the player raises the TV strip (groups, channels on air with their pictures, what starts soon). Kept on this
 * device by profile id. A profile made in the editor has no id yet: its name waits in [pendingName] until it appears.
 */
object TvProfiles {
    var ids by mutableStateOf<Set<Long>>(emptySet())
        private set
    var pendingName: String? = null
    /** The profile in use, set by the shell. */
    var active by mutableStateOf<Long?>(null)

    /** Whether the profile in use is a TV profile. */
    val activeIsTv: Boolean get() = isTv(active)

    fun isTv(profileId: Long?): Boolean = profileId != null && profileId in ids

    fun load(context: Context) {
        ids = prefs(context).getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun set(context: Context, profileId: Long, on: Boolean) {
        ids = if (on) ids + profileId else ids - profileId
        prefs(context).edit().putStringSet(KEY, ids.map { it.toString() }.toSet()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private const val PREFS = "glcn_tv_profiles"
    private const val KEY = "ids"
}
