package tv.own.owntv.glcn

import android.content.Context
import tv.own.owntv.core.settings.SettingsRepository

/**
 * glcn: the look a new install opens on — the menu as a top bar ("Island", see TopBarPref), Home in Cinema, Films and
 * Series in Runway. Written once, on the first run of a fresh install only: an update (the app was there before) is
 * marked done without touching anything, so whoever already had the app keeps every setting as it was, chosen or not.
 */
object FreshDefaults {
    suspend fun apply(context: Context, settings: SettingsRepository) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return
        if (freshInstall(context)) runCatching {
            settings.setNavStyle(SettingsRepository.NavStyle.TOP)
            settings.setHomeBackdropStyle(HOME_CINEMA)
            settings.setVodLayout(SettingsRepository.VodLayout.RUNWAY)
        }
        prefs.edit().putBoolean(KEY, true).apply()
    }

    /** Never updated: installed and not yet replaced by a newer build. */
    private fun freshInstall(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    private const val HOME_CINEMA = 1
    private const val FILE = "glcn_fresh_defaults"
    private const val KEY = "done"
}
