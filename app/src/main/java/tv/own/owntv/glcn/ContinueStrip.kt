package tv.own.owntv.glcn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/** glcn: one card of the Keep watching strip. */
data class StripCard(
    val key: Long,
    val title: String,
    /** A wide picture (the title's backdrop), else its poster, cropped. */
    val image: String?,
    /** Under the title: the episode that is next ("S2 · E5") or how long is left. */
    val line: String?,
    /** How far it is seen, 0–1, or null for a show waiting on its next episode. */
    val progress: Float?,
)

/**
 * glcn: "Keep watching" above the posters of Films and Series (in All, while something is under way): wide cards,
 * lower than the posters so they tell apart at a glance, each with its picture, its bar and where it stands. ▼ goes on
 * to the posters, ▲ to the toolbar; OK plays (a film) or opens the show at its episode, hold OK opens the page.
 */
@Composable
fun ContinueStrip(
    cards: List<StripCard>,
    startPad: Dp,
    endPad: Dp,
    onFocus: (Int) -> Unit,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.home_row_keep_watching),
            style = stageText(20, 800), color = stageAccent.ink,
            modifier = Modifier.padding(start = startPad, bottom = 8.mpx),
        )
        LazyRow(
            state = rememberLazyListState(),
            horizontalArrangement = Arrangement.spacedBy(STRIP_GAP.mpx),
            contentPadding = PaddingValues(start = startPad, end = endPad, top = 10.mpx, bottom = 10.mpx),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(cards, key = { _, c -> c.key }) { i, c ->
                StagePoster(
                    title = c.title, line = c.line,
                    width = STRIP_W.mpx, height = STRIP_H.mpx,
                    progress = c.progress?.takeIf { it in 0.001f..0.999f },
                    onClick = { onClick(i) }, onLongClick = { onLongClick(i) },
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus(i) },
                ) {
                    if (c.image != null) AsyncImage(model = c.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** glcn: the strip's cards, wide and lower than the 196 × 294 posters. */
private const val STRIP_W = 300
private const val STRIP_H = 169
private const val STRIP_GAP = 24
