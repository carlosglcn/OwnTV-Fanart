package tv.own.owntv.features.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.GlcnStyle
import tv.own.owntv.glcn.kind
import tv.own.owntv.glcn.label
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: first-run "Choose your style". Moving onto a style applies it at once, so the whole screen —
 * this step included — shows it before OK settles it.
 */
@Composable
internal fun StyleStep(onNext: () -> Unit, onBack: () -> Unit) {
    val settings: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val current by settings.glcnStyle.collectAsStateWithLifecycle(initialValue = null)
    val first = rememberFirstFocus()
    WizardFrame(
        step = WizardStep.DISPLAY,
        title = stringResource(R.string.glcn_setup_style_title),
        sub = stringResource(R.string.glcn_style_desc),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = WizardAction(stringResource(R.string.setup_continue), onNext),
        width = 1500.mpx,
    ) {
        GlcnStyle.entries.chunked(5).forEachIndexed { row, styles ->
            Row(Modifier.fillMaxWidth().padding(top = if (row == 0) 8.mpx else 16.mpx), horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                styles.forEachIndexed { i, st ->
                    StageSurface(
                        onClick = { scope.launch { settings.applyGlcnStyle(st) }; onNext() },
                        radius = 18.mpx,
                        focusStyle = StageFocus.FX,
                        idle = Modifier.background(StageColors.Ink.copy(alpha = 0.05f), RoundedCornerShape(18.mpx)),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { if (it.isFocused && current != st) scope.launch { settings.applyGlcnStyle(st) } }
                            .then(if (row == 0 && i == 0) Modifier.focusRequester(first) else Modifier),
                    ) { _ ->
                        Column(Modifier.padding(14.mpx)) {
                            StyleThumb(st)
                            Text(st.label(), style = stageText(20, 800), color = StageColors.Text, maxLines = 1, modifier = Modifier.padding(top = 10.mpx))
                            Text(st.kind(), style = stageText(14, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** A small picture of a style: its page, a rail, a billboard and three cards in its accent. */
@Composable
private fun StyleThumb(st: GlcnStyle) {
    val accent = Color(android.graphics.Color.parseColor(st.accentHex))
    val card = Color(st.glassTint)
    Box(Modifier.fillMaxWidth().height(120.mpx).background(Color(st.page), RoundedCornerShape(12.mpx)).padding(10.mpx)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
            Box(Modifier.width(12.mpx).height(80.mpx).background(card, RoundedCornerShape(6.mpx)))
            Column(verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                Box(Modifier.fillMaxWidth().height(46.mpx).background(Color(0xFF1C4A2C), RoundedCornerShape(8.mpx))) {
                    Box(Modifier.padding(8.mpx).size(width = 34.mpx, height = 10.mpx).background(accent, CircleShape))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
                    repeat(3) { Box(Modifier.weight(1f).height(36.mpx).background(card, RoundedCornerShape(6.mpx))) }
                }
            }
        }
    }
}

/** glcn: first-run "How do you want Home?" — Classic, Cinema, Panel or Dock. */
@Composable
internal fun HomeLayoutStep(onNext: () -> Unit, onBack: () -> Unit) {
    val settings: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val first = rememberFirstFocus()
    fun pick(mode: Int) { scope.launch { settings.setHomeBackdropStyle(mode) }; onNext() }
    WizardFrame(
        step = WizardStep.DISPLAY,
        title = stringResource(R.string.glcn_setup_home_title),
        sub = stringResource(R.string.glcn_setup_home_sub),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = null,
    ) {
        WizardCards(
            listOf(
                WizardCard(OwnTVIcon.HOME, stringResource(R.string.glcn_home_backdrop_classic), stringResource(R.string.glcn_setup_home_classic)) { pick(0) },
                WizardCard(OwnTVIcon.MOVIES, stringResource(R.string.glcn_home_backdrop_cinema), stringResource(R.string.glcn_setup_home_cinema)) { pick(1) },
                WizardCard(OwnTVIcon.LIST_GRID, stringResource(R.string.glcn_home_backdrop_panel), stringResource(R.string.glcn_setup_home_panel)) { pick(2) },
                WizardCard(OwnTVIcon.LIVE_TV, stringResource(R.string.glcn_home_backdrop_dock), stringResource(R.string.glcn_setup_home_dock)) { pick(3) },
            ),
            first,
        )
    }
}
