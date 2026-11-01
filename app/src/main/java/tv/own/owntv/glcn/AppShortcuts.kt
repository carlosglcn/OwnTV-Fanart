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
    data class App(val pkg: String, val label: String, val banner: Drawable?)

    /** The packages chosen, in order. */
    val chosen = mutableStateListOf<String>()
    private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        prefs(context).getString(KEY, null)?.split(SEP)?.filter { it.isNotBlank() }?.let { chosen.addAll(it) }
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
                    runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) ?: ai.loadIcon(pm) }.getOrNull(),
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
