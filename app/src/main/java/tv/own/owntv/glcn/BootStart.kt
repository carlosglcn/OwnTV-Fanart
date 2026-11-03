package tv.own.owntv.glcn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings

/**
 * glcn (test feature): open OwnTV Fanart when the SHIELD starts — on a real start-up (BOOT_COMPLETED) and on waking
 * from sleep (the screen coming on, heard while the app's process is alive, which on the SHIELD it usually is).
 * Android lets a background app open a screen only with "Display over other apps" granted, asked once.
 * Only with the Labs switch and the "Open when the TV starts" choice both on.
 */
object BootStart {
    fun canStart(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun askPermission(context: Context) {
        val pkg = Uri.parse("package:" + context.packageName)
        val tries = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
        )
        for (i in tries) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    /** Brings the app up when it should: the switch on, the permission granted. */
    fun startIfWanted(context: Context) {
        Labs.ensure(context)
        if (!Labs.on || !Labs.autostart || !canStart(context)) return
        val launch = context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
            ?: context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
    }

    /** The waking half: the screen coming on is only heard by a receiver registered at run time. */
    fun listenForWake(context: Context) {
        val app = context.applicationContext
        runCatching {
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.action == Intent.ACTION_SCREEN_ON && !AppForeground.visible) startIfWanted(c)
                }
            }, IntentFilter(Intent.ACTION_SCREEN_ON))
        }
    }
}

/** glcn: whether one of the app's screens is showing (kept by the activity), so waking does not reopen it on itself. */
object AppForeground {
    @Volatile var visible = false
}

/** glcn: the start-up half of [BootStart]. */
class BootStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // glcn: not at the very moment the box starts — Android is still bringing everything up then, and the app
        // opening with it froze at times; a little later it opens into a settled system.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == QUICKBOOT) {
            val app = context.applicationContext
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ BootStart.startIfWanted(app) }, BOOT_DELAY_MS)
        }
    }

    private companion object {
        const val QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        const val BOOT_DELAY_MS = 40_000L
    }
}
