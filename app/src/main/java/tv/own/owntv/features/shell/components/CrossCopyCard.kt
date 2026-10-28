package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the channel on screen gives no picture and the only copy left is in another playlist. A card says so and
 * counts down; OK switches now, Back stays. Copies in the same playlist are switched to without asking.
 */
@Composable
fun CrossCopyCard(channel: String, playlist: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    var left by remember { mutableIntStateOf(CROSS_COPY_SECONDS) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        while (left > 0) { kotlinx.coroutines.delay(1_000); left-- }
        onAccept()
    }
    BackHandler { onDecline() }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 48.mpx, end = 48.mpx).width(560.mpx)
                .stageGlass(StageRadii.Sheet, overContent = true)
                .padding(22.mpx)
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    val ok = e.key == androidx.compose.ui.input.key.Key.DirectionCenter || e.key == androidx.compose.ui.input.key.Key.Enter
                    if (ok && e.type == KeyEventType.KeyUp) { onAccept(); true } else ok
                }
                .focusable(),
            verticalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            Text(stringResource(R.string.glcn_cross_copy_title), style = stageText(15, 700), color = stageAccent.accent, maxLines = 1)
            Text(
                stringResource(R.string.glcn_cross_copy_body, channel, playlist, left),
                style = stageText(20, 700), color = StageColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Row {
                StageKeyHints(
                    listOf(
                        stringResource(R.string.common_ok) to stringResource(R.string.glcn_cross_copy_now),
                        stringResource(R.string.common_back) to stringResource(R.string.glcn_cross_copy_stay),
                    ),
                    textSize = 15,
                )
            }
        }
    }
}

/** How long the card waits before switching by itself. */
private const val CROSS_COPY_SECONDS = 8
