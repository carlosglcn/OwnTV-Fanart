package tv.own.owntv.features.shell.components

import androidx.tv.material3.MaterialTheme
import tv.own.owntv.ui.theme.hudInk
import tv.own.owntv.ui.theme.hudShade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.core.live.EpgNowNext

/** Which pair of slots a [LiveEpgCard] is describing: what is on air now, or what was on air
 *  at the moment being replayed out of the archive. */
enum class EpgCardVariant { LIVE, ARCHIVE }

/**
 * Now / Next for the PLAYING live channel, laid out horizontally so it can live in the player's top
 * bar beside the channel identity (the older vertical Before/Now/Next card sat on the right edge,
 * where the history channel list now goes). Informational only: never focusable, and renders NOTHING
 * when the channel has no guide data — a permanent "no info" block would be noise on every unhide.
 */
@Composable
fun LiveEpgCard(
    epg: EpgNowNext?,
    modifier: Modifier = Modifier,
    // ARCHIVE renders the same two slots for a programme being replayed: different labels, a teal
    // frame matching the "watching" clock, and progress measured against the replayed instant rather
    // than against now. Two identically-labelled cards a few pixels apart would be unreadable.
    variant: EpgCardVariant = EpgCardVariant.LIVE,
    // ARCHIVE only: the wall-clock instant on screen, which drives the progress bar and "x min left".
    atMs: Long? = null,
    /** glcn: one line only — the live card under the archive one while rewound. */
    compact: Boolean = false,
    /** glcn: drawn inside [LiveEpgCardWithArt] — no panel of its own, a longer synopsis. */
    bare: Boolean = false,
) {
    if (epg == null || (epg.now == null && epg.next == null)) return
    val archive = variant == EpgCardVariant.ARCHIVE
    val formatTime = rememberSystemTimeFormatter()
    // Drives the "x min left" text and the progress bar; a slow tick is plenty for minute precision.
    var wallNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); wallNow = System.currentTimeMillis() } }
    // On the archive card every "how far through are we" question is asked about the replayed moment,
    // not about the present — otherwise a programme from yesterday reads as 100% finished.
    val nowMs = if (archive) (atMs ?: wallNow) else wallNow

    // glcn: stacked, not side by side — now on top at full width, what follows on one line under it.
    // Side by side, the two halves fought for the corner and the "now" half shrank to one word a line.
    Column(
        modifier = modifier
            .widthIn(min = 300.dp, max = if (bare) 470.dp else 500.dp)
            .then(if (bare) Modifier else Modifier.clip(RoundedCornerShape(10.dp)).background(hudShade(0.72f)))
            .then(
                if (archive) Modifier
                    .background(tv.own.owntv.ui.theme.stageAccent.accent.copy(alpha = 0.09f))
                    .border(1.dp, tv.own.owntv.ui.theme.stageAccent.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                else Modifier,
            )
            .padding(horizontal = 14.dp, vertical = if (compact) 6.dp else if (bare) 2.dp else 10.dp),
    ) {
        val now = epg.now
        if (compact) {
            // "EN DIRECTO AHORA · Title · quedan 12 min"
            if (now != null) {
                val remaining = ((now.stopMs - nowMs) / 60_000L).toInt()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlotLabel(stringResource(R.string.content_live_now), tv.own.owntv.ui.theme.stageAccent.accent)
                    Text(
                        now.title + (if (remaining in 1..600) "  ·  " + stringResource(R.string.content_live_time_remaining, formatTime(now.stopMs), remaining) else ""),
                        style = MaterialTheme.typography.labelMedium, color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            return@Column
        }
        if (now != null) {
            SlotLabel(stringResource(if (archive) R.string.content_archive_playing else R.string.content_live_now), tv.own.owntv.ui.theme.stageAccent.accent)
            Text(
                now.title,
                style = MaterialTheme.typography.titleMedium,
                color = hudInk,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val remaining = ((now.stopMs - nowMs) / 60_000L).toInt()
            Text(
                if (remaining in 1..600) stringResource(R.string.content_live_time_remaining, formatTime(now.stopMs), remaining)
                else stringResource(R.string.content_live_time_range, formatTime(now.startMs), formatTime(now.stopMs)),
                style = MaterialTheme.typography.labelMedium,
                color = hudInk.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val span = (now.stopMs - now.startMs).toFloat()
            if (span > 0f) {
                val progress = ((nowMs - now.startMs) / span).coerceIn(0f, 1f)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(hudInk.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(RoundedCornerShape(2.dp)).background(tv.own.owntv.ui.theme.stageAccent.accent))
                }
            }
            // What the programme actually IS, not just what it is called.
            now.description?.takeIf { it.isNotBlank() }?.let { synopsis ->
                Text(
                    synopsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = hudInk.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (bare) 3.dp else 6.dp),
                )
            }
        }
        epg.next?.let { entry ->
            Row(
                Modifier.padding(top = if (now != null) (if (bare) 4.dp else 8.dp) else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SlotLabel(stringResource(if (archive) R.string.content_archive_then else R.string.content_live_next), hudInk.copy(alpha = 0.6f))
                Text(
                    formatTime(entry.startMs) + "  " + entry.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = hudInk.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * glcn: the live programme card with the programme's picture beside it — the player's top-right card, in
 * the same manner as the channel-change banner. Without a picture it is the plain card.
 */
@Composable
fun LiveEpgCardWithArt(epg: EpgNowNext?, art: String?, modifier: Modifier = Modifier) {
    if (epg == null || (epg.now == null && epg.next == null)) return
    if (art.isNullOrBlank()) { LiveEpgCard(epg, modifier); return }
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(hudShade(0.72f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tv.own.owntv.ui.components.ProgrammeArt(
            url = art,
            modifier = Modifier.size(width = 136.dp, height = 76.dp).clip(RoundedCornerShape(10.dp)),
        )
        LiveEpgCard(epg, bare = true)
    }
}

/**
 * N3 — the guide lines under the channel-change banner: what is on now, how far through it is, and what
 * comes next. The same labels, time formats and progress bar as [LiveEpgCard], packed to fit the
 * banner's width. Draws nothing for a channel without a guide, so the banner stays name and number.
 */
@Composable
fun ZapGuideLines(epg: EpgNowNext?, modifier: Modifier = Modifier) {
    if (epg == null || (epg.now == null && epg.next == null)) return
    val formatTime = rememberSystemTimeFormatter()
    // The banner is up for a few seconds, so the moment it opened is precise enough.
    val nowMs = remember(epg) { System.currentTimeMillis() }
    Column(modifier.padding(top = 8.dp)) {
        epg.now?.let { entry ->
            SlotLabel(stringResource(R.string.content_live_now), tv.own.owntv.ui.theme.stageAccent.accent)
            Text(entry.title, style = tv.own.owntv.ui.theme.stageText(25, 400), color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val remaining = ((entry.stopMs - nowMs) / 60_000L).toInt()
            Text(
                if (remaining in 1..600) stringResource(R.string.content_live_time_remaining, formatTime(entry.stopMs), remaining)
                else stringResource(R.string.content_live_time_range, formatTime(entry.startMs), formatTime(entry.stopMs)),
                style = tv.own.owntv.ui.theme.stageText(20, 500),
                color = hudInk.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val span = (entry.stopMs - entry.startMs).toFloat()
            if (span > 0f) {
                val progress = ((nowMs - entry.startMs) / span).coerceIn(0f, 1f)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(hudInk.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(progress).height(2.dp).clip(RoundedCornerShape(1.dp)).background(tv.own.owntv.ui.theme.stageAccent.accent))
                }
            }
        }
        epg.next?.let { entry ->
            Spacer(Modifier.height(8.dp))
            SlotLabel(stringResource(R.string.content_live_next), hudInk.copy(alpha = 0.45f))
            Text(entry.title, style = tv.own.owntv.ui.theme.stageText(22, 700), color = hudInk.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.content_live_time_range, formatTime(entry.startMs), formatTime(entry.stopMs)),
                style = tv.own.owntv.ui.theme.stageText(20, 500),
                color = hudInk.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SlotLabel(text: String, color: Color) {
    Text(
        text.uppercase(),
        style = tv.own.owntv.ui.theme.stageText(20, 500).copy(letterSpacing = 1.2.sp),
        color = color,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 1.dp),
    )
}
