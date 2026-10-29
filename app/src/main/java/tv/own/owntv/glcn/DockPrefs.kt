package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** glcn: the Dock's own choices, kept on the device. [doubleRow]: the next tab's cards peek under the row. */
object DockPrefs {
    var doubleRow by mutableStateOf(true)
        private set
    /** Trailers that play by themselves behind Home and the title pages (light mode and animations off stop them anyway). */
    var autoTrailers by mutableStateOf(true)
        private set
    /** "Page before watching": choosing a film opens its page (trailer, audio, subtitles) instead of playing at once. */
    var titlePage by mutableStateOf(true)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        doubleRow = prefs.getBoolean(DOUBLE_ROW, true)
        autoTrailers = prefs.getBoolean(AUTO_TRAILERS, true)
        titlePage = prefs.getBoolean(TITLE_PAGE, true)
    }

    fun setDoubleRow(context: Context, on: Boolean) {
        doubleRow = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DOUBLE_ROW, on).apply()
    }

    fun setAutoTrailers(context: Context, on: Boolean) {
        autoTrailers = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(AUTO_TRAILERS, on).apply()
    }

    fun setTitlePage(context: Context, on: Boolean) {
        titlePage = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(TITLE_PAGE, on).apply()
    }

    private const val PREFS = "glcn_dock"
    private const val DOUBLE_ROW = "double_row"
    private const val AUTO_TRAILERS = "auto_trailers"
    private const val TITLE_PAGE = "title_page"
}
