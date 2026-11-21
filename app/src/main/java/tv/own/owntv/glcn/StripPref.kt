package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: whether Films and Series show the "Keep watching" strip above their posters. Off until asked for (Settings ›
 * Layout): the category of the same name is always there.
 */
object StripPref {
    var on by mutableStateOf(false)
        private set
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        on = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(value: Boolean) {
        on = value
        app?.getSharedPreferences(FILE, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY, value)?.apply()
    }

    private const val FILE = "glcn_strip"
    private const val KEY = "shown"
}
