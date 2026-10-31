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
    /** glcn: trailers with sound — asked for once (Vol +, Play/Pause, a page's sound button) and kept from then on. */
    var trailerSound by mutableStateOf(false)
        private set
    private var app: Context? = null

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        doubleRow = prefs.getBoolean(DOUBLE_ROW, true)
        autoTrailers = prefs.getBoolean(AUTO_TRAILERS, true)
        titlePage = prefs.getBoolean(TITLE_PAGE, true)
        trailerSound = prefs.getBoolean(TRAILER_SOUND, false)
        app = context.applicationContext
    }

    fun keepTrailerSound(on: Boolean) {
        if (trailerSound == on) return
        trailerSound = on
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(TRAILER_SOUND, on)?.apply()
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
    private const val TRAILER_SOUND = "trailer_sound"
}
