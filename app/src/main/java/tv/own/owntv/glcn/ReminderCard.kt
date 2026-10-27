package tv.own.owntv.glcn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ReminderEntity
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.ownTvTween
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/** How long the reminder card stays before it closes itself (and counts as dismissed). */
const val REMINDER_CARD_MS = 8_000

private val QUALITY_WORDS = Regex("""(?i)\b(?:8K|4K|UHD|2160P|FHD|1080P|HD|720P|SD|HEVC|H265)\b""")
private val NOT_ALNUM = Regex("""[^\p{L}\p{N}]+""")

private fun baseName(name: String) = QUALITY_WORDS.replace(name, " ").replace(NOT_ALNUM, "").lowercase()

/**
 * Is [playing] the channel a reminder is about — in any quality? The reminder keeps the id of the row it was set on,
 * but the same channel often exists as HD, FHD, 4K… with ids of their own, so the guide id and the plain name count too.
 */
fun isReminderChannel(reminder: ReminderEntity, playing: ChannelEntity?): Boolean {
    if (playing == null) return false
    if (playing.id == reminder.channelId) return true
    val epg = reminder.epgChannelId
    if (!epg.isNullOrBlank() && epg.equals(playing.epgChannelId, ignoreCase = true)) return true
    val base = baseName(reminder.channelName)
    return base.isNotEmpty() && base == baseName(playing.name)
}

/**
 * The programme reminder as a small glass card in the top-right corner: it takes no focus, so whatever the viewer is
 * doing carries on, and it closes itself after [REMINDER_CARD_MS] (a thin bar empties to show it). [keyHint] names
 * the key that accepts it.
 */
@Composable
fun ReminderCard(
    reminder: ReminderEntity,
    eyebrow: String,
    keyHint: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a = stageAccent
    val left = remember(reminder.id) { Animatable(1f) }
    val enter = remember(reminder.id) { Animatable(0f) }
    val enterSpec = ownTvTween<Float>(420)
    LaunchedEffect(reminder.id) {
        enter.animateTo(1f, enterSpec)
    }
    LaunchedEffect(reminder.id) {
        left.animateTo(0f, tween(REMINDER_CARD_MS, easing = LinearEasing))
        onDismiss()
    }
    Box(modifier.fillMaxSize().padding(top = 45.mpx, end = 60.mpx), contentAlignment = Alignment.TopEnd) {
        Column(
            Modifier
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * -16.mpx.toPx()
                }
                .width(400.mpx)
                .stageGlass(22.mpx, overContent = true)
                .padding(horizontal = 20.mpx, vertical = 16.mpx),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
                OwnTVIcon(OwnTVIcon.BELL, a.accent, Modifier.size(15.mpx))
                Text(eyebrow, style = stageText(14, 700, 0.02.em), color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                reminder.title, style = stageText(21, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.mpx),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.mpx),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.content_epg_watch_channel), style = stageText(15, 700), color = StageColors.Text)
                Text(keyHint, style = stageText(14, 700), color = a.accent)
            }
            Box(
                Modifier.padding(top = 12.mpx).fillMaxWidth().height(3.mpx).clip(RoundedCornerShape(2.mpx))
                    .background(StageColors.Text.copy(alpha = 0.14f)),
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = left.value
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .background(a.accent),
                )
            }
        }
    }
}
