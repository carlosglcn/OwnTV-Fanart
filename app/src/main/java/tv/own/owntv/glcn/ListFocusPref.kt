package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: how Live TV's channel list moves. Off (as it was): the highlight walks down the list and the list only
 * scrolls at its edges. On, "fixed focus": the highlight stays at one height and the channels pass under it, as in
 * TiviMate or Apple TV — near the list's first and last channels, where there is nothing left to scroll, it walks.
 */
object ListFocusPref {
    var fixed by mutableStateOf(false)
        private set
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        fixed = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(value: Boolean) {
        fixed = value
        app?.getSharedPreferences(FILE, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY, value)?.apply()
    }

    private const val FILE = "glcn_list_focus"
    private const val KEY = "fixed"
}

/** glcn: the fixed highlight's height in the list, from its top (a little above the middle, so more of what follows shows). */
const val FIXED_FOCUS_PIVOT = 0.36f
/** glcn: one spring for the list and for the highlight in fixed focus — moving alike, the highlight stands still. */
const val FIXED_FOCUS_STIFFNESS = 300f
const val FIXED_FOCUS_DAMPING = 0.9f
