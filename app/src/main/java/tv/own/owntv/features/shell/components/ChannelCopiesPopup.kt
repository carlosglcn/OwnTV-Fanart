package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.stage.StagePopupRadio
import tv.own.owntv.ui.theme.mpx

/**
 * glcn: the copies of the channel on screen (FHD / HD / SD of the same playlist), best first. OK tunes
 * the one picked and the current one is marked; Back closes. Nothing but the choice.
 */
@Composable
fun ChannelCopiesPopup(
    copies: List<ChannelEntity>,
    playingId: Long?,
    onPick: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = remember { FocusRequester() }
    val playingIndex = copies.indexOfFirst { it.id == playingId }.coerceAtLeast(0)
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { start.requestFocus() } }
    StagePopup(onDismiss = onDismiss, title = stringResource(R.string.player_tool_quality), width = 640.mpx, scroll = false) {
        LazyColumn(Modifier.padding(top = 6.mpx), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
            itemsIndexed(copies, key = { _, c -> c.id }) { i, c ->
                val playing = c.id == playingId
                StagePopupOption(
                    title = c.name,
                    onClick = { if (playing) onDismiss() else onPick(c) },
                    modifier = if (i == playingIndex) Modifier.focusRequester(start) else Modifier,
                    leading = { focused -> StagePopupRadio(on = playing, focused = focused) },
                )
            }
        }
    }
}
