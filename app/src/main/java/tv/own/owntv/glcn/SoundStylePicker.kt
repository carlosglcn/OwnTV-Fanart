package tv.own.owntv.glcn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the way to choose the menu sounds — a list that plays each style as the focus reaches it, instead
 * of a chip that cycles through them. Leaving without choosing puts the current style back.
 */
@Composable
fun SoundStylePicker(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val names = listOf(
        R.string.glcn_ui_sound_style_classic, R.string.glcn_ui_sound_style_crystal, R.string.glcn_ui_sound_style_wood,
        R.string.glcn_ui_sound_style_bubble, R.string.glcn_ui_sound_style_soft, R.string.glcn_ui_sound_style_air,
    )
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    // Whatever the list leaves behind, the style the person had is the one that stays unless they pick.
    DisposableEffect(Unit) { onDispose { UiSounds.style = current } }
    StagePopup(onDismiss = onDismiss, title = stringResource(R.string.glcn_ui_sound_style_title), width = 700.mpx) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
            names.forEachIndexed { style, name ->
                StageSurface(
                    onClick = { onPick(style); onDismiss() },
                    radius = 14.mpx,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (style == current.coerceIn(0, names.lastIndex)) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { if (it.hasFocus) { UiSounds.style = style; UiSounds.preview() } },
                ) { _ ->
                    Text(
                        stringResource(name),
                        style = stageText(22, if (style == current) 800 else 600),
                        color = if (style == current) stageAccent.accent else StageColors.Text,
                        modifier = Modifier.padding(horizontal = 18.mpx, vertical = 12.mpx),
                    )
                }
            }
        }
    }
}
