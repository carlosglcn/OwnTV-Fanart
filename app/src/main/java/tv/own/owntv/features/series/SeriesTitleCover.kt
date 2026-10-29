package tv.own.owntv.features.series

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.shell.components.VodTitleInfo
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: a series' first page, the Apple TV way — the same as a film's: the picture (or its trailer) edge to edge, the
 * title's lettering and facts, one big button for the episode that comes next and round buttons for the rest. ▼ (or
 * the Episodes button) slides on to the seasons and episodes underneath; Back there comes back here.
 */
@Composable
internal fun SeriesTitleCover(
    info: VodTitleInfo,
    seriesId: Long,
    trailerKey: String?,
    playLabel: String?,
    onPlay: () -> Unit,
    favourite: Boolean,
    onToggleFavourite: () -> Unit,
    onTrailer: (() -> Unit)?,
    onMore: () -> Unit,
    onEpisodes: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    val episodesFocus = remember { FocusRequester() }
    var focusedLabel by remember { mutableStateOf<String?>(null) }
    val enter = remember { androidx.compose.animation.core.Animatable(if (tv.own.owntv.glcn.LowRam.on) 1f else 0f) }
    val enterSpec = tv.own.owntv.ui.theme.ownTvTween<Float>(COVER_ENTER_MS, androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
    LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }
    LaunchedEffect(playLabel != null) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { if (playLabel != null) playFocus.requestFocus() else episodesFocus.requestFocus() }
    }
    val pageFocus = androidx.compose.ui.platform.LocalFocusManager.current
    val page = tv.own.owntv.ui.theme.stageTone(Color(0xFF070B0E), tv.own.owntv.ui.theme.LocalStagePalette.current.page)
    Box(
        Modifier.fillMaxSize().background(page).trapAllFocusExit()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { onEpisodes(); true }
                else e.key == Key.DirectionUp
            }
            .onKeyEvent { e ->
                // The cover's own ◀ ▶: the shell's "◀ at the edge goes to the menu" must not see them.
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { pageFocus.moveFocus(FocusDirection.Left); true }
                    Key.DirectionRight -> { pageFocus.moveFocus(FocusDirection.Right); true }
                    else -> false
                }
            }
            .focusGroup(),
    ) {
        tv.own.owntv.ui.components.ArtTint(info.backdropUrl, Modifier.fillMaxSize(), strength = 0.7f)
        tv.own.owntv.features.shell.components.VodCinematicBackdrop(
            info.backdropUrl ?: info.posterUrl, series = true, Modifier.align(Alignment.TopEnd),
            trailer = trailerKey, trailerTitle = info.title, focusKey = seriesId,
        )
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }
                .background(Brush.verticalGradient(0.4f to Color.Transparent, 0.75f to page.copy(alpha = 0.8f), 1f to page.copy(alpha = 0.95f))),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 96.mpx, bottom = 64.mpx, end = 96.mpx)
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 40.mpx.toPx() },
            verticalArrangement = Arrangement.spacedBy(14.mpx),
        ) {
            tv.own.owntv.features.shell.components.VodHero(info, Modifier.width(1100.mpx), showCast = false, plotLines = 3)
            Row(
                Modifier.padding(top = 10.mpx).horizontalScroll(rememberScrollState()).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (playLabel != null) StageButton(
                    playLabel, onClick = onPlay, icon = OwnTVIcon.PLAY, iconFilled = true, height = 64.mpx, textSize = 21,
                    modifier = Modifier.focusRequester(playFocus).onFocusChanged { if (it.isFocused) focusedLabel = null },
                )
                @Composable
                fun roundButton(label: String, icon: OwnTVIcon, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) = StageButton(
                    null, onClick = onClick, icon = icon, iconFilled = filled, round = true, height = 64.mpx,
                    modifier = modifier.onFocusChanged { if (it.isFocused) focusedLabel = label },
                )
                roundButton(stringResource(R.string.content_episodes), OwnTVIcon.LIST, Modifier.focusRequester(episodesFocus), onClick = onEpisodes)
                onTrailer?.let { roundButton(stringResource(R.string.glcn_dock_trailer), OwnTVIcon.VIDEO, onClick = it) }
                // glcn: the trailer's sound, for remotes whose volume never reaches the box (a TV's own, by HDMI).
                if (trailerKey != null) roundButton(
                    stringResource(if (tv.own.owntv.features.home.CinemaTrailer.sound) R.string.glcn_trailer_sound_off else R.string.glcn_trailer_sound_on),
                    if (tv.own.owntv.features.home.CinemaTrailer.sound) OwnTVIcon.VOLUME_MUTE else OwnTVIcon.VOLUME_HIGH,
                ) { tv.own.owntv.features.home.CinemaTrailer.sound = !tv.own.owntv.features.home.CinemaTrailer.sound }
                roundButton(
                    stringResource(if (favourite) R.string.content_favorited else R.string.content_favorite),
                    OwnTVIcon.FAVORITE, filled = favourite, onClick = onToggleFavourite,
                )
                roundButton(stringResource(R.string.content_key_options), OwnTVIcon.MORE, onClick = onMore)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                focusedLabel?.let { Text(it, style = stageText(17, 600), color = StageColors.Body, maxLines = 1) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
                    tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.CHEVRON_DOWN, StageColors.Muted, Modifier.size(18.mpx))
                    Text(stringResource(R.string.content_episodes), style = stageText(16, 600), color = StageColors.Muted, maxLines = 1)
                }
            }
        }
    }
}

private const val COVER_ENTER_MS = 420
