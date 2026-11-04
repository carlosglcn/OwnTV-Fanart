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
            }
        }
    }
}
