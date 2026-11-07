package tv.own.owntv.glcn

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateListOf

/**
 * glcn: the TV's other apps (YouTube, Plex, Kodi…), for the "Apps" row on TV Home — only the ones the person picked,
 * in the order picked. Read from the TV's launcher list (apps with a TV banner); kept per device by package name.
 */
object AppShortcuts {
    /** [banner] is the app's TV banner, or null when it has none — then [icon] is drawn on its own colour. */
    data class App(val pkg: String, val label: String, val banner: Drawable?, val icon: Drawable? = null, val group: Int = GROUP_OTHER)

    // glcn: Android's own category for each app (what the maker declared), folded into four groups.
    const val GROUP_VIDEO = 0
    const val GROUP_MUSIC = 1
    const val GROUP_GAMES = 2
    const val GROUP_OTHER = 3

    private fun groupOf(info: android.content.pm.ApplicationInfo): Int = when {
        info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME -> GROUP_GAMES
        info.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> GROUP_VIDEO
        info.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> GROUP_MUSIC
        @Suppress("DEPRECATION") (info.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0 -> GROUP_GAMES
        else -> GROUP_OTHER
    }

    /** glcn: the apps opened from here, the latest first (kept per device). */
    val recent = mutableStateListOf<String>()
    private const val RECENT_KEY = "recent"
    private const val RECENT_MAX = 8

    /** The packages chosen, in order. */
    val chosen = mutableStateListOf<String>()
    private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        prefs(context).getString(KEY, null)?.split(SEP)?.filter { it.isNotBlank() }?.let { chosen.addAll(it) }
        prefs(context).getString(RECENT_KEY, null)?.split(SEP)?.filter { it.isNotBlank() }?.let { recent.addAll(it) }
    }

    private fun noteOpened(context: Context, pkg: String) {
        ensure(context)
        recent.remove(pkg)
        recent.add(0, pkg)
        while (recent.size > RECENT_MAX) recent.removeAt(recent.lastIndex)
        prefs(context).edit().putString(RECENT_KEY, recent.joinToString(SEP)).apply()
    }

    /** Every app the TV's launcher shows, but this one, by name. */
    fun installed(context: Context): List<App> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { ai ->
                App(
                    ai.packageName,
                    ai.loadLabel(pm).toString(),
                    runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull(),
                    runCatching { ai.loadIcon(pm) }.getOrNull(),
                    groupOf(ai.applicationInfo),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun toggle(context: Context, pkg: String) {
        ensure(context)
        if (pkg in chosen) chosen.remove(pkg) else chosen.add(pkg)
        save(context)
    }

    /** Moves [pkg] one place left (-1) or right (+1) in the chosen order. */
    fun move(context: Context, pkg: String, delta: Int) {
        val i = chosen.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in chosen.indices) return
        chosen.removeAt(i)
        chosen.add(j, pkg)
        save(context)
    }

    /** The app's own page in Android's settings (storage, permissions, uninstall). */
    fun openInfo(context: Context, pkg: String) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun open(context: Context, pkg: String): Boolean = runCatching {
        val pm = context.packageManager
        val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        noteOpened(context, pkg)
        true
    }.getOrDefault(false)

    private fun save(context: Context) {
        prefs(context).edit().putString(KEY, chosen.joinToString(SEP)).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private const val PREFS = "glcn_app_shortcuts"
    private const val KEY = "apps"
    private const val SEP = ","
}
