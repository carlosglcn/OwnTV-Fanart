package tv.own.owntv.glcn

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import tv.own.owntv.R
import tv.own.owntv.features.shell.components.RootRow
import tv.own.owntv.features.shell.components.TileTone
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * glcn: Settings › App › the Fanart screensaver — whether the TV uses it, and a way to Android's screensaver page to
 * pick it (an app cannot choose it by itself).
 */
@Composable
internal fun dreamRootRow(): RootRow {
    val context = LocalContext.current
    var ours by remember { mutableStateOf(isOurDream(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ours = isOurDream(context) }
    return RootRow(
        DREAM_ROW_KEY, TileTone.SECONDARY, OwnTVIcon.IMAGE,
        title = stringResource(R.string.glcn_dream_title),
        desc = stringResource(R.string.glcn_dream_desc),
        chip = stringResource(if (ours) R.string.glcn_dream_on else R.string.glcn_dream_choose),
        chipTone = if (ours) TileTone.PRIMARY else TileTone.SECONDARY,
        fanartTag = true,
        onClick = { openDreamSettings(context) },
    )
}

private fun isOurDream(context: Context): Boolean = runCatching {
    Settings.Secure.getString(context.contentResolver, SCREENSAVER_COMPONENTS)?.contains(context.packageName) == true
}.getOrDefault(false)

private fun openDreamSettings(context: Context) {
    val tries = listOf(Intent(Settings.ACTION_DREAM_SETTINGS), Intent(Settings.ACTION_DISPLAY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    for (i in tries) if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
}

private const val SCREENSAVER_COMPONENTS = "screensaver_components"
private const val DREAM_ROW_KEY = "glcn_dream"
