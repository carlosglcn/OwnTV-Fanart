package tv.own.owntv.glcn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * glcn: entries the user hid from the Live category lists with a long press — Recently watched,
 * Favourites, a playlist tab. Only the list is affected, never the channels. "Show hidden" at the end
 * of the list brings them all back. Provider and custom categories keep their own hide (Customize).
 */
object HiddenCategories {
    private const val FILE = "glcn_hidden_categories"
    private const val KEY = "keys"

    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys
    @Volatile private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _keys.value = runCatching {
                context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(KEY, emptySet())?.toSet()
            }.getOrNull() ?: emptySet()
            loaded = true
        }
    }

    fun hide(context: Context, key: String) = set(context, _keys.value + key)

    fun showAll(context: Context) = set(context, emptySet())

    private fun set(context: Context, value: Set<String>) {
        _keys.value = value
        runCatching { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet(KEY, value).apply() }
    }

    fun playlistKey(id: Long) = "playlist:$id"
}
