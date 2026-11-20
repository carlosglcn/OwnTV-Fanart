package tv.own.owntv.glcn

import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.Text
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

/**
 * glcn: the Runway's page under its toolbar — the strip and the row of posters do not both fit there, and drawn one
 * over the other they overlapped. They are one page that moves: on the strip, the strip is whole and the posters'
 * tops show below; on the posters, the row is whole and the strip's foot shows above. What is cut fades out.
 */
fun Modifier.runwayPage(scroll: androidx.compose.foundation.ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = RUNWAY_FADE.mpx.toPx()
        if (scroll.value > 0) drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black), startY = 0f, endY = fade),
            size = androidx.compose.ui.geometry.Size(size.width, fade), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
        if (scroll.value < scroll.maxValue) drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.Transparent), startY = size.height - fade, endY = size.height),
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade), size = androidx.compose.ui.geometry.Size(size.width, fade),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
    .clipToBounds()
    .verticalScroll(scroll, enabled = false)

/** glcn: the page follows the focus — up to the strip, down to the posters. */
@Composable
fun RunwayPageFollow(scroll: androidx.compose.foundation.ScrollState, onStrip: Boolean) {
    androidx.compose.runtime.LaunchedEffect(onStrip, scroll.maxValue) {
        val to = if (onStrip) 0 else scroll.maxValue
        if (LowRam.on || tv.own.owntv.ui.stage.GlcnMotion.off) scroll.scrollTo(to)
        else scroll.animateScrollTo(to, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = RUNWAY_PAGE_STIFFNESS))
    }
}

private const val RUNWAY_FADE = 46
private const val RUNWAY_PAGE_STIFFNESS = 260f

/** glcn: the strip's cards, wide and lower than the 196 × 294 posters. */
private const val STRIP_W = 300
private const val STRIP_H = 169
private const val STRIP_GAP = 24
