package tv.own.owntv.glcn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.trakt.Trakt
import tv.own.owntv.core.trakt.TraktSync
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: Settings › Sources › Trakt. Signed out: the device sign-in — a code to type at trakt.tv/activate on a
 * phone, checked every few seconds until the account says yes. Signed in: the account, "Sync now" and "Sign out".
 */
@Composable
fun TraktDialog(profileId: Long, onDismiss: () -> Unit) {
    val version by Trakt.version.collectAsState()
    val scope = rememberCoroutineScope()
    if (Trakt.connected(profileId)) {
        version // read: a sign-out redraws the dialog as the sign-in
        var syncing by remember { mutableStateOf(false) }
        StagePopup(
            onDismiss = onDismiss,
            title = stringResource(R.string.glcn_trakt_title),
            body = stringResource(R.string.glcn_trakt_connected_as, Trakt.userName(profileId) ?: "Trakt"),
        ) {
            StagePopupOption(
                title = stringResource(if (syncing) R.string.glcn_trakt_syncing else R.string.glcn_trakt_sync_now),
                subtitle = stringResource(R.string.glcn_trakt_sync_line),
                enabled = !syncing,
                onClick = {
                    syncing = true
                    scope.launch { runCatching { TraktSync.refresh(profileId, force = true) }; syncing = false }
                },
            )
            StagePopupOption(
                title = stringResource(R.string.glcn_trakt_sign_out),
                danger = true,
                onClick = { Trakt.disconnect(profileId); onDismiss() },
            )
            TraktNotice()
        }
        return
    }
    var code by remember { mutableStateOf<Trakt.DeviceCode?>(null) }
    var state by remember { mutableStateOf(SignIn.WAITING) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        state = SignIn.WAITING
        val c = Trakt.startDevice() ?: run { state = SignIn.FAILED; return@LaunchedEffect }
        code = c
        var interval = c.intervalS.coerceAtLeast(1) * 1000L
        val deadline = System.currentTimeMillis() + c.expiresInS * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            when (Trakt.pollDevice(profileId, c)) {
                Trakt.Poll.Pending -> Unit
                Trakt.Poll.SlowDown -> interval += 1000L
                Trakt.Poll.Done -> {
                    state = SignIn.DONE
                    TraktSync.refreshLater(profileId)
                    return@LaunchedEffect
                }
                Trakt.Poll.Expired -> { state = SignIn.EXPIRED; return@LaunchedEffect }
                Trakt.Poll.Denied -> { state = SignIn.DENIED; return@LaunchedEffect }
                is Trakt.Poll.Failed -> Unit
            }
        }
        state = SignIn.EXPIRED
    }
    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.glcn_trakt_title),
        body = stringResource(
            when (state) {
                SignIn.WAITING -> R.string.glcn_trakt_sign_in_line
                SignIn.DONE -> R.string.glcn_trakt_signed_in
                SignIn.EXPIRED -> R.string.glcn_trakt_expired
                SignIn.DENIED -> R.string.glcn_trakt_denied
                SignIn.FAILED -> R.string.glcn_trakt_failed
            },
        ),
    ) {
        val c = code
        if (state == SignIn.WAITING && c != null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.mpx), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(c.url.removePrefix("https://"), style = stageText(22, 600), color = StageColors.Muted, textAlign = TextAlign.Center)
                Text(c.userCode, style = stageText(56, 800), color = stageAccent.accent, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.mpx))
            }
        }
        TraktNotice()
        when (state) {
            SignIn.DONE -> StagePopupOption(title = stringResource(R.string.glcn_trakt_close), onClick = onDismiss)
            SignIn.EXPIRED, SignIn.DENIED, SignIn.FAILED ->
                StagePopupOption(title = stringResource(R.string.glcn_trakt_retry), onClick = { code = null; attempt++ })
            SignIn.WAITING -> StagePopupOption(title = stringResource(R.string.glcn_trakt_cancel), onClick = onDismiss)
        }
    }
}

/** Trakt's required notice, small, under the dialog's options. */
@Composable
private fun TraktNotice() {
    Text(
        stringResource(R.string.glcn_trakt_notice), style = stageText(15, 500), color = StageColors.Dim, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.mpx),
    )
}

private enum class SignIn { WAITING, DONE, EXPIRED, DENIED, FAILED }
