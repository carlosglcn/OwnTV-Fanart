package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.GlcnStyle
import tv.own.owntv.ui.stage.StageConfirm

/**
 * glcn: the new style (Brasa) is offered once to who already had the app — nothing changes unless they say so. A new
 * install picks its style in the set-up (where Brasa comes first), so it is never asked ([seen] there).
 */
object StyleInvite {
    fun pending(context: Context): Boolean = !prefs(context).getBoolean(KEY, false)
    fun seen(context: Context) { prefs(context).edit().putBoolean(KEY, true).apply() }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private const val FILE = "glcn_style_invite"
    private const val KEY = "brasa"
}

/**
 * glcn: the offer itself, a moment after the app is up and never over a playing picture ([quiet] false). "Try it"
 * applies the style at once; a second question then lets it be kept or put back as it was.
 */
@Composable
fun StyleInviteOffer(settings: SettingsRepository, quiet: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by settings.glcnStyle.collectAsStateWithLifecycle(initialValue = null)
    var ask by remember { mutableStateOf(false) }
    // What to go back to after trying it; null = not trying.
    var before by remember { mutableStateOf<GlcnStyle?>(null) }
    var tried by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!StyleInvite.pending(context)) return@LaunchedEffect
        delay(INVITE_DELAY_MS)
        if (current == GlcnStyle.BRASA) StyleInvite.seen(context) else ask = true
    }
    if (ask && quiet && !tried) StageConfirm(
        title = stringResource(R.string.glcn_style_invite_title),
        body = stringResource(R.string.glcn_style_invite_body),
        confirm = stringResource(R.string.glcn_style_invite_try),
        cancel = stringResource(R.string.glcn_star_later),
        eyebrow = stringResource(R.string.glcn_style_invite_eyebrow),
        onConfirm = {
            StyleInvite.seen(context)
            before = current; tried = true
            scope.launch { settings.applyGlcnStyle(GlcnStyle.BRASA) }
        },
        onCancel = { StyleInvite.seen(context); ask = false },
    )
    // Tried: keep it, or back to the style there was. Without a style to go back to, the first answer stands.
    val back = before
    if (tried && back != null) StageConfirm(
        title = stringResource(R.string.glcn_style_keep_title),
        body = stringResource(R.string.glcn_style_keep_body),
        confirm = stringResource(R.string.glcn_style_keep_yes),
        cancel = stringResource(R.string.glcn_style_keep_back, back.label()),
        onConfirm = { before = null; ask = false },
        onCancel = { before = null; ask = false; scope.launch { settings.applyGlcnStyle(back) } },
    )
}

private const val INVITE_DELAY_MS = 6_000L
