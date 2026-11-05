package tv.own.owntv.glcn

import android.content.Context
import tv.own.owntv.core.settings.SettingsRepository

/** glcn: the menu style (side or top) as last shown, read at once on start so the first frame draws the right one. */
object NavStyleCache {
    private const val PREFS = "glcn_nav_style"
    private const val KEY = "style"

    fun read(c: Context): SettingsRepository.NavStyle =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { name -> SettingsRepository.NavStyle.entries.firstOrNull { it.name == name } }
            ?: SettingsRepository.NavStyle.DOCKED

    fun write(c: Context, style: SettingsRepository.NavStyle) {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString(KEY, null) != style.name) p.edit().putString(KEY, style.name).apply()
    }
}
