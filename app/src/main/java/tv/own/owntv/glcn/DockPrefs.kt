package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** glcn: the Dock's own choices, kept on the device. [doubleRow]: the next tab's cards peek under the row. */
object DockPrefs {
    var doubleRow by mutableStateOf(true)
        private set

    fun load(context: Context) {
        doubleRow = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DOUBLE_ROW, true)
    }

    fun setDoubleRow(context: Context, on: Boolean) {
        doubleRow = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DOUBLE_ROW, on).apply()
    }

    private const val PREFS = "glcn_dock"
    private const val DOUBLE_ROW = "double_row"
}
