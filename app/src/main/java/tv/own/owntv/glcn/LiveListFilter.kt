package tv.own.owntv.glcn

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * glcn: the playlist chosen in the Live TV category sheets. It narrows Live TV only; the app-wide
 * playlist (top bar) is untouched, so Home, Movies and Series keep their own scope. Remembered across
 * restarts; a playlist no longer in scope counts as every playlist (see LiveViewModel's ctx).
 */
object LiveListFilter {
    private const val PREFS = "glcn_live_list"
    private const val KEY = "playlist"
    private var prefs: SharedPreferences? = null
    private val _id = MutableStateFlow(-1L)
    val id: StateFlow<Long> = _id

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _id.value = p.getLong(KEY, -1L)
    }

    fun set(id: Long) {
        val v = if (id > 0L) id else -1L
        _id.value = v
        prefs?.edit()?.putLong(KEY, v)?.apply()
    }
}
