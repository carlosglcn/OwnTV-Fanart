package tv.own.owntv.glcn

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.features.shell.components.GLCN_REPO
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: "Support on GitHub" from About. A TV has no browser, so it is a QR to scan with the phone —
 * opened only when asked, never on its own.
 */
@Composable
internal fun GitHubStarPopup(onDismiss: () -> Unit) {
    val url = "https://$GLCN_REPO"
    val qr = remember(url) { CompanionLink.renderQr(url) }
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { close.requestFocus() } }
    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.glcn_star_title),
        body = stringResource(R.string.glcn_star_body),
        width = 820.mpx,
        buttons = {
            StageButton(stringResource(R.string.glcn_star_close), onClick = onDismiss, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(close))
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalAlignment = Alignment.CenterVertically) {
            if (qr != null) {
                Box(Modifier.size(190.mpx).clip(RoundedCornerShape(16.mpx)).background(Color.White).padding(10.mpx)) {
                    Image(bitmap = qr.asImageBitmap(), contentDescription = stringResource(R.string.glcn_star_qr), modifier = Modifier.fillMaxSize())
                }
            }
            Column {
                Text(GLCN_REPO, style = stageText(19, 700), color = StageColors.Text)
                Text(stringResource(R.string.glcn_star_scan), style = stageText(16, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 8.mpx))
                Text(stringResource(R.string.glcn_star_google), style = stageText(15, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 4.mpx))
            }
        }
    }
}

/**
 * glcn: the one-time GitHub star card — once per install, never on the first days or right after opening:
 * from the third day and the fifth start, after a minute of browsing with nothing playing. Either button,
 * or leaving it (it closes itself), and it never comes back.
 */
object GitHubStarPrompt {
    private const val PREFS = "glcn_star_prompt"
    private const val MIN_DAYS = 3
    private const val MIN_OPENS = 5
    const val BROWSE_MS = 60_000L
    const val SHOW_MS = 20_000L

    private fun prefs(c: android.content.Context) = c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    /** Counts this start and says whether the card is due (it then still waits for a calm minute). */
    fun launchAndCheck(c: android.content.Context): Boolean {
        val p = prefs(c)
        if (p.getBoolean("done", false)) return false
        val now = System.currentTimeMillis()
        val first = p.getLong("first", 0L).takeIf { it > 0 } ?: now.also { p.edit().putLong("first", it).apply() }
        val opens = p.getInt("opens", 0) + 1
        p.edit().putInt("opens", opens).apply()
        return opens >= MIN_OPENS && now - first >= MIN_DAYS * 24L * 3_600_000L
    }

    fun done(c: android.content.Context) { prefs(c).edit().putBoolean("done", true).apply() }
}

/** glcn: the card itself — small, bottom right, the QR and two buttons. */
@Composable
internal fun GitHubStarCard(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val url = "https://$GLCN_REPO"
    val qr = remember(url) { CompanionLink.renderQr(url) }
    val later = remember { FocusRequester() }
    val close = { GitHubStarPrompt.done(context); onClose() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); runCatching { later.requestFocus() } }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(GitHubStarPrompt.SHOW_MS); close() }
    androidx.activity.compose.BackHandler { close() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = close, stageLayout = true) {
        Box(Modifier.fillMaxSize().trapAllFocusExit(), contentAlignment = Alignment.BottomEnd) {
            Row(
                Modifier.padding(end = 56.mpx, bottom = 48.mpx).size(width = 760.mpx, height = 250.mpx)
                    .stageGlass(28.mpx, overContent = true).padding(22.mpx),
                horizontalArrangement = Arrangement.spacedBy(22.mpx), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (qr != null) {
                    Box(Modifier.size(196.mpx).clip(RoundedCornerShape(14.mpx)).background(Color.White).padding(9.mpx)) {
                        Image(bitmap = qr.asImageBitmap(), contentDescription = stringResource(R.string.glcn_star_qr), modifier = Modifier.fillMaxSize())
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.glcn_star_title), style = stageText(22, 800), color = StageColors.Text)
                    Text(stringResource(R.string.glcn_star_body), style = stageText(16, 500), color = StageColors.Body, modifier = Modifier.padding(top = 4.mpx))
                    Text(stringResource(R.string.glcn_star_scan) + " " + stringResource(R.string.glcn_star_google), style = stageText(14, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 4.mpx))
                    Row(Modifier.padding(top = 12.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                        StageButton(stringResource(R.string.glcn_star_later), onClick = close, height = 48.mpx, textSize = 17, modifier = Modifier.focusRequester(later))
                        StageButton(stringResource(R.string.glcn_star_done), onClick = close, height = 48.mpx, textSize = 17)
                    }
                }
            }
        }
    }
}


