package tv.own.owntv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * "Set TMDB name" in the Stage design: the user types the exact title (+ optional year)
 * to search TMDB under, overriding the auto-normalized provider title — the escape hatch when a match is
 * wrong or was negative-cached for 7 days; saving forces a fresh re-resolve.
 *
 * A glass panel 760 wide: the eyebrow "TMDB", the title, the explanation, Title and Year side by side,
 * then Cancel · Clear ([hasOverride] only) · Save (tinted; does nothing while the title is blank).
 * Centred; while the TV keyboard is up it moves to the top and is capped to the room above the keys,
 * so the field being typed in stays visible (it used to shrink to its heading).
 */
@Composable
fun SetTmdbNameDialog(
    initialTitle: String,
    initialYear: Int?,
    hasOverride: Boolean,
    initialTmdbId: Int? = null,
    onSave: (title: String, year: Int?, tmdbId: Int?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        var title by remember { mutableStateOf(initialTitle) }
        var year by remember { mutableStateOf(initialYear?.toString() ?: "") }
        var tmdbId by remember { mutableStateOf(initialTmdbId?.toString().orEmpty()) }
        val titleFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { titleFocus.requestFocus() } }
        BackHandler { onDismiss() }
        val ime = LocalTvImeMetrics.current
        val density = LocalDensity.current
        BoxWithConstraints(
            Modifier.fillMaxSize().background(StageColors.Scrim).trapAllFocusExit().focusGroup(),
            contentAlignment = if (ime.visible) Alignment.TopCenter else Alignment.Center,
        ) {
            val top = 40.mpx
            // Room above the keyboard, less a gap; the whole height when it is down.
            val room = if (ime.visible) with(density) { ime.keyboardTopPx.toDp() } - top - 24.mpx else maxHeight - top * 2
            Column(
                Modifier
                    .padding(top = if (ime.visible) top else 0.mpx)
                    .width(760.mpx)
                    .heightIn(max = room.coerceAtLeast(120.mpx))
                    .stageGlass(30.mpx, overContent = true)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 36.mpx, vertical = 32.mpx),
            ) {
                Text(
                    "TMDB", style = stageText(14, 800, 0.12.em), color = stageAccent.accent,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.setup_tmdb_name), style = stageText(34, 800, (-0.5).mpxSp), color = StageColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx),
                )
                Text(
                    stringResource(R.string.setup_tmdb_description), style = stageText(18, 400).copy(lineHeight = (18 * 1.45f).mpxSp),
                    color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx, bottom = 24.mpx),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    tv.own.owntv.features.settings.StageFieldRow(
                        icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                        label = stringResource(R.string.common_title),
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.weight(1f).focusRequester(titleFocus),
                    )
                    tv.own.owntv.features.settings.StageFieldRow(
                        icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                        label = stringResource(R.string.setup_year_optional),
                        value = year,
                        onValueChange = { s -> year = s.filter { it.isDigit() }.take(4) },
                        modifier = Modifier.width(160.mpx),
                        keyboardType = KeyboardType.Number,
                    )
                    // glcn: the exact TMDB id, when the name and year find the wrong film.
                    tv.own.owntv.features.settings.StageFieldRow(
                        icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                        label = stringResource(R.string.setup_tmdb_id_optional),
                        value = tmdbId,
                        onValueChange = { s -> tmdbId = s.filter { it.isDigit() }.take(9) },
                        modifier = Modifier.width(210.mpx),
                        keyboardType = KeyboardType.Number,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 28.mpx),
                    horizontalArrangement = Arrangement.spacedBy(14.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
                    if (hasOverride) StageButton(stringResource(R.string.common_clear), onClick = onClear, height = 56.mpx, textSize = 19)
                    Spacer(Modifier.weight(1f))
                    StageButton(
                        stringResource(R.string.common_save),
                        onClick = { if (title.isNotBlank()) onSave(title.trim(), year.trim().toIntOrNull(), tmdbId.trim().toIntOrNull()) },
                        height = 56.mpx, textSize = 19, tinted = true,
                    )
                }
            }
        }
    }
}
