package tv.own.owntv.glcn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.theme.GLCN_BACKDROP_FANART
import tv.own.owntv.core.theme.GLCN_BACKDROP_PLAIN
import tv.own.owntv.core.theme.GLCN_BACKDROP_THEME
import tv.own.owntv.core.theme.GLCN_MODE_AUTO
import tv.own.owntv.core.theme.GLCN_MODE_DARK
import tv.own.owntv.core.theme.GLCN_MODE_LIGHT
import tv.own.owntv.core.theme.GlcnStyle
import tv.own.owntv.core.theme.GlcnTheme
import tv.own.owntv.ui.stage.StageGroupLabel
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: Appearance — three choices and nothing else in sight: the theme (eight, each with a night and a day side),
 * the mode (automatic, dark, light) and the background (plain, the theme's own, the picture in focus). Changes show at
 * once behind the popup. What a theme does not need changed — accent, focus, sounds — waits under Personalise, and the
 * styles of earlier versions under their own row, for whoever had one.
 */
@Composable
fun LookPopup(
    style: GlcnStyle?,
    mode: Int?,
    backdrop: Int,
    onTheme: (GlcnTheme) -> Unit,
    onMode: (Int) -> Unit,
    onBackdrop: (Int) -> Unit,
    onAccent: () -> Unit,
    onFocus: () -> Unit,
    onSounds: () -> Unit,
    onOlder: () -> Unit,
    onGlass: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = GlcnTheme.of(style)
    val first = remember { FocusRequester() }
    var customise by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    // The side each sketch shows: the one in use now, so the tiles match the screen behind them.
    val light = style?.light == true
    StagePopup(onDismiss = onDismiss, title = stringResource(R.string.glcn_look_title), body = stringResource(R.string.glcn_look_desc), width = 1500.mpx) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.mpx)) {
            StageGroupLabel("1 · " + stringResource(R.string.glcn_look_theme), sheet = true)
            GlcnTheme.entries.chunked(THEMES_PER_ROW).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    row.forEachIndexed { c, theme ->
                        val picked = theme == current
                        val idx = r * THEMES_PER_ROW + c
                        StageSurface(
                            onClick = { onTheme(theme) },
                            radius = 18.mpx,
                            modifier = Modifier.weight(1f).staggerAt(idx, step = 40)
                                .then(if (picked || (current == null && idx == 0)) Modifier.focusRequester(first) else Modifier),
                        ) { focused ->
                            Column(Modifier.padding(10.mpx), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                                StyleSketch(theme.style(light), focused, Modifier.fillMaxWidth().height(104.mpx))
                                Text(
                                    (if (picked) "✓ " else String()) + theme.label(), style = stageText(18, 800), maxLines = 1,
                                    color = if (focused) stageAccent.onAccent else if (picked) stageAccent.accent else StageColors.Text,
                                )
                            }
                        }
                    }
                    repeat(THEMES_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 6.mpx), horizontalArrangement = Arrangement.spacedBy(28.mpx)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                    StageGroupLabel("2 · " + stringResource(R.string.glcn_look_mode), sheet = true)
                    Segments(
                        listOf(
                            GLCN_MODE_AUTO to stringResource(R.string.glcn_look_mode_auto),
                            GLCN_MODE_DARK to stringResource(R.string.glcn_look_mode_dark),
                            GLCN_MODE_LIGHT to stringResource(R.string.glcn_look_mode_light),
                        ),
                        // Never chosen: the side in use is the mode it is in.
                        mode ?: if (light) GLCN_MODE_LIGHT else GLCN_MODE_DARK,
                        onMode,
                    )
                    Text(stringResource(R.string.glcn_look_mode_auto_desc), style = stageText(14, 500), color = StageColors.Dim)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                    StageGroupLabel("3 · " + stringResource(R.string.glcn_look_backdrop), sheet = true)
                    Segments(
                        listOf(
                            GLCN_BACKDROP_PLAIN to stringResource(R.string.glcn_look_backdrop_plain),
                            GLCN_BACKDROP_THEME to stringResource(R.string.glcn_look_backdrop_theme),
                            GLCN_BACKDROP_FANART to stringResource(R.string.glcn_look_backdrop_fanart),
                        ),
                        backdrop,
                        onBackdrop,
                    )
                    Text(stringResource(R.string.glcn_look_backdrop_fanart_desc), style = stageText(14, 500), color = StageColors.Dim)
                }
            }

            Wide(stringResource(R.string.glcn_look_customise), stringResource(R.string.glcn_look_customise_desc), open = customise) { customise = !customise }
            if (customise) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    Small(stringResource(R.string.settings_accent), Modifier.weight(1f)) { onDismiss(); onAccent() }
                    Small(stringResource(R.string.settings_focus_highlight), Modifier.weight(1f)) { onDismiss(); onFocus() }
                    Small(stringResource(R.string.glcn_ui_sound_style_title), Modifier.weight(1f)) { onDismiss(); onSounds() }
                    // The glass's transparency lives on its own page (Glass & background); this is the way there.
                    Small(stringResource(R.string.glcn_look_glass), Modifier.weight(1f)) { onGlass() }
                    if (current != null) Small(stringResource(R.string.glcn_look_reset), Modifier.weight(1.4f)) { onTheme(current) }
                }
            }
            Wide(stringResource(R.string.glcn_look_older), null, open = false) { onDismiss(); onOlder() }
        }
    }
}

private const val THEMES_PER_ROW = 4

/** A row of choices: the chosen one filled, the rest plain; OK chooses. */
@Composable
private fun Segments(items: List<Pair<Int, String>>, chosen: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
        items.forEach { (value, label) ->
            val on = value == chosen
            StageSurface(onClick = { onPick(value) }, radius = 22.mpx, modifier = Modifier.weight(1f)) { focused ->
                Text(
                    (if (on) "● " else String()) + label, style = stageText(17, if (on) 800 else 600), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (focused) stageAccent.onAccent else if (on) stageAccent.accent else StageColors.Text,
                    modifier = Modifier.padding(horizontal = 16.mpx, vertical = 11.mpx),
                )
            }
        }
    }
}

@Composable
private fun Wide(title: String, desc: String?, open: Boolean, onClick: () -> Unit) {
    StageSurface(onClick = onClick, radius = 14.mpx, modifier = Modifier.fillMaxWidth()) { focused ->
        Row(Modifier.padding(horizontal = 18.mpx, vertical = 12.mpx)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = stageText(18, 800), color = if (focused) stageAccent.onAccent else StageColors.Text)
                desc?.let {
                    Text(it, style = stageText(14, 500), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (focused) stageAccent.onAccent.copy(alpha = 0.9f) else StageColors.Dim)
                }
            }
            Text(if (open) "▾" else "›", style = stageText(22, 700), color = if (focused) stageAccent.onAccent else stageAccent.accent)
        }
    }
}

@Composable
private fun Small(title: String, modifier: Modifier, onClick: () -> Unit) {
    StageSurface(onClick = onClick, radius = 14.mpx, modifier = modifier) { focused ->
        Text(
            title, style = stageText(16, 700), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (focused) stageAccent.onAccent else StageColors.Text,
            modifier = Modifier.padding(horizontal = 16.mpx, vertical = 12.mpx),
        )
    }
}
