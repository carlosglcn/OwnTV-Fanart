package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: "light mode" for boxes with little memory (about 2 GB or less). The app simply asks less of the
 * device: the live preview waits for OK, no auto-drifting text, no image cross-fades, a smaller picture
 * cache. Never switched on by itself: the person turns it on in Settings, or says yes once when the
 * system reports memory really running out.
 */
object LowRam {
    private var forced by mutableStateOf<Boolean?>(null)

    /** Light mode is in effect: forced by the person, else what the device looks like. */
    val on: Boolean get() = forced == true

    fun detect(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        forced = if (prefs.contains(FORCED)) prefs.getBoolean(FORCED, false) else null
        previewOnOk = prefs.getBoolean(OK_PREVIEW, false)
    }

    fun force(context: Context, value: Boolean) {
        forced = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(FORCED, value).apply()
    }

    /** Live preview starts with OK instead of on every step; a second OK goes full screen. */
    var previewOnOk by mutableStateOf(false)
        private set

    fun setPreviewOnOk(context: Context, value: Boolean) {
        previewOnOk = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(OK_PREVIEW, value).apply()
    }

    /** The system said memory is critical and the person has not been asked yet: Home shows the question. */
    var suggest by mutableStateOf(false)
        private set

    fun onMemoryCritical(context: Context) {
        if (on || suggest) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(ASKED, false)) return
        suggest = true
    }

    fun answer(context: Context, yes: Boolean) {
        suggest = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ASKED, true).apply()
        if (yes) { force(context, true); setPreviewOnOk(context, true) }
    }

    const val PREFS = "glcn_lowram"
    private const val FORCED = "forced"
    private const val OK_PREVIEW = "preview_on_ok"
    private const val ASKED = "asked"
}

/** glcn: asked once, when memory runs out: "Switch on light mode?" */
@androidx.compose.runtime.Composable
fun LowRamPrompt() {
    if (!LowRam.suggest) return
    val context = androidx.compose.ui.platform.LocalContext.current
    tv.own.owntv.ui.stage.StageConfirm(
        title = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.glcn_lowram_prompt_title),
        body = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.glcn_lowram_prompt_body),
        confirm = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.glcn_lowram_prompt_yes),
        cancel = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.glcn_lowram_prompt_no),
        onConfirm = { LowRam.answer(context, true) },
        onCancel = { LowRam.answer(context, false) },
    )
}
