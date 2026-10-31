package tv.own.owntv.glcn

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tv.own.owntv.R

/**
 * glcn: when the app went down last time, the next launch shows what happened before anything else runs — the
 * recorded crash ([tv.own.owntv.core.util.CrashRecorder]) on a plain screen, with "Share report" and "Open the
 * app". A beta that crashes at start-up used to leave no way to export the report (that page is inside the app).
 * Plain Android views on purpose: nothing of the app's own UI, which may be what broke, is needed to draw it.
 * Shown once per crash.
 */
object CrashScreen {

    /** When the newest crash Android or the recorder knows of happened (0 = none). */
    private fun lastCrashAt(context: android.content.Context): Long {
        val file = java.io.File(java.io.File(context.filesDir, DIR), FILE)
        var at = if (file.exists() && file.length() > 0) file.lastModified() else 0L
        if (android.os.Build.VERSION.SDK_INT >= 30) runCatching {
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()?.let { e ->
                val bad = e.reason == android.app.ApplicationExitInfo.REASON_CRASH ||
                    e.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE ||
                    e.reason == android.app.ApplicationExitInfo.REASON_ANR
                if (bad && e.timestamp > at) at = e.timestamp
            }
        }
        return at
    }

    /** A crash (recorded, or one Android saw: native, freeze) since the last one shown, within a day. */
    fun pending(context: android.content.Context): Boolean = runCatching {
        val at = lastCrashAt(context)
        at > 0 && at > context.prefs().getLong(KEY_SEEN, 0L) &&
            System.currentTimeMillis() - at < MAX_AGE_MS
    }.getOrDefault(false)

    /** Draws the screen into [activity]; false when there is nothing to show. */
    fun show(activity: Activity): Boolean {
        val file = java.io.File(java.io.File(activity.filesDir, DIR), FILE)
        val stamp = lastCrashAt(activity)
        if (stamp <= 0L) return false
        val prefs = activity.prefs()
        val recorded = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
        val system = runCatching { tv.own.owntv.core.util.CrashRecorder.exitReasons(activity) }.getOrNull().orEmpty()
        val text = (recorded + "\n\n" + system).trim().takeIf { it.isNotBlank() } ?: return false
        val pad = (24 * activity.resources.displayMetrics.density).toInt()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 12, 14))
            setPadding(pad * 2, pad, pad * 2, pad)
        }
        root.addView(TextView(activity).apply {
            setText(R.string.glcn_crash_title); setTextColor(Color.WHITE); textSize = 26f; typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(activity).apply {
            setText(R.string.glcn_crash_body); setTextColor(Color.rgb(190, 200, 205)); textSize = 16f; setPadding(0, pad / 3, 0, pad / 2)
        })
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START }
        val open = Button(activity).apply {
            setText(R.string.glcn_crash_open)
            setOnClickListener {
                prefs.edit().putLong(KEY_SEEN, stamp).commit()
                activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.let {
                    activity.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
                activity.finish()
            }
        }
        val share = Button(activity).apply {
            setText(R.string.glcn_crash_share)
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                runCatching { activity.startActivity(Intent.createChooser(send, null)) }
            }
        }
        buttons.addView(share); buttons.addView(open)
        root.addView(buttons)
        root.addView(ScrollView(activity).apply {
            addView(TextView(activity).apply {
                this.text = text; setTextColor(Color.rgb(160, 175, 180)); textSize = 12f; typeface = Typeface.MONOSPACE
                setPadding(0, pad / 2, 0, 0); setTextIsSelectable(true)
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        activity.setContentView(root)
        share.requestFocus()
        return true
    }

    /** Read afresh each time: the report and the app run in two processes. */
    @Suppress("DEPRECATION")
    private fun android.content.Context.prefs() = getSharedPreferences(PREFS, android.content.Context.MODE_MULTI_PROCESS)

    /** The report's own process (`:crash`), where the application skips its start-up. */
    fun isCrashProcess(context: android.content.Context): Boolean {
        val name = if (android.os.Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName()
        else runCatching { java.io.File(PROC_CMDLINE).readText().trimEnd('\u0000') }.getOrDefault("")
        return name == context.packageName + CRASH_PROCESS
    }

    private const val PROC_CMDLINE = "/proc/self/cmdline"
    private const val CRASH_PROCESS = ":crash"
    private const val DIR = "diagnostics"
    private const val FILE = "last_crash.txt"
    private const val PREFS = "glcn_crash_screen"
    private const val KEY_SEEN = "seen"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
}

/** glcn: the plain screen [CrashScreen] draws, as its own activity so nothing of the app's start-up runs. */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (!CrashScreen.show(this)) finish()
    }
}
