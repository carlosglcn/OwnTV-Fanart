package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: how quickly the interface answers the remote. Everything that moves after a key — the focus box sliding to
 * its new place, a list or the Home page gliding a row, a menu opening — takes its time, and that reads as a slow
 * remote however smoothly it is drawn. "Brisk" (on by default) halves those times again: the app's pace ([Pace])
 * goes from 0.6 to 0.3 of each animation's written length, and the waits that are not animations (the menu opening
 * the section the focus rests on) are halved here. The slow ambient motion keeps its calm. Off gives back the pace
 * it had.
 */
object PacePref {
    var brisk by mutableStateOf(true)
        private set
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        brisk = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun set(value: Boolean) {
        brisk = value
        app?.getSharedPreferences(FILE, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY, value)?.apply()
        Pace.refresh()
    }

    /** A wait, in milliseconds. */
    fun ms(d: Long): Long = if (brisk) d / 2 else d

    private const val FILE = "glcn_pace"
    private const val KEY = "brisk"
}
