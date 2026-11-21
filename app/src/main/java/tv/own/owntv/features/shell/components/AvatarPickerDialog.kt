package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.ProfileIcon
import tv.own.owntv.ui.components.OwnTVAvatars
import tv.own.owntv.ui.components.longPressMenuGuard

/** Full-screen avatar picker: a grid of the preset cartoon avatars. Picking one applies & closes. */
@Composable
fun AvatarPickerDialog(
    selectedId: Int,
    onSelect: (Int) -> Unit,
    // A picture of the user's own, when this profile has one — it takes the place of the drawn tile
    // wherever the avatar appears. Blank means none, and the two callbacks below are absent when the
    // host has nowhere to pick a picture from.
    customPath: String = "",
    onPickCustom: (() -> Unit)? = null,
    onClearCustom: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }

    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_avatar_picker_title),
        width = 760.mpx,
        modifier = Modifier.longPressMenuGuard(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
            // Phase 7 — "no avatar" option showing the Rank 1 ProfileIcon (ID -1)
            AvatarTile(selected = selectedId == -1, onClick = { onSelect(-1); onDismiss() }, focus = selectedFocus.takeIf { selectedId == -1 }) {
                ProfileIcon(color = stageAccent.ink, modifier = Modifier.size(56.mpx))
            }

            // A picture of your own, alongside the drawn set. Same two ways in as the background
            // image — a file on this device, or a photo sent from a phone — so there is one idea to
            // learn rather than two; the host supplies that chooser.
            if (onPickCustom != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.mpx), verticalAlignment = Alignment.CenterVertically) {
                    AvatarTile(selected = customPath.isNotBlank(), onClick = onPickCustom) {
                        if (customPath.isNotBlank()) {
                            OwnTVAvatar(avatarId = selectedId, imagePath = customPath, modifier = Modifier.size(90.mpx))
                        } else {
                            ProfileIcon(color = StageColors.Muted, modifier = Modifier.size(56.mpx))
                        }
                    }
                    Text(
                        text = stringResource(R.string.profiles_avatar_own_picture),
                        style = stageText(19, 600),
                        color = StageColors.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    if (customPath.isNotBlank() && onClearCustom != null) {
                        tv.own.owntv.ui.stage.StagePopupButton(
                            stringResource(R.string.common_clear),
                            onClick = onClearCustom,
                            primary = false,
                        )
                    }
                }
            }

            // glcn: 48 avatars in two groups — Characters, then Objects — six to a row in smaller tiles.
            listOf(
                R.string.glcn_avatar_characters to (OwnTVAvatars.OBJECTS until OwnTVAvatars.COUNT).toList(),
                R.string.glcn_avatar_objects to (0 until OwnTVAvatars.OBJECTS).toList(),
            ).forEach { (groupLabel, groupIds) ->
            tv.own.owntv.ui.stage.StageGroupLabel(stringResource(groupLabel), sheet = true)
            groupIds.chunked(6).forEach { rowIds ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    rowIds.forEach { id ->
                        val isSelected = id == selectedId
                        AvatarTile(selected = isSelected, onClick = { onSelect(id); onDismiss() }, focus = selectedFocus.takeIf { isSelected }, tile = 100) {
                            OwnTVAvatar(avatarId = id, modifier = Modifier.size(76.mpx))
                        }
                    }
                }
            }
            }
        }
    }
}

/** One choice: a 120 px Stage tile, ringed in accent while it is the current avatar. */
@Composable
private fun AvatarTile(selected: Boolean, onClick: () -> Unit, focus: FocusRequester? = null, tile: Int = 120, content: @Composable () -> Unit) {
    val accent = stageAccent.accent
    tv.own.owntv.ui.stage.StageSurface(
        onClick = onClick,
        radius = 28.mpx,
        modifier = (focus?.let { Modifier.focusRequester(it) } ?: Modifier).size(tile.mpx),
        focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
        idle = Modifier
            .background(StageColors.ControlFill, RoundedCornerShape(28.mpx))
            .then(if (selected) Modifier.border(3.mpx, accent, RoundedCornerShape(28.mpx)) else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}
