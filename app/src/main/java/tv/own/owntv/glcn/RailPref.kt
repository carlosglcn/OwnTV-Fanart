package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tv.own.owntv.R

/**
 * glcn: how the side menu is drawn. GLASS is the menu as it always was — a block of glass, the open section washed in
 * the accent, the focused row filled. FADE lets the glass fade into the picture, marks the open section with a bar in
 * the accent and outlines the focused row, which shows what waits there once the focus rests on it. COLUMN is names
 * alone, large, over the same veil. LABEL keeps the menu closed and names the focused icon in a label beside it.
 */
enum class RailShape(val label: Int) {
    GLASS(R.string.glcn_rail_glass),
    FADE(R.string.glcn_rail_fade),
    COLUMN(R.string.glcn_rail_column),
    LABEL(R.string.glcn_rail_label),
}

/** glcn: the chosen [RailShape]. Who already had the app keeps the glass; a new install opens on the fade. */
object RailPref {
    var shape by mutableStateOf(RailShape.GLASS)
        private set
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY, null)?.let { name -> RailShape.entries.firstOrNull { it.name == name } }
        shape = stored ?: (if (freshInstall(context)) RailShape.FADE else RailShape.GLASS).also {
            prefs.edit().putString(KEY, it.name).apply()
        }
    }

    fun set(value: RailShape) {
        shape = value
        app?.getSharedPreferences(FILE, Context.MODE_PRIVATE)?.edit()?.putString(KEY, value.name)?.apply()
    }

    fun next() = set(RailShape.entries[(shape.ordinal + 1) % RailShape.entries.size])

    private fun freshInstall(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    private const val FILE = "glcn_rail"
    private const val KEY = "shape"
}
